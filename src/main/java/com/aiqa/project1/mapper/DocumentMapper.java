package com.aiqa.project1.mapper;

import com.aiqa.project1.pojo.DailyCountVO;
import com.aiqa.project1.pojo.document.Document;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;


@Mapper
public interface DocumentMapper extends BaseMapper<Document> {

    // 新增：过去7天每天的成功上传文档数（按日期分组）
    @Select("""
        SELECT
        DATE(update_time) as date,
        COUNT(*) as count
        FROM document
        WHERE status = 'AVAILABLE'
        AND update_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
        GROUP BY DATE(update_time)
        ORDER BY date
    """)
    List<DailyCountVO> getSevenDaysDailySuccessDocumentCount();

}

