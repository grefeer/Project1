package com.aiqa.project1.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyCountVO {
    // 日期，格式：yyyy-MM-dd
    private String date;
    // 当日数量
    private Integer count;
}