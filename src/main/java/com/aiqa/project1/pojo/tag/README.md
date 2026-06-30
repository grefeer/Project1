# pojo.tag

本目录存放组织标签、用户标签关系和标签查询返回对象。

## 主要文件

- `OrganizationTag`: 组织标签实体，对应 `organization_tags` 表。
- `OrganizationTagForCsv`: 批量导入标签时使用的 CSV/XLSX 映射对象。
- `UserTag`: 用户与标签关系实体，对应 `user_tag` 表。
- `DocumentTag`: 文档与标签关系实体，对应 `document_tag` 表。
- `TagNameCount`: 标签名称和用户数量统计对象。
- `TagQueryList`: 标签分页查询返回对象。
- `UserQueryList`: 标签下用户分页查询返回对象。
- `UserTagHierarchyVO`: 用户标签层级查询返回对象。

## 使用场景

这些对象主要被 `SpecialTagService`、`SpecialTagController`、`UserServiceimpl`、`DocsServiceimpl` 和各 Mapper 使用，用于权限范围、文档归属和批量导入。

## 维护提示

- 标签层级、父标签字段和用户-标签关系会影响文档权限与检索范围。
- 批量导入对象的字段顺序和注解需要与 Excel/CSV 模板保持一致。
- 删除标签或用户关系时，需要同步考虑文档、向量和权限缓存。
