# interceptor

本目录存放 Spring MVC 请求拦截器。

## 主要文件

- `UserInterceptor`: 实现 `HandlerInterceptor`，从请求中读取 JWT，解析为 `AuthInfo`，并将鉴权上下文传给后续处理流程。

## 职责边界

拦截器适合做横切逻辑，例如鉴权、上下文注入、请求日志或通用校验。具体业务权限判断应尽量留在业务服务或控制器中，以免拦截规则过重。

## 维护提示

- 修改 JWT Header、Token 解析方式或白名单路径时，需要同步检查 `config/WebConfig.java`。
- 拦截器中不要直接执行业务数据库写操作。
- `AuthInfo` 字段变化时，要同步检查控制器和服务中的读取逻辑。
