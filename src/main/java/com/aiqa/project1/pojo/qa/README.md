# pojo.qa

本目录存放问答和聊天会话相关数据对象。

## 主要文件

- `SessionChat`: 会话实体，对应 `sessionchat` 表，保存会话名称、用户、收藏状态等信息。
- `SessionChatDTO`: 会话返回 DTO。
- `UserChatMemory`: 聊天记忆实体，对应 `userChatMemory` 表，保存单轮问答内容和活跃时间。
- `RetrievalDecision`: RAG 检索决策对象，用于表达是否需要检索、检索方式或相关判断。

## 使用场景

这些对象主要被 `AIAnswerController`、`QuestionAnsweringService`、`CacheAsideUtils`、`RedisStoreUtils`、`MysqlUpdateConsumer` 和对应 Mapper 使用。

## 维护提示

- 会话 ID、用户 ID、记忆 ID 是 Redis Key 和数据库记录之间的关联基础，改动字段时要同步检查 Key 格式。
- 聊天记忆写入路径同时涉及 Redis 缓存、RabbitMQ 异步同步和 MySQL，状态字段变更要全链路检查。
