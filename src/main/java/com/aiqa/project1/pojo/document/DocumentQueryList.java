package com.aiqa.project1.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


/**
 * pages : 总页数
 * total : 总条数
 *
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class DocumentQueryList {

    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private Integer pages;
    private List<DocumentUploadData> list;
}
