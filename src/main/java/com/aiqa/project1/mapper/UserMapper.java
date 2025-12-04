package com.aiqa.project1.mapper;

import com.aiqa.project1.pojo.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("select * from user")
    public List<User> getUsers();

    @Select("select userId from user where username=#{username}")
    public Integer getUserIdByUserName(String username);

    @Select("select * from user where username=#{username}")
    public User getUserByUserName(String username);

    @Insert("insert into user(username, password, role, phone, email, createdTime) " +
            "values (#{username}, #{password}, #{role}, #{phone}, #{email}, #{createdTime})")
    public Integer insertUser(User user);

    @Delete("delete from user where userId=#{userId}")
    public Integer deleteUserById(int userId);

    @Delete("delete from user where username=#{username}")
    public Integer deleteUserByUserName(String username);

    @Update("update user set username=#{username}, password=#{password}, role=#{role}, phone=#{phone}, email=#{email}, createdTime=#{createdTime} where userId=#{userId}")
    public Integer updateUserById(User user);

    @Update("update user set userId=#{userId}, password=#{password}, role=#{role}, phone=#{phone}, email=#{email}, createdTime=#{createdTime} where username=#{username}")
    public Integer updateUserByUserName(User user);

    @Update("update user set password=#{password} where username=#{username}")
    public Integer updateUserPasswordByUserName(@Param("username") String username, @Param("password") String password);

}
