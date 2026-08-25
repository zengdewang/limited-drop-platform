# 评价进入 RAG 知识库作为购买意见

Moderated customer reviews are indexed into the QA knowledge base alongside official product documents, so answers can cite real purchase opinions (including negatives like "runs small"). Reviews flow through Moderation (auto rules + an optional manual-review toggle), then are published and asynchronously vectorized into Milvus by the qa service.

Considered: official documents only. Rejected — UGC opinions are the project's differentiator and complete the review→RAG closed loop.
