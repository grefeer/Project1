package com.aiqa.project1.nodes;

import lombok.Data;

@Data
public class SubQuery {
    private String sub_question;
    private String tool_name;
    private String sub_answer;

    public SubQuery(String subQuestion, String toolName) {
        this.sub_question = subQuestion;
        this.tool_name = toolName;
    }

    @Override
    public String toString() {
        return "SubQuery{question='" + sub_question + "', tool='" + tool_name + "'}";
    }
}
