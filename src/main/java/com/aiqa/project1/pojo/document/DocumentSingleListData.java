package com.aiqa.project1.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentSingleListData {
    private String documentId;
    private String documentName;
    private String userId;
    private String fileSize;
    private String fileType;
    private String description;
    private Long currentVersion;
    private LocalDateTime uploadTime;
    private String status;
    private String previewUrl;
    private List<DocumentVersionList> documentVersionList;
}
