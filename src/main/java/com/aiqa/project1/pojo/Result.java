package com.aiqa.project1.pojo;


import lombok.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result {
    private Integer code;
    private String message;
    private Object data;

    public static Result success(String message) {
        Result result = new Result();
        result.setCode(200);
        result.setMessage(message);
        return result;
    }

    public static Result success(String message, Object data) {
        Result result = success(message);
        result.setData(data);
        return result;
    }

    public static Result error(String message) {
        Result result = new Result();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }

    public static Result error(String message, Object data) {
        Result result = error(message);
        result.setData(data);
        return result;
    }

    public static Result define(String message) {
        Result result = new Result();
        result.setMessage(message);
        return result;
    }


    public static Result define(String message, Object data) {
        Result result = define(message);
        result.setData(data);
        return result;
    }

    public static Result define(Integer code, String message, Object data) {
        Result result = define(message, data);
        result.setCode(code);
        return result;
    }

}
