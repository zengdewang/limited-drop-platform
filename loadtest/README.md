# Flash Sale JMeter 并发测试

本目录面向 JMeter **5.6.3**，测试接口：

~~~text
POST /api/flashsale/drops/{dropId}/buy
~~~

测试计划仅使用 JMeter 标准组件，无需安装第三方插件。

## 文件说明

| 文件 | 用途 |
|---|---|
| `flashsale-concurrency.jmx` | 五场景测试计划，包含同步起跑、CSV 参数化、断言、统计和最终判定 |
| `prepare-users.ps1` | 批量注册/登录测试账号并生成带真实 JWT 的 `users.csv` |
| `run-flashsale.ps1` | 自动预热 Drop、调用 JMeter CLI、生成报告并检查 PASS/FAIL |
| `users.csv.example` | 100 行 CSV 模板；直连场景可直接使用，Gateway 场景需真实 JWT |

默认 JMeter 路径：

~~~text
C:\Users\lemon\Desktop\apache-jmeter-5.6.3\bin\jmeter.bat
~~~

## 场景

| scenario | 验证目标 | 默认线程 | 访问入口 |
|---|---|---:|---|
| `inventory` | 不同用户同步起跑，验证库存不超卖 | 50 | Flashsale `:8083` |
| `same-user` | 同一用户并发请求，验证购买幂等 | 30 | Flashsale `:8083` |
| `ramp` | 不同用户递增并发，观察吞吐与响应时间 | 100 / 30 秒 | Flashsale `:8083` |
| `limit-ip` | 多账号同 IP，验证 Gateway IP 限流 | 30 | Gateway `:8080` |
| `limit-account` | 同账号不同 IP，验证 Gateway 账号限流 | 30 | Gateway `:8080` |

`inventory`、`same-user`、`limit-ip`、`limit-account` 使用 Synchronizing Timer 同步释放线程；`ramp` 按配置时长逐步启动线程，不做同步屏障。

## 第一次测试：照着做

第一次建议只测 `inventory`，先用 20 个用户确认整个流程正常：

1. 启动 Docker 基础设施以及 user、product、flashsale、order、gateway 服务。
2. 确认项目中存在一个可用于测试的 Drop，并记住它的 ID，例如 `1`。
3. 在项目根目录打开 PowerShell，运行：

~~~powershell
pwsh -File .\loadtest\run-flashsale.ps1 -Scenario inventory -DropId 1 -InventoryUsers 20 -OpenReport -CloseAfter
~~~

脚本会自动打开 Drop、读取实际库存、执行测试、生成报告并在结束后关闭 Drop。测试结束后浏览器会自动打开 HTML 仪表盘。

先看 PowerShell 输出中的 `checks`：

- `PASS`：库存、业务结果和断言全部符合预期；
- `FAIL`：查看紧随其后的失败原因，再看 HTML 报告的错误详情；
- `success`：抢购成功数；
- `soldout`：售罄数；
- `duplicate`：重复购买数；
- `oversell`：超卖数，必须为 `0`；
- `average_ms` / `p95_ms`：平均响应时间与 95% 请求完成时间。

如果 20 用户通过，再依次测试 50、100、250，不要一开始就运行上千线程。

## 当前 Windows 环境：可直接复制

以下命令与本项目当前目录、JMeter 5.6.3 和 Codex 自带 PowerShell 路径一致。先在项目根目录打开 PowerShell：

~~~powershell
cd 'C:\Users\lemon\Desktop\xiaofeipingtai - 副本 (2)'
~~~

### 1. 先用 20 用户打开 GUI

将 `DropId 2` 换成商品管理界面中已经创建并同步完成的 Drop ID：

~~~powershell
& 'C:\Users\lemon\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\loadtest\run-flashsale.ps1 -Scenario inventory -DropId 2 -InventoryUsers 20 -Gui -CloseAfter
~~~

JMeter 打开后点击绿色三角形。依次查看：

1. `汇总报告（GUI）`：确认样本数正确、错误率为 `0%`；
2. `聚合报告（GUI）`：查看 Average、Median、P90、P95、P99；
3. `响应时间图`：观察延迟是否随着压力明显升高；
4. `Aggregate Graph`：在 `Settings` 勾选需要的指标，再点击 `Display Graph`；
5. 少量请求失败时，临时启用 `查看结果树` 查看响应和断言，排查完立即关闭。

关闭 JMeter 后回到 PowerShell，最终以脚本输出的 `checks=PASS`、`oversell=0`、`assertion_failures=0` 为准。

### 2. 增加到 400 用户

若出现：

