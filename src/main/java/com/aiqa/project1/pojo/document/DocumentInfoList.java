package com.aiqa.project1.pojo.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentInfoList {
    private List<Object> successList = new ArrayList<>();
    private List<Object> failList = new ArrayList<>();
}
