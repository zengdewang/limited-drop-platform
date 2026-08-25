# Order 服务 · 任务上下文（会话交接）

> 生成于 2026-08-25（**已更新到最新状态**）。用途：任何后续会话/协作者接手时，凭此文件恢复上下文。
>
> **⚠️ 2026-08-25 更新**：本文件早期版本写"全链路从未跑通"已过时。最新状态：
> - **秒杀全链路已验证通过**（抢购→幂等→异步建单→支付→评价→售罄，防超卖精确）；**支付超时→库存释放已验证**；**gateway 已验证**（路由/JWT/Sentinel per-IP+per-account 429）。
> - **qa（RAG）卡在 Milvus POC**（服务端 BM25 Function + 中文分词在 v2.5.4/v2.5.7 均崩溃，POC 未跑通，下一步方案见下面指向的文档）。
> - 详见 **`.scratch/qa-service/claude-context.md`**（当前项目状态 + qa POC 深挖 + 下一步建议）。

## 状态速览（2026-08-25 定稿）
> **秒杀链路 + gateway 已跑通验证；qa 卡在 Milvus 混合检索 POC（v2.5.4 jieba 崩溃 → 升级 v2.5.7 后崩溃点后移到搜索 → 建议改 Java 手动 BM25 稀疏向量）。本文件以下内容为 order 服务的设计细节（仍准确）。**

---

## 1. 用户最终确认的目标

**限量高端消费品发售平台（xiaofeipingtai）**，作为简历项目。两个卖点，缺一不可：

1. **高并发 FCFS 秒杀**：先到先得，三层削峰。
2. **RAG 知识库**：官方商品资料 + 已审核评价 → 向量检索 + 重排 + 带引用回答。

**量化目标**（用户已确认照此设计）：单场 10 万级请求、峰值 ≥5k QPS、P99 < 200ms、成功率 ≥99.9%，**用 JMeter 压测背书**。

**协作方式**（用户明确要求）：用中文交流；**用户对 JMeter、Milvus 运维、跨服务一致性不熟，要在实现/测试时逐步教**，不要只丢结论。

---

## 2. 已确定的范围 与 明确不做

### 已确认做
- **FCFS 秒杀**（非抽签），三层削峰：Sentinel 限流 → Redis 预热 + Lua 原子扣减 → RocketMQ 异步建单；支付窗口 15 分钟，超时释放库存；幂等（顾客+场次）；防作弊 = Sentinel 按 IP + 账号限流（**可 JMeter 验证**）。
- **6 微服务**：gateway / user / product / flashsale / order / qa + `common` 共享模块。
- **单仓库 Maven 多模块**，中间件走 Docker Compose，服务跑本机 JVM。
- **评价闭环**：已支付订单可评价 → Moderation（自动规则 + 人工复核开关）→ 通过后进 RAG 知识库。
- **RAG 进阶版**：混合检索（bge-m3 稠密 + BM25）→ bge-reranker 重排 → DeepSeek 生成；30–50 条评估集。
- **技术栈**：JDK 17 / Spring Boot 3.4.4 / Spring Cloud 2024.0.1 / MyBatis-Plus 3.5.7 / rocketmq-spring 2.3.5 / milvus-sdk 2.5.10 / LangChain4j 1.0.0 / DeepSeek / bge-m3+bge-reranker via SiliconFlow / Redis / MySQL 8 / JWT。
- **纯静态演示前端**（网关注册登录/发售列表/秒杀页/评价/QA 对话，无构建工具）。
- 鉴权 **JWT**；**v1 不引入 Nacos**（留作升级）；模拟支付；10 万种子账号。

### 明确不做（v1）
- 抽签制、会员等级梯度（仅留 `member_level` 字段）、设备指纹/行为风控。
- 购物车、优惠券、售后、物流、多规格 SKU。
- 真实支付网关（仅模拟）、注册中心/配置中心（Nacos）、K8s 部署。

---

## 3. Order 服务当前状态

**代码完成且 `mvn install` 编译通过**（含依赖 common）。尚未运行验证（中间件未起）。

- 路径：`order/src/main/java/com/limiteddrop/order/`
- 组件：
  - `OrderApplication` / `config/`（GlobalExceptionHandler、MybatisPlusConfig）
  - `entity/Order`、`mapper/OrderMapper`、`dto/OrderResponse`
  - `service/OrderService`：`createFromHit`（幂等建单 + 调度超时检查）、`checkTimeout`（过期→EXPIRED→发释放事件；订单未落库则短延迟重试）、`pay`（模拟支付，超时→释放）、`get`（404=CREATING，前端轮询）、`my`（分页）
  - `mq/FlashSaleHitConsumer`（消费 `DROP_ORDER:HIT`）、`mq/OrderTimeoutCheckConsumer`（消费延迟自消息 `ORDER_TIMEOUT:CHECK`）
  - `controller/OrderController`（GET /{orderNo}、POST /{orderNo}/pay、GET /my）