~~~text
CSV 用户不足：场景需要 400 行，文件只有 100 行
~~~

先生成至少 400 行真实用户数据：

~~~powershell
& 'C:\Users\lemon\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\loadtest\prepare-users.ps1 -Count 400
~~~

再运行 400 用户测试：

~~~powershell
& 'C:\Users\lemon\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\loadtest\run-flashsale.ps1 -Scenario inventory -DropId 2 -InventoryUsers 400 -Gui -CloseAfter
~~~

`users.csv.example` 只有 100 行。线程数大于 100 时应先运行 `prepare-users.ps1`，并确保：

~~~text
CSV 行数 >= 线程数 × Loops
~~~

### 3. 正式寻找本机上限

GUI 主要用于看图，不适合高并发正式结论。建议按以下顺序测试：

~~~powershell
# 100 个同步用户
& 'C:\Users\lemon\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\loadtest\run-flashsale.ps1 -Scenario inventory -DropId 2 -InventoryUsers 100 -CloseAfter

# 250 个同步用户
& 'C:\Users\lemon\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\loadtest\run-flashsale.ps1 -Scenario inventory -DropId 2 -InventoryUsers 250 -CloseAfter

# 500 个同步用户
& 'C:\Users\lemon\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\loadtest\run-flashsale.ps1 -Scenario inventory -DropId 2 -InventoryUsers 500 -CloseAfter

# 1000 用户在 60 秒内递增启动
& 'C:\Users\lemon\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe' -NoProfile -File .\loadtest\run-flashsale.ps1 -Scenario ramp -DropId 2 -RampUsers 1000 -RampSeconds 60 -CloseAfter
~~~

每一档使用足够行数的 `users.csv`，并尽量使用独立 Drop。若错误率超过 1%、P95 突然翻倍、吞吐不再上升或服务 CPU 长时间超过 85%，停止增加压力，以上一档作为当前电脑的稳定范围。

库存不超卖测试允许用户数大于库存，超出部分应返回“售罄”。但若要测完整成功链路容量，应保证 Drop 库存不小于请求数。

## 单机测试上限

当前脚本参数允许最多 `10000` 个线程，但这是输入上限，不代表这台电脑能可信地产生 10000 并发。JMeter 5.6.3 当前使用默认 `1 GB` 堆内存，而且 JMeter、全部微服务、MySQL、Redis、RocketMQ 和 Milvus 运行在同一台电脑上，会互相争抢 CPU 与内存。

| 运行方式 | 建议范围 | 用途 |
|---|---:|---|
| JMeter GUI | 20～100 线程 | 学习、观察曲线、排查失败请求 |
| 命令行同步并发 | 100～500 线程 | 本机库存与幂等验证的主要范围 |
| 命令行递增并发 | 100～1000 线程 | 逐步寻找响应时间和吞吐拐点 |
| 2000 线程以上 | 不建议单机直接下结论 | JMeter 和本机资源可能先成为瓶颈 |

因此，这套本地环境的保守可信上限约为 **500 个同步瞬时并发**，递增场景可以试探到 **1000 个线程**。这不是服务器的最终容量；真正容量需要把 JMeter 放到另一台机器，或使用分布式压测。

寻找实际上限时按 `100 → 250 → 500 → 750 → 1000` 逐级测试。出现以下任一情况就停止增加，并把上一档作为当前环境的稳定范围：

- 错误率超过 1%；
- P95 突然翻倍或超过业务可接受值；
- 增加线程后吞吐量不再上升；
- JMeter 出现内存不足、无法创建线程或连接超时；
- 服务 CPU 长时间超过 85%，或 Redis/MySQL 出现明显排队。

`inventory` 和 `ramp` 的成功路径容量还受 Drop 库存限制。若总请求数大于库存，后续请求只会快速返回售罄，不能代表完整抢购链路的承载能力。用于容量测试时，应保证：

~~~text
Drop 库存 >= 线程数 × Loops
CSV 行数 >= 线程数 × Loops
~~~

## 前置条件

1. 启动 MySQL、Redis、RocketMQ，以及 user、flashsale、gateway 服务。
2. 准备一个专用于压测的 Drop，确认其已经同步到 Flashsale。
3. 不要让多个测试同时使用同一个 Drop。
4. 重复测试同一 Drop 前，应留意旧订单的支付超时事件；建议每轮使用新的测试账号，必要时使用独立 Drop。

运行脚本默认会调用：

~~~text
POST /api/flashsale/drops/{dropId}/open
~~~

该操作会重置 Redis 中的库存、已购买用户集合和订单幂等键。传入 `-SkipOpen` 可跳过，但此时由你负责确保 Drop 已正确预热。

