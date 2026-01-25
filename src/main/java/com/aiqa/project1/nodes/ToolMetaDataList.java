package com.aiqa.project1.nodes;

import java.util.ArrayList;
import java.util.List;

public class ToolMetaDataList {
    public List<ToolMetaData> tools = new ArrayList<>();
    public ToolMetaDataList() {}
    public ToolMetaDataList(List<ToolMetaData> tools) {
        this.tools = tools;
    }

    public void add(ToolMetaData toolMetaData) {
        this.tools.add(toolMetaData);
    }

    public Boolean add(List<String> toolNames, List<String> toolVersions) {
        if (toolNames == null || toolNames.isEmpty() || toolVersions == null || toolVersions.isEmpty()) {
            return false;
        }
        if (toolNames.size() != toolVersions.size()) {
            return false;
        }
        for (int i=0; i<toolNames.size(); i++) {
            this.tools.add(new ToolMetaData(toolNames.get(i), toolVersions.get(i)));
        }
        return true;
    }

    public void remove(ToolMetaData toolMetaData) {
        this.tools.remove(toolMetaData);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("```json\n" +
                "        {");
        for (ToolMetaData tool : tools) {
            builder.append(tool.toString()).append("\n");
        }
        builder.append("}\n```");
        return builder.toString();
    }
}
