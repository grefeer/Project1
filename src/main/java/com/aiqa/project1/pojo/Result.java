package com.aiqa.project1.pojo;


import lombok.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result {

    private Integer code;
    private String message;
    private Object data;

}
