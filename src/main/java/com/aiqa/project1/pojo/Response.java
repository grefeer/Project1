package com.aiqa.project1.pojo;


public class Response {
    private Integer code;
    private String message;
    private ResponseData data;

    public Response(){}

    public Response(Integer code, String message, ResponseData data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ResponseData getData() {
        return data;
    }

    public void setData(ResponseData data) {
        this.data = data;
    }
}
