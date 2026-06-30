# consumer

本目录存放 RabbitMQ 消费者，负责将耗时任务从同步 HTTP 请求中拆出来异步执行。

## 主要文件

- `TextConsumer`: 消费文档处理消息，调用 `DataProcessUtils` 抽取文本、分块、向量化并更新文档状态，同时通过 SSE/Redis 回写处理进度。
- `RagConsumer`: 消费 RAG 执行消息，分别驱动 `AgenticRAGGraph`、`NaiveRAGGraph` 和 `SubQueryRAGGraph`。
- `MysqlUpdateConsumer`: 将 Redis 中的会话和聊天记忆同步回 MySQL。
- `UpdateEmbeddingConsumer`: 处理文档删除或更新后对应向量数据的清理。

## 消息处理约定

- 消费方法通过 `@RabbitListener` 绑定队列和交换机。
- 处理成功后手动 ack，失败时按当前逻辑写入 `DeadLetterMapper` 或触发拒绝。
- 消费者通常依赖 `utils` 中的 Redis、Milvus、SSE 和数据处理工具，也依赖 `mapper` 完成持久化。

## 维护提示

- 修改消息体结构时，要同步检查生产方、DTO 和消费者解析逻辑。
- 新增消费者时，需要明确是否手动 ack、是否需要死信记录、是否允许重试。
- 处理外部资源时应确保异常路径能回写状态，否则前端可能一直停留在处理中。
