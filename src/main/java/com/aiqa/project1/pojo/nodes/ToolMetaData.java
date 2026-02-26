package com.aiqa.project1.pojo.nodes;

public class ToolMetaData {
    public String name;
    public String description;

    public ToolMetaData(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public ToolMetaData() {}

    public static ToolMetaData of(String name, String description) {
        return new ToolMetaData(name, description);
    }

    public static ToolMetaData of() {
        return new ToolMetaData();
    }

    @Override
    public String toString() {
        return "\"%s\": \"%s\"".formatted(name, description);
    }
}