- 依赖表 `drop_order_db.orders`：`order_no` 唯一键 = 跨服务幂等键。
- 端口 8084；MySQL 走 **3308**（非 3306，见 §4 问题）。

### Order 服务的核心职责（不要改坏这条契约）
**释放库存的触发器在 order**——它是唯一知道支付状态的服务。支付超时由 order 发 `ORDER_TIMEOUT_DONE:RELEASE` 事件，flashsale 消费后用 `release.lua` 释放。支付成功发 `ORDER_PAID:PAID`（遥测，product 据此记 `paid_order` 供评价资格校验，flashsale 据此标记审计）。

### Order 服务当前已知的坑
- **延迟消息级别假设未验证**：`app.order.payment-delay-level: 15`（15m）依赖 `infra/rocketmq/broker.conf` 的 `messageDelayLevel` 覆写。**broker 从未启动，此假设从未验证**。开发快测时把 `pay-window-minutes` 调小 + `payment-delay-level` 对应调小（如 1m→level 5）。

---

## 4. 已完成 / 未完成 / 有问题的改动

### ✅ 已完成
- `git init`（main 分支）、`.gitignore`、根 `pom.xml`（版本全部锁死，含 UTF-8 编码属性——平台是 GBK）。
- **common**（编译安装通过）：`Result`/`ApiException`/`JwtUtil`；事件 `FlashSaleHitEvent`/`ProductDocPublished`/`ReviewModerated`/`ReviewUnmoderated`/`OrderPaidEvent`/`OrderPaymentTimeoutEvent`/`PaymentTimeoutCheck`/`DropPublished`；`Topics` 常量（含 `DROP_PUBLISHED`）；`FlashSaleKey`。
- **user**（编译通过）：注册/登录/me，`DataSeeder`（10 万账号，bcrypt cost=4），`TokenGenerator`（离线签发 JWT → jmeter/tokens.csv）。
- **product**（编译通过）：商品/发售/评价 CRUD；Moderation 状态机（自动 + 人工复核 ops）；`paid_order` 表（消费 `ORDER_PAID` 事件同步）；发射 `ProductDocPublished`/`ReviewModerated`/`ReviewUnmoderated`/`DropPublished`。
- **flashsale**（编译通过）：`lua/buy.lua` + `lua/release.lua`；`FlashSaleService`（预热/开售/关停/info/buy/release）；异步审计 `FlashHitLogWriter`；MQ 组件（`DropSessionConsumer`/`FlashSaleHitPublisher`/`OrderTimeoutDoneConsumer`/`OrderPaidConsumer`）；`drop_session` 本地会话表。
- **order**（编译通过）：见 §3。
- **infra**：`docker-compose.yml`（compose config 校验通过；MySQL 映射 3308）、5 个库建表 SQL、`mysql/my.cnf`、`rocketmq/broker.conf`（含 15m 延迟级别）、`.wslconfig` 文档。
- **本机工具**：Maven 3.9.9 下载到 `C:\tools\apache-maven-3.9.9`（未持久化 PATH）；已确认本机有 **Node v24 + npm 11**、git、JDK 17（Temurin）；**无 JMeter**。

### ❌ 未完成
- **qa 服务**：只有 pom.xml，未写任何代码。Milvus collection + 混合检索 POC（全项目最高风险点）未做。
- **gateway 服务**：只有 pom.xml。路由/JWT 过滤/XFF 清洗/Sentinel 限流/静态页全未写。
- **前端静态页**：未写。
- **种子数据**：`seed/products.json`、评价种子、`eval_question` 种子未写。
- **100k 账号种子 + tokens.csv**：未跑（需 MySQL 起来）。
- **JMeter `.jmx`**：未写。
- **冒烟验证**：RocketMQ 往返、MySQL utf8mb4 中文/emoji 往返、user→product→flashsale→order 全链路 curl——全部未跑（中间件没起来）。

