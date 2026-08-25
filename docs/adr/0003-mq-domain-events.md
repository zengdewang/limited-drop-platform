# 跨服务通信采用 MQ 领域事件而非同步 RPC

The flash-sale → order flow (after Redis stock decrement) and the review → QA indexing flow both go through RocketMQ events, not synchronous HTTP. This is required for the async-shaving design and keeps services decoupled; consumers (order, qa) must be idempotent and handle retries.

Considered: synchronous RPC (simpler). Rejected — it couples the hot path to downstream latency and breaks the削峰 design.
