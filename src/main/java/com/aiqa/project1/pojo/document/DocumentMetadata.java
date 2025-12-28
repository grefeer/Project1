package com.aiqa.project1.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocumentMetadata {
    private String author;
    private String title;
    private String date;
    private String filename;
    private String abstractText;
}
