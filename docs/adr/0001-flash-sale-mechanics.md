# 发售机制采用 FCFS 秒杀 + 三层削峰架构

We chose first-come-first-served flash sale (抢购即命中) over lottery as the drop mechanism, because the project's resume goal is demonstrating high-concurrency handling and FCFS concentrates all pressure into the opening second. The three-layer shaving architecture (gateway rate limiting → Redis preheated inventory with atomic Lua decrement → asynchronous order creation via RocketMQ) is the backbone.

Considered: lottery (spreads load across phases but dilutes the concurrency story). Rejected.
