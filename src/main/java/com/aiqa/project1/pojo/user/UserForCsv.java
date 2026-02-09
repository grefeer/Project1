package com.aiqa.project1.pojo.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.opencsv.bean.CsvBindByName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserForCsv {
    @CsvBindByName(column = "用户名")
    private String username;
    @CsvBindByName(column = "密码")
    private String password;
    @CsvBindByName(column = "角色")
    private String role;
    @CsvBindByName(column = "手机号")
    private String phone;
    @CsvBindByName(column = "邮箱")
    private String email;
}
