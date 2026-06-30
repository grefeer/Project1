# pojo.nodes

本目录存放 RAG 图执行相关对象，包括 LangGraph4j 图定义、图状态和工具元数据。

## 主要文件

- `NaiveRAGGraph` / `NaiveRAGState`: 基础 RAG 流程图和状态。
- `AgenticRAGGraph` / `AgenticRAGState`: Agentic RAG 流程图和状态，包含更复杂的检索、反思或工具调用链路。
- `SubQueryRAGGraph` / `SubQueryRAGState`: 子问题拆分 RAG 流程图和状态。
- `State`: 消费者和缓存写入中使用的问答状态数据。
- `SubQuery` / `SubQuery1`: 子问题拆分结果。
- `ToolMetaData` / `ToolMetaData1` / `ToolMetaDataList`: 可用工具或检索器元数据描述。

## 依赖关系

图组件通常依赖 `utils` 中的 Milvus 检索器、Redis 工具、查询拆分器、LLM 模型和 RabbitMQ 模板。`RagConsumer` 会消费队列消息并触发对应图执行。

## 维护提示

- 图状态字段变更时，要同步检查初始状态构造、消费者入参、Redis 缓存和最终回答落库逻辑。
- 新增图节点时，应明确输入字段、输出字段和异常路径。
- 该目录中有非标准命名或无扩展名文件时，建议确认是否为临时说明文件，避免影响构建或团队理解。