### ⚠️ 有问题/阻塞
1. **【阻塞】Docker 引擎拉镜像失败 + Docker Desktop 反复卡启动**（详见 §5 问题与约束第 6 条）。这是当前唯一硬阻塞。
2. **MySQL 3306 端口被本机 Windows mysqld 占用**——已规避：compose 映射 `3308:3306`，所有服务 JDBC 已改 3308。**注意别改回 3306。**
3. **镜像加速配置可能没生效**：写入的是 `~/.docker/daemon.json`，但 Docker Desktop 引擎实际读 `/run/config/docker/daemon.json`（WSL 内）。此路径从未成功配置，需用 Docker Desktop GUI（Settings → Docker Engine）验证/配置。

---

## 5. 关键技术决策与约束

| 主题 | 决策 | 约束/原因 |
|---|---|---|
| 架构 | 6 微服务 + common，单仓库多模块 | 简历展示分布式；无 Nacos，网关静态路由；服务间全走 MQ 事件，**无同步 RPC、无分布式事务**（ADR-0003） |
| 秒杀 | Redis Lua 原子扣减（一次往返防超卖/防重复）→ MQ 异步建单 → 超时释放 | 热路径只碰 Redis，不落库不阻塞等 MQ |
| 幂等 | 跨服务用 `order_no` 唯一键（orders/paid_order/flash_hit_log/review） | 消费者重复消息无害 |
| 库存释放 | 触发器在 order（唯一知道支付状态），事件 `ORDER_TIMEOUT_DONE` → flashsale `release.lua` | 不要挪到别处 |
| 延迟消息 | RocketMQ 延迟级别，15m=level 15（broker.conf 覆写） | **未验证**，见 §3 坑 |
| 评价资格 | product 消费 `ORDER_PAID` → `paid_order` 表，评价须匹配该表且属本人 | 防刷 |
| 内存 | 16GB 机器：compose 各容器 mem_limit（milvus 3G/broker 1G/namesrv 0.3G/etcd 0.5G/minio 0.5G/mysql 1G/redis 0.3G≈6.6G）；JVM `-Xmx256m~384m`；`.wslconfig` memory=10GB swap=6GB | 压测时停 qa 服务腾内存 |
| 编码 | 所有 yml/pom 显式 UTF-8；MySQL utf8mb4 | Windows 平台编码 GBK |
| 端口 | 服务 8081–8085，网关 8080；MySQL **3308**（3306 被占用） | 见 §4 |
| 环境 | JDK 17 / Spring Boot 3.4.4 / Cloud 2024.0.1；Maven 需 `export PATH=/c/tools/apache-maven-3.9.9/bin:$PATH` | 每次构建命令前都要 export（PATH 未持久化） |

---

## 6. 下一步建议（按依赖顺序）

1. **先解决 Docker 引擎拉镜像**（唯一硬阻塞）：
   - 用 Docker Desktop **GUI → Settings → Docker Engine** 配置 `registry-mirrors`（daocloud/1ms.run 等），而非手写 `~/.docker/daemon.json`；
   - 或直接在 GUI 确认代理设置；改完重启 Docker Desktop，`docker pull redis:7-alpine` 验证。
2. 中间件起来后：`docker compose ps` 全 healthy → **RocketMQ 消息往返冒烟**（rocketmq-spring 2.3.5 对 5.3.0 broker 兼容性未验证）→ **MySQL 中文/emoji 往返**。
3. 按序启动并 curl 冒烟：user（注册→登录→me）→ product（建商品/发售→事件入 MQ）→ flashsale（open→buy→-2/-1 分支）→ order（轮询 CREATING→PENDING_PAYMENT→pay→PAID；快过期释放库存）。
4. 然后实现 **qa**（**先做 Milvus collection 初始化 + 混合检索 POC spike**，最高风险，先行隔离验证）→ 评价摄入消费者 → DeepSeek 生成 → 评估器。
5. 再实现 **gateway**（路由、JWT 过滤、XFF 清洗、Sentinel 限流、静态页；断言过滤器顺序 JWT→XFF→Sentinel）。
6. 前端静态页 → 全量种子（10 万账号、products、reviews、eval）→ JMeter `.jmx`（先确认本机有无 JMeter，再装）。

**给接手的提醒**：不要因为早期计划文件里列了更多内容就擅自扩范围——只做上面 1–6，且严格按用户确认的范围（§2）。用户要的是"先把已写的跑通"，不是加功能。

---

## 7. 范围红线

- **不要**新增 Nacos/K8s/真实支付/会员等级逻辑/抽签制。
- **不要**把 MySQL 端口改回 3306（本机占用）。
- **不要**在热路径（buy）上加同步 DB 写或同步 MQ 发送等待。
- **不要**动 `order_no` 幂等键语义、库存释放触发位置、评价进库状态机。
- 涉及技术选型变更、接口契约变更，先与用户确认（用中文）。
