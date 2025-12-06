package com.aiqa.project1.pojo.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponseData {

    private Integer userId;
    private String username;
    private String role;

}
