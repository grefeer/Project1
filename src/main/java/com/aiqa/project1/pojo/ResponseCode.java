package com.aiqa.project1.pojo;

public enum ResponseCode {
    REGISTER_SUCCESS(200, "注册成功"),
    LOGIN_SUCCESS(200, "登录成功"),
    USERNAME_EXIST(400, "用户名已被注册"),
    PASSWORD_ERROR(400, "用户名或密码错误"),
    TOKEN_ERROR(401, "未登录 / Token 过期 / Token 无效"),
    AUTH_ERROR(403,"权限不足"),
    RESOURCE_ERROR(404, "资源不存在（如用户 / 文档未找到）"),
    SERVER_ERROR(500, "服务端内部错误");

    private final int code;
    private final String message;

    ResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}
