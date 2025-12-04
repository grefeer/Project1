package com.aiqa.project1.utils;

// 自定义业务异常（统一异常处理，替代Controller中模糊的catch）
public class BusinessException extends RuntimeException {
    private final int code;
    private final String message;
    private final Object data;

    public BusinessException(int code, String message, Object data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
