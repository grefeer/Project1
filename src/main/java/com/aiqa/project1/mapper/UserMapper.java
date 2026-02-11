package com.aiqa.project1.mapper;

import com.aiqa.project1.pojo.tag.UserTag;
import com.aiqa.project1.pojo.user.User;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("select * from user")
    public List<User> getUsers();

    @Select("select username from user")
    public List<String> getUsersUserName();

    @Select("select user_id from user where username=#{username}")
    public Integer getUserIdByUserName(String username);

    @Select("select * from user where username=#{username}")
    public User getUserByUserName(String username);

    @Insert("insert into user(username, password, role, phone, email, created_time) " +
            "values (#{username}, #{password}, #{role}, #{phone}, #{email}, #{createdTime})")
    public Integer insertUser(User user);

    @Delete("delete from user where user_id=#{userId}")
    public Integer deleteUserById(int userId);

    @Delete("delete from user where username=#{username}")
    public Integer deleteUserByUserName(String username);

    @Update("update user set username=#{username}, password=#{password}, role=#{role}, phone=#{phone}, email=#{email}, created_time=#{createdTime} where user_id=#{userId}")
    public Integer updateUserById(User user);

    @Update("update user set role=#{role} where user_id=#{userId}")
    public Integer updateUserRoleById(Integer userId, String role);

    @Update("update user set user_id=#{userId}, password=#{password}, role=#{role}, phone=#{phone}, email=#{email}, created_time=#{createdTime} where username=#{username}")
    public Integer updateUserByUserName(User user);

    @Update("update user set password=#{password} where username=#{username}")
    public Integer updateUserPasswordByUserName(@Param("username") String username, @Param("password") String password);

    @Update("update user set phone=#{phone} where username=#{username}")
    public Integer updateUserPhoneByUserName(@Param("username") String username, @Param("phone") String phone);

    @Update("update user set email=#{email} where username=#{username}")
    public Integer updateUserEmailByUserName(@Param("username") String username, @Param("email") String email);


    // 方式2：自定义SQL查询（推荐复杂场景）
    @Select("SELECT u.* FROM user u " +
            "JOIN user_tag ut ON u.user_id = ut.user_id " +
            "WHERE ut.tag_id = #{tagId}")
    List<User> selectUserByTagId(@Param("tagId") Long tagId);

//    // 通用联表查询（兼容Wrapper）
//    @Select("SELECT u.* FROM user u " +
//            "JOIN user_tag ut ON u.user_id = ut.user_id " +
//            "${ew.customSqlSegment}")
//    List<User> selectUserByJoinWrapper(@Param(Constants.WRAPPER) Wrapper<UserTag> wrapper);

}
