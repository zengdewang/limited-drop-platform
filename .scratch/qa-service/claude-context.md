# QA（RAG）服务 · 任务上下文（会话交接）

> 生成于 2026-08-25。用途：接手 qa/RAG 工作时，凭此文件恢复上下文。
> 状态速览：**秒杀链路 + gateway 已验证通过；qa 卡在 Milvus 混合检索 POC——Milvus 服务端 BM25 Function + 中文分词在 v2.5.4 / v2.5.7 上都会崩溃，POC 未跑通。下一步建议改用"Java 端手动 BM25 稀疏向量"，不再依赖服务端 Function。**

---

## 1. 用户最终确认的目标（qa 相关）

项目是**限量高端消费品发售平台**简历项目，双卖点：高并发 FCFS 秒杀 + **RAG 知识库**。qa（RAG）卖点要求：

- **混合检索**（bge-m3 稠密 + BM25 关键词）→ bge-reranker 重排 → DeepSeek 生成带引用回答（ADR-0006）。
- **评价闭环**：已支付订单评价 → Moderation 审核通过 → 进知识库（ADR-0004）。
- 评估集 30–50 条，量化 recall@k 与回答质量。

技术栈（qa）：JDK 17 / Spring Boot 3.4.4 / LangChain4j 1.0.0 / **milvus-sdk-java 2.5.10** / DeepSeek / bge-m3+bge-reranker via SiliconFlow / RocketMQ。

---

## 2. 已完成并验证的模块清单（本次会话）

### ✅ 已跑通并验证
- **中间件**：MySQL(3308) / Redis(6379) / RocketMQ(namesrv 9876 + broker 10911) / **Milvus（当前 v2.5.7，但已崩，见 §3）** / etcd / minio。中文+emoji 往返、RocketMQ 生产+消费往返均验证。
- **秒杀全链路**（user/product/flashsale/order，8081–8084）：注册→登录→建商品→建发售→开售预热→抢购(code=0)→同用户重复抢购(code=-2 同 orderNo)→异步建单(轮询 404→PENDING_PAYMENT)→支付(PAID)→支付事件→paid_order→评价(APPROVED)→售罄(精确 stock 件命中，无超卖)。
- **支付超时→库存释放**：1 分钟窗口验证，EXPIRED + 库存回补 + 同用户可重入。
- **gateway(8080)**：5 条路由透传、JWT 过滤(401+注入 X-User-Id)、XFF 清洗、**Sentinel per-IP(5 qps→429) + per-account(10 qps→429)** 均验证。
- 服务运行方式：`java -Xmx256m -jar <module>/target/limited-drop-<m>-1.0.0-SNAPSHOT.jar`（本机 JVM，非 Docker）。

### ❌ 未完成
- **qa 服务本身**：只有 `QaApplication` + `application.yml`（端口 8085、drop_qa_db、Milvus/SiliconFlow/DeepSeek 配置占位）+ `MilvusPocTest`。**没有**摄入消费者、检索器、reranker、DeepSeek 生成、评估器、控制器。
- 前端静态页、全量种子(100k 账号/products/reviews/eval)、JMeter `.jmx`：未做。
- 构建需先 `export PATH=/c/tools/apache-maven-3.9.9/bin:$PATH`（Maven 装在 `C:\tools\apache-maven-3.9.9`，未持久化 PATH）。

---

## 3. qa 卡在 Milvus POC 的具体问题（按时间线）

### 3.1 v2.4.24 → 无 BM25 Function
最初用本机 `milvusdb/milvus:v2.4.24` + SDK 2.4.11。**SDK 2.4.11 的 jar 里没有 `Function`/`FunctionType`/`analyzer` 类**——BM25 Function（服务端文本→稀疏向量）是 2.5 才有的。且 v2.4 服务端也不支持。

### 3.2 升级 v2.5.4 → chinese(jieba) analyzer 崩溃
用户选择"升级 Milvus 2.5"（服务端 BM25 是 ADR-0006 原始方案）。拉 `docker.m.daocloud.io/milvusdb/milvus:v2.5.4` + SDK 2.5.10。
- 建集合 + 插入含中文 → **Milvus 崩溃**（`jieba_tokenizer.rs:66` panic_bounds_check，SIGABRT exit 134）。
- 关键报错信息（一步步逼出来的）："BM25 function input field must set enable_analyzer to true" → 需在 `chunk_text` 字段 `enableAnalyzer(true)`；稀疏索引 metric 必须用 `IndexParam.MetricType.BM25`（非 IP）。

### 3.3 Workaround 尝试：standard 分词器 + Java jieba 预分词
把 `chunk_text` 改 `enableAnalyzer(true)` 但不配 analyzerParams（默认 standard），中文**预先 jieba 分词 + 空格连接**入库和查询。POC 结构上跑通（hybridSearch + WeightedRanker 返回结果），但：
- **稀疏(BM25)检索腿返回空**（断言只查外层 list 非空，被空内层骗过）——未解决。
- 崩溃的集合（含 chinese analyzer schema）毒化了 etcd/minio，Milvus 一重启恢复该集合就崩 → 需 `docker compose down etcd minio milvus` + `docker volume rm limited-drop-infra_etcd-data limited-drop-infra_minio-data` 重置。
- `sparse` 字段不允许 `query` 取原始值（"not allowed to retrieve raw data of field sparse"）——不能用它诊断。

