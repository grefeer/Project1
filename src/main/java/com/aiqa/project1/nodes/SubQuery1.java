package com.aiqa.project1.nodes;

import lombok.Data;

import java.io.Serializable;

@Data
public class SubQuery1 implements Serializable {
    private static final long serialVersionUID = 1L;


    private String sub_question;
    private String tool_name;
    private String sub_answer;

    public SubQuery1(String subQuestion, String toolName) {
        this.sub_question = subQuestion;
        this.tool_name = toolName;
    }

//    @Override
//    public String toString() {
//        return "SubQuery{question='" + sub_question + "', tool='" + tool_name + "'}";
//    }
}
