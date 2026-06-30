# pojo.user

本目录存放用户领域的数据对象。

## 主要文件

- `User`: 用户实体，对应 `user` 表。
- `UserForCsv`: 批量导入用户时使用的 CSV/XLSX 映射对象。
- `UserResponseData`: 用户相关接口返回数据基类。
- `RegisterDataUser`: 注册接口返回数据。
- `LoginDataUser`: 登录接口返回数据，通常包含 Token 和用户基础信息。
- `InfoDataUser`: 用户详情接口返回数据。

## 使用场景

这些对象主要在 `UserController`、`UserServiceimpl`、`UserMapper`、`SystemInitRunner` 和标签服务中使用。

## 维护提示

- 密码字段应只在服务层进行加密和校验，不应直接返回给前端。
- 批量导入字段变更时，需要同步检查 EasyExcel 解析逻辑和校验逻辑。
- 用户角色字段会影响控制器权限判断和默认标签分配逻辑。
