# RAG 检索采用混合检索 + 重排

The qa service retrieves via **hybrid search** (dense bge-m3 vectors + BM25 keyword matching, both on Milvus) and **reranks with bge-reranker** before generation, rather than vector-only retrieval. Mixed retrieval catches keyword hits like "尺码偏小" that dense vectors miss, and reranking measurably improves answer quality. This is a deliberate resume differentiator.

Considered: vector-only retrieval (simpler). Rejected for answer quality and demo value.
