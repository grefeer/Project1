package com.aiqa.project1.pojo.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponseData {

    private Integer userId;
    private String username;
    private String role;
    private List<String> tags = new ArrayList<>(); // 添加tags

}
