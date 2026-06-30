# config

本目录存放应用基础设施配置类，负责创建 Spring Bean、读取配置项并连接外部系统。

## 主要文件

- `CorsConfig`: 配置跨域规则。
- `WebConfig`: 注册 Web MVC 相关配置，例如拦截器。
- `SecurityConfig`: 提供密码编码器等安全相关 Bean。
- `InterceptorConfig`: 预留拦截器相关 Bean 配置。
- `MybatisPlusConfig`: 启用 Mapper 扫描并配置 MyBatis-Plus 分页插件。
- `RedisConfiguration`: 配置 Redis 序列化和 `RedisTemplate`。
- `RedisExpireConfig`: 配置 Redis 过期事件监听容器。
- `RabbitConfig`: 定义 RabbitMQ 交换机、队列、绑定、消息转换器和监听容器。
- `MilvusConfig`: 创建 Milvus 客户端并包含集合 Schema 初始化逻辑。
- `EmbeddingModelConfig`: 提供向量模型 Bean，例如 `bgeM3`。
- `LLMConfig`: 配置多个聊天模型 Bean。
- `MinioConfig`: 创建 MinIO 客户端。
- `SnowFlakeConfig`: 创建雪花 ID 生成器。
- `SchedulerConfig`: 配置定时任务线程池。
- `UploadExecutor`: 配置文档上传和问答任务线程池。
- `WebSearchNodeConfig`: 配置 Web 搜索节点依赖。
- `SystemConfig`: 集中维护 RabbitMQ、Redis、缓存、检索器和并发控制常量。

## 依赖关系

这些配置类被 `utils`、`service/impl`、`consumer` 和 `pojo/nodes` 大量注入使用。修改 Bean 名称、队列名、Redis Key 格式或模型 Bean 时，需要同步检查对应调用方。

## 维护提示

- Bean 名称已经被 `@Qualifier` 引用时，改名需要全局搜索。
- RabbitMQ 队列、交换机和路由键应优先使用 `SystemConfig` 中的常量。
- 外部服务连接参数应来自配置文件，不要硬编码到业务类中。
