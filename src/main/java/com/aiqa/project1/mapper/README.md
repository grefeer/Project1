# mapper

本目录存放 MyBatis-Plus Mapper，负责数据库访问和少量自定义 SQL。

## 主要文件

- `UserMapper`: 用户表 CRUD、自定义查询、按用户名查询用户、更新用户信息、按标签查询用户。
- `UserTagMapper`: 用户与标签关系表操作，包含用户标签层级查询和按用户名/标签名插入关系。
- `SpecialTagMapper`: 组织标签表操作，包含按父标签查询和统计标签用户数。
- `DocumentMapper`: 文档表操作，包含近 7 天成功上传文档数统计。
- `DocumentVersionMapper`: 文档版本表操作。
- `DocumentTagMapper`: 文档与标签关系表操作。
- `SessionChatMapper`: 会话表操作，支持更新会话名称和收藏状态。
- `UserChatMemoryMapper`: 聊天记忆表操作，包含今日活跃用户、今日问答数和近 7 天问答数统计。
- `DeadLetterMapper`: 死信消息表操作。

## 使用方式

大多数 Mapper 继承 `BaseMapper<T>`，基础 CRUD 由 MyBatis-Plus 提供。复杂查询通过注解 SQL 或 XML Mapper 扩展。

## 维护提示

- 修改实体字段、表名或主键策略时，需要同步检查对应 `pojo` 实体和 SQL。
- 自定义 SQL 中的表名、字段名要与数据库迁移保持一致。
- 统计接口应注意时间字段、状态字段和空数据补齐逻辑。