## 准备 CSV 用户

CSV 固定为四列：

~~~csv
userId,token,username,clientIp
~~~

直连场景只使用 `userId`，所以可以直接使用 `users.csv.example`。两个 Gateway 限流场景必须使用真实 JWT。

生成 100 个测试账号：

~~~powershell
pwsh -File .\loadtest\prepare-users.ps1 -Count 100
~~~

脚本会安全提示输入密码，不会把密码写入 CSV，也不会在终端输出 JWT。生成的 `loadtest/users.csv` 已被 `.gitignore` 排除。

如果希望非交互运行：

~~~powershell
pwsh -File .\loadtest\prepare-users.ps1 -Count 100 -Password '仅用于本地测试的密码'
~~~

## GUI 监听器

需要直观观察时，通过运行脚本打开已经配置好库存、线程和 CSV 的 JMeter GUI：

~~~powershell
pwsh -File .\loadtest\run-flashsale.ps1 -Scenario inventory -DropId 1 -InventoryUsers 20 -Gui -CloseAfter
~~~

JMeter 打开后：

1. 点击顶部绿色三角形启动测试；
2. 点击左侧 `响应时间图` 查看延迟变化；
3. 点击 `Aggregate Graph` 查看平均值和百分位柱状图；
4. 点击 `汇总报告（GUI）` 查看吞吐量和错误率；
5. 测试结束后关闭 JMeter，脚本会在 PowerShell 中输出 PASS/FAIL 汇总并关闭 Drop。

每次重新运行前，点击工具栏的扫帚图标清空旧结果。`inventory` 是同步瞬时起跑，通常只会在响应时间图上形成一个时间点；要观察连续曲线，请使用递增场景，例如：

~~~powershell
pwsh -File .\loadtest\run-flashsale.ps1 -Scenario ramp -DropId 1 -RampUsers 100 -RampSeconds 30 -Gui -CloseAfter
~~~

响应时间图按 1 秒聚合并只显示“秒杀购买请求”。Aggregate Graph 也只统计该业务请求；在它的 `Settings` 页选择需要比较的 `Average`、`Median`、`90% Line`、`95% Line` 和 `99% Line` 后点击 `Display Graph`。

测试计划内置五个中文监听器：

| 监听器 | 用途 | 默认状态 |
|---|---|---|
| `汇总报告（GUI）` | 查看样本数、平均耗时、吞吐量和错误率 | 启用 |
| `聚合报告（GUI）` | 查看中位数及 P90、P95、P99 响应时间 | 启用 |
| `响应时间图` | 查看响应时间随测试进程的变化趋势 | 启用 |
| `Aggregate Graph` | 用柱状图比较平均值、中位数和百分位 | 启用 |
| `查看结果树（调试时手动启用）` | 查看单次请求、响应和断言失败详情 | 禁用 |

GUI 方式只建议用于 100 线程以内的学习和观察。`查看结果树` 会保存每个样本的详细信息，只适合少量线程排查接口问题。正式并发测试不要开启，否则会明显增加 JMeter 内存占用并干扰吞吐和延迟数据。正式结果仍以 `summary.json`、JTL 和 HTML 报告为准。

HTML 仪表盘中重点看：

- `APDEX`：整体响应体验；
- `Response Time Percentiles`：P90、P95、P99；
- `Transactions per Second`：每秒吞吐量；
- `Response Times Over Time`：响应时间是否随压力恶化；
- `Active Threads Over Time`：实际并发线程变化；
- `Errors`：失败类型和占比。

## 命令行运行

### 库存不超卖

~~~powershell
pwsh -File .\loadtest\run-flashsale.ps1 -Scenario inventory -DropId 1 -InventoryUsers 50
~~~

判定条件：

- 样本数必须等于线程数；
- 成功数必须等于 `min(实际库存, 样本数)`；
- 其余请求必须全部售罄；
- 成功数不得超过库存；
- `remaining` 不得为负数；
- 不允许出现重复购买、未开售、HTTP/JSON/业务断言错误。

### 同用户并发幂等

~~~powershell
pwsh -File .\loadtest\run-flashsale.ps1 -Scenario same-user -DropId 1 -SameUserThreads 30 -SameUserId 10001
~~~

判定条件：

- 必须且只能有 1 个 `data.code=0`；
- 其余请求必须全部为 `data.code=-2`；
- 成功与重复响应必须返回同一个 `orderNo`。

### 不同用户递增并发

