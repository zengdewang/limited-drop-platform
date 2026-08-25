# 限量发售平台 (Limited Drop Platform)

A limited-edition luxury consumer goods platform where products go on sale through timed, high-concurrency drop events. The domain centers on the flash-sale flow (报名 → 抢购 → 支付) and a RAG knowledge base that grounds product consultation on official documents and moderated customer reviews.

## Language

**Drop**: A scheduled, limited-quantity sale of a Product, announced in advance and opened at a fixed time.
_Avoid_: Release, Campaign, Sale

**Flash Sale**: The drop mechanism in which the first eligible Customer to request the Product during the opening window secures it, subject to stock.
_Avoid_: 秒杀（代码内规范名用 Flash Sale）、Rush purchase、Spike

**Product**: An item at style level (e.g. a specific bag or sneaker colorway) offered for sale in a Drop.
_Avoid_: SKU（指规格变体）、Item

**Customer**: A registered person who participates in Drops.
_Avoid_: Consumer、User（User 保留给账号/技术实体）、消费者

**Order**: A Customer's purchase of a Product, created asynchronously after a successful flash-sale request and completed by payment within a window.
_Avoid_: Purchase、Transaction

**Review**: A Customer's post-purchase comment on a Product. Reviews pass through Moderation before being published and possibly indexed into the Knowledge Base as purchase opinions.
_Avoid_: Comment、Feedback

**Moderation**: The process that filters Reviews (rules on rating, length, and sensitivity) before they are published or indexed.
_Avoid_: Approval、审核（代码内规范名用 Moderation）

**Knowledge Base**: The RAG corpus for the Q&A Assistant, assembled from official Product documents and moderated Reviews.
_Avoid_: Corpus、Vector store、知识库（代码内规范名用 Knowledge Base）

**Q&A Assistant**: The RAG-based product consultation service that answers Customer questions by retrieving from the Knowledge Base.
_Avoid_: Chatbot、智能客服

**Inventory**: The remaining sellable quantity of a Product in a Drop. Reserved atomically at flash-sale hit (pre-reservation) and released back when payment times out.
_Avoid_: Stock、库存（代码内规范名用 Inventory）

**Payment Window**: The bounded period after a flash-sale hit during which the Customer must pay, otherwise the reserved Inventory is released.
_Avoid_: 支付时限、Pay deadline
