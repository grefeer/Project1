package com.aiqa.project1.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Data
@TableName("dead_letter")
public class DeadLetter {
    @TableId(type = IdType.AUTO)
    private String id;
    @TableField("user_id")
    private Integer userId;
    @TableField("error_filed")
    private String errorFiled;
    @TableField("message")
    private String message;
    @TableField("created_time")
    private LocalDateTime createdTime;
}
