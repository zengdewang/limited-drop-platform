# 技术选型：RocketMQ / Milvus / LangChain4j / DeepSeek / bge-m3

- MQ: **RocketMQ** — ordered/delayed messages natively support pay-window timeout stock release; the de-facto choice for flash-sale systems.
- Vector DB: **Milvus standalone** — professional vector DB with first-class LangChain4j integration (MilvusEmbeddingStore).
- Embedding: **bge-m3** via SiliconFlow API (1024-dim). API route avoids local GPU dependency.
- LLM: **DeepSeek** — OpenAI-compatible, cost-effective, high resume recognition.
- RAG framework: **LangChain4j** — mature Java/Spring integration.

Considered: Kafka/RabbitMQ, Redis-Stack/Qdrant, local Ollama. Trade-offs of ops weight, integration maturity, and resume value drove these choices.
