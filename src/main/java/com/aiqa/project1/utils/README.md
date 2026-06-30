# utils

本目录存放跨业务复用的工具组件和外部系统适配器。

## 主要类别

- 鉴权和通用异常: `JwtUtils`、`BusinessException`、`UserUtils`。
- 缓存和 Redis: `RedisStoreUtils`、`RedisPoolManager`、`CacheManager`、`CacheAsideUtils`、`ExpireKeyListener`。
- 文档处理: `TextExtractUtil`、`TextCleanUtil`、`TextStructUtil`、`ChildParentTextStructUtil`、`DataProcessUtils`、`TextDeduplicationUtil`。
- Milvus 检索: `MilvusSearchUtils`、`MilvusSearchUtils1`、`MilvusQueryRetriever`、`MilvusFilterRetriever`、`MilvusHybridRetriever` 以及对应 `ContentRetriever` 实现。
- 对象存储: `TencentCOSUtil`、`MinIOStoreUtils`。
- 问答辅助: `QuerySplitter`、`ChatMemoryManager`、`TimeoutControl`、`RateLimiter`。
- 异步和通知: `AsyncTaskExecutor`、`SseEmitterManager`、`ActivateSessionCleaner`。
- ID 和校验: `SnowFlakeUtil`、`RegexValidate`。

## 使用原则

工具类应封装稳定的技术细节，例如 Redis Key 访问、向量检索、文件抽取、对象存储路径和 SSE 连接管理。业务规则仍应留在 `service/impl`。

## 维护提示

- Redis Key、Milvus 集合名、对象存储路径这类跨模块协议改动时，要同步检查生产方和消费方。
- `MilvusSearchUtils` 与各 Retriever 的输入输出会影响 RAG 图节点，修改返回字段要检查 `pojo/nodes`。
- 工具组件中涉及外部服务的异常应提供足够上下文，便于消费者和服务层回写失败状态。
