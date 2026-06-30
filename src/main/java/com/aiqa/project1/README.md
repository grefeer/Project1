# com.aiqa.project1

这是应用的 Java 根包，承载 Spring Boot 启动入口和主要业务模块。`Project1Application` 通过 `@SpringBootApplication` 启动应用，并开启定时任务能力。

## 目录结构

- `config`: Spring Bean、第三方客户端、线程池、Redis、RabbitMQ、Milvus、LLM 等基础配置。
- `controller`: 面向前端或外部调用方的 REST API。
- `service`: 业务服务接口。
- `service/impl`: 用户、标签、文档、问答等业务实现。
- `mapper`: MyBatis-Plus Mapper 和少量自定义 SQL。
- `pojo`: 数据库实体、请求响应 DTO、RAG 状态对象和通用返回对象。
- `consumer`: RabbitMQ 消费者，用于文档处理、向量更新、RAG 执行和异步落库。
- `utils`: JWT、Redis、COS/MinIO、Milvus、文本处理、SSE、缓存、限流等工具组件。
- `initializer`: 应用启动后的初始化任务。
- `interceptor`: 请求拦截和鉴权上下文处理。

## 主要流程

1. 请求首先进入 `controller`，由控制器解析参数、读取鉴权信息并调用业务服务。
2. `service/impl` 负责执行业务规则，必要时调用 `mapper`、`utils` 或发送 RabbitMQ 消息。
3. `consumer` 处理异步任务，例如文档抽取入库、RAG 图执行、聊天记录落库和向量删除。
4. `pojo` 下的实体和 DTO 在控制器、服务、Mapper、消息队列和 RAG 图之间传递数据。

## 维护提示

- 新增业务接口时，优先保持 `controller -> service -> mapper/utils` 的分层方向。
- 新增跨模块常量时，优先放入 `config/SystemConfig.java` 或对应配置类，避免散落在业务代码中。
- 对涉及 Redis、RabbitMQ、Milvus、对象存储的变更，应同步检查 `config`、`consumer` 和 `utils` 中的耦合点。
