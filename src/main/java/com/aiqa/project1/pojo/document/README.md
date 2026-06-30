# pojo.document

本目录存放文档领域的数据对象，覆盖数据库实体、版本记录、上传结果和列表查询返回。

## 主要文件

- `Document`: 文档主表实体，对应 `document` 表。
- `DocumentVersion`: 文档版本实体，对应 `document_version` 表。
- `DocumentTag`: 文档标签关系不在本目录，而在 `pojo/tag` 中维护。
- `DocumentTransferDTO`: 文档上传后传递给异步消费者的消息 DTO。
- `DocumentUploadData`: 文档上传接口返回数据。
- `DocumentResponseData`: 文档接口通用响应数据基类。
- `DocumentMetadata`: 文本抽取和 LLM 解析得到的文档元信息。
- `DocumentQueryList`: 文档列表查询返回结构。
- `DocumentInfoList`: 文档摘要列表项。
- `DocumentSingleListData`: 单文档详情返回结构。
- `DocumentVersionList`: 文档版本列表项。

## 使用场景

这些对象主要在 `DocsController`、`DocsServiceimpl`、`TextConsumer`、`DocumentMapper` 和文档处理工具之间传递。

## 维护提示

- 新增文档状态时，需要同步检查上传、删除、列表过滤、SSE 推送和消费者状态回写。
- 修改版本字段时，要同步检查下载、详情查询和 COS/MinIO 路径生成。
- 消息 DTO 变更需要同步 RabbitMQ 生产方和消费者。
