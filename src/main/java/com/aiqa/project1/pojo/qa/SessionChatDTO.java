package com.aiqa.project1.pojo.qa;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionChatDTO {
    private Integer sessionId; // 会话ID
    private String sessionName; // 会话名称（可选，如“2024.acl-long.393.pdf相关”）
    private LocalDateTime lastActiveTime; // 最后活跃时间（用于多session排序）
    private List<UserChatMemory> messages = new ArrayList<>();; // 该会话的消息列表
}
