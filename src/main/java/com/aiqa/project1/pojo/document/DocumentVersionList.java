package com.aiqa.project1.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersionList {
    private Long version;
    private LocalDateTime uploadTime;
    private String ossPath;
    private Long fileSize;
}
