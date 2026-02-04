package com.aiqa.project1.pojo.tag;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("document_tag")
public class DocumentTag implements Serializable {
    @TableField("document_id")
    private String documentId;
    @TableField("tag_id")
    private Integer tagId;
}