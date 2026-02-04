package com.aiqa.project1.pojo.tag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_tag")
public class UserTag {
    @TableField("user_id")
    private Integer userId;
    @TableField("tag_id")
    private Long tagId;
}
