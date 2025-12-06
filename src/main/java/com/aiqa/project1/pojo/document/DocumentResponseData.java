package com.aiqa.project1.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponseData {
    private String documentId;
    private String documentName;
    private Long currentVersion;
    private LocalDateTime uploadTime;
}
