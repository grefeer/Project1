package com.aiqa.project1.pojo.document;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class DocumentUploadData extends DocumentResponseData{

    private String ossPath;
    private Long fileSize;
    private String fileType;
    private String previewUrl;
    private String status = "NOT_EMBEDDED";
    private String tagType = "PERSONAL";

    public DocumentUploadData(String documentId, String documentName, String ossPath, Long fileSize, String fileType, Long version, LocalDateTime uploadTime, String previewUrl, String status, String tagType) {
        super(documentId, documentName, version, uploadTime);

        this.ossPath = ossPath;
        this.fileSize = fileSize;
        this.fileType = fileType;
        this.previewUrl = previewUrl;
        this.status = status;
        this.tagType = tagType;
    }

    public DocumentUploadData() {
    }


}
