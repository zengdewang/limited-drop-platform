# 六服务微服务拆分

Split into six Spring Boot services (gateway / user / product / flashsale / order / qa) rather than a modular monolith, because the resume goal is demonstrating distributed engineering depth. Services communicate via RocketMQ domain events; they share Redis / MySQL / RocketMQ / Milvus.

Considered: modular monolith (far less ops burden). Rejected — the resume goal is distributed-system depth. We accept the ops cost; Docker Compose keeps the whole stack runnable on one developer machine.