~~~powershell
pwsh -File .\loadtest\run-flashsale.ps1 -Scenario ramp -DropId 1 -RampUsers 100 -RampSeconds 30
~~~

判定条件与 `inventory` 相同；HTML 报告用于观察吞吐、平均响应时间、P90/P95/P99 和错误率随递增并发的变化。

### Gateway IP 限流

先生成真实 JWT CSV，然后运行：

~~~powershell
pwsh -File .\loadtest\run-flashsale.ps1 -Scenario limit-ip -DropId 1 -LimitThreads 30
~~~

每个线程读取不同账号的 JWT，但统一发送相同 `X-Forwarded-For`。默认 Gateway 每 IP 5 QPS，因此同步起跑后应观察到 HTTP 429。

### Gateway 账号限流

~~~powershell
pwsh -File .\loadtest\run-flashsale.ps1 -Scenario limit-account -DropId 1 -LimitThreads 30
~~~

脚本默认读取 `users.csv` 中第一个真实 JWT，所有线程共用该账号，但为每个线程生成不同的 `X-Forwarded-For`。默认 Gateway 每账号 10 QPS，因此应观察到 HTTP 429。

也可以显式传入 JWT：

~~~powershell
pwsh -File .\loadtest\run-flashsale.ps1 -Scenario limit-account -DropId 1 -SameUserToken '<JWT>'
~~~

不要把真实 JWT 写进脚本、JMX 或提交到仓库。

## 自动预热和库存参数

`run-flashsale.ps1` 会：

1. 通过 Gateway 调用 `open`；
2. 查询 `/info` 并确认状态为 `OPEN`；
3. 读取 Drop 的实际 `stock` 作为断言基准；
4. 启动 JMeter；
5. 读取 `summary.json`，若 `checks=FAIL` 则让脚本失败退出。

通常不需要手动传 `-Stock`。如果显式传入的库存与 Drop 实际库存不同，脚本会在测试前终止，避免产生错误结论。

测试完成后关闭 Drop：

~~~powershell
pwsh -File .\loadtest\run-flashsale.ps1 -Scenario inventory -DropId 1 -CloseAfter
~~~

## 断言

每个请求包含三层独立断言：

1. **HTTP 状态断言**：直连场景只允许 200；Gateway 限流场景允许 200 或 429。
2. **JSON 结构断言**：响应必须是合法 JSON，并包含顶层 `code`、`message`、`data`。
3. **业务码断言**：放行响应只允许 `0/-1/-2/-3`；限流响应必须为 HTTP 429 且顶层 `code=429`。

成功响应还会校验：

- `orderNo` 必须存在；
- `remaining` 必须是数字且大于等于 0；
- 重复购买必须返回原 `orderNo`。

## 线程安全统计

JMX 使用 `AtomicLong`、并发集合和并发队列统计，避免高并发下普通 JMeter 属性读改写造成计数丢失。

汇总字段：

| 字段 | 含义 |
|---|---|
| `success` | `data.code=0` |
| `soldout` | `data.code=-1` |
| `duplicate` | `data.code=-2` |
| `unopened` | `data.code=-3` |
| `rate_limited` | HTTP 429 / 顶层 `code=429` |
| `oversell` | 成功响应出现负数 `remaining` |
| `http_error` | 非场景允许的 HTTP 状态 |
| `invalid_json` | JSON 无法解析 |
| `invalid_business` | 响应信封或业务码不符合契约 |
| `assertion_failures` | 三层断言失败总数 |
| `average_ms` / `p95_ms` | 请求平均耗时与 P95 |

## 结果文件

每次运行都会生成：

~~~text
loadtest/results/{scenario}-{timestamp}/
├── results.jtl
├── jmeter.log
├── summary.json
└── html/
    └── index.html
~~~

先看 `summary.json` 的 `checks` 和 `failures`，再打开 HTML 报告检查吞吐、延迟百分位与错误详情。日志中也可搜索：

~~~text
FLASHSALE_SUMMARY
~~~

## 直接运行 JMX

不使用包装脚本时，至少需要传入场景、Drop、库存、线程数、CSV 和汇总文件：

~~~powershell
& 'C:\Users\lemon\Desktop\apache-jmeter-5.6.3\bin\jmeter.bat' -n -t .\loadtest\flashsale-concurrency.jmx -Jscenario=inventory -Jdrop_id=1 -Jstock=20 -Jthreads=50 -Jusers_file=.\loadtest\users.csv.example -Jsummary_file=.\loadtest\summary.json -l .\loadtest\results.jtl
~~~

直接运行前必须自行调用 `open`，并保证 `stock` 与 Drop 实际库存一致。
