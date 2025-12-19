package com.aiqa.project1.pojo.qa;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("userChatMemory")
public class UserChatMemory {
    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("user_id")
    private Integer userId;
    @TableField("session_id")
    private Integer sessionId;
    @TableField("memory_id")
    private Integer memoryId;
    @TableField("content")
    private String content;
}