### 3.4 升级 v2.5.7 → 崩溃点后移
用户问"能不能换 tokenizer 类型"→ 结论：tokenizer 类型就那几种，jieba 崩是版本 bug，standard 需预分词。拉了 `v2.5.7`。
- **v2.5.7 + chinese analyzer：建集合 + insert 不再崩**（jieba bug 修了一部分）。
- 但**搜索阶段 Milvus 再次崩溃**（exit 134，超时 150s 后确认）——崩溃点从"插入"后移到"搜索/索引构建"。
- 另：SDK 2.5.10 的 `runAnalyzer` RPC 在 v2.5.7 服务端 **UNIMPLEMENTED**（诊断 API 不可用）。

### 3.5 POC 结论（如实）
**Milvus 服务端 BM25 Function + 中文(jieba)分词，在 v2.5.4 和 v2.5.7 上都不能可靠跑通**——一个在插入崩、一个在搜索崩。稠密向量检索本身没问题；问题全在稀疏/全文这条腿。**不要继续在"服务端 BM25 Function"上耗**，这不是配置问题，是版本缺陷。

---

## 4. 关键技术决策与约束（qa 相关）

| 主题 | 现状/决策 |
|---|---|
| SDK | milvus-sdk-java **2.5.10**（父 POM 已锁；API 是 `io.milvus.v2.*`，非旧 `io.milvus.param.*`） |
| 服务端 | 镜像 **docker.m.daocloud.io/milvusdb/milvus:v2.5.7**（compose 已改）；当前崩溃中 |
| 镜像源 | daocloud 可用（`docker.m.daocloud.io/<name>:<tag>` 直接引用即可，无需 registry-mirror） |
| 建集合要点 | `chunk_text` 字段须 `enableAnalyzer(true)`；BM25 Function `inputFieldNames=[chunk_text]` `outputFieldNames=[sparse]`；稀疏索引 metric 用 `BM25` |
| 关键 API | `ConnectConfig.builder().uri(...)`；`CreateCollectionReq.builder().collectionSchema(schema)`；`CollectionSchema.builder().fieldSchemaList().functionList()`；`HybridSearchReq.searchRequests(List<AnnSearchReq>).ranker(new WeightedRanker(List.of(0.6f,0.4f)))`；`AnnSearchReq.vectorFieldName().vectors(List.of(FloatVec/EmbeddedText))`；`SearchReq.annsField().data(...)`；插入用 **Gson JsonObject**；`getSearchResults()` 是双层 List |
| 内存 | 16GB 机器；已停旧项目容器(rag-mysql/redis)和 milvus 栈的 etcd/minio 曾停又启；Milvus 崩溃后等处理 |
| 配置占位 | `qa/application.yml` 已含 `app.rag.*`（SiliconFlow/DeepSeek key 走环境变量） |

---

## 5. 下一步建议（qa 恢复后按此走）

**推荐方案 A：Java 端手动 BM25 稀疏向量**（不依赖服务端 Function）——最稳、用现有镜像、真·混合检索：
1. Java 用 **jieba**（`com.huaban:jieba-analysis:1.0.2`）对 chunk 分词，自算 BM25 权重（含 idf，语料级统计）。
2. 插入时把稀疏向量作为 `SparseFloatVec`（`SortedMap<Long,Float>`）与稠密向量一起写入（**不加 Function 字段**，稀疏字段普通 SparseFloatVector，索引 metric **IP**）。
3. 查询时同样 jieba 分词 + 算查询 BM25 权重 → 稀疏腿用 `SparseFloatVec` 查询（不用 `EmbeddedText`）。
4. 混合检索 `HybridSearchReq` + `WeightedRanker(0.6/0.4)` → rerank → 生成，流程不变。
5. 简历叙事："Java jieba 计算 BM25 稀疏向量 + bge-m3 稠密向量 → Milvus 混合检索 + bge-reranker 重排"——仍然成立，且展示更多工程。

备选：B. 再试 v2.5.8+/2.6 版本（又一个 2.5GB 拉取，且 2.6 SDK 配 2.5 服务端有错位风险）；C. 降级稠密+关键词兜底（与 ADR-0006 冲突，不推荐）。

**动手前必做**：重置 Milvus 数据（当前卷里有毒集合）：
```
docker compose down etcd minio milvus
docker volume rm limited-drop-infra_etcd-data limited-drop-infra_minio-data
docker compose up -d etcd minio milvus
```
然后改 `qa/src/test/.../MilvusPocTest.java`（当前配置是 v2.5.7 + chinese analyzer，需按方案 A 调整），`mvn -pl qa test -Dtest=MilvusPocTest` 验证后再写 qa 全量。

---

## 6. 范围红线（接手提醒）

- **不要继续在"服务端 BM25 Function + chinese analyzer"上调试**——两个版本都崩，是版本缺陷不是配置。
- **不要**为实现 qa 而扩大范围（不加新功能、不擅改已定 ADR）。
- 涉及技术选型（尤其 Milvus 版本/检索方案）变化，先与用户确认（中文）。
- Milvus 相关操作（down/清卷/up）会短暂停服，动手前告知用户。
