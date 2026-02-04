package com.aiqa.project1.pojo.tag;

import com.aiqa.project1.pojo.user.User;
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
public class TagQueryList {

    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private Integer pages;
    private List<OrganizationTag> list;
}
