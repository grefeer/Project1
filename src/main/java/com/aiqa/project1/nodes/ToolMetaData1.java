package com.aiqa.project1.nodes;

import java.io.Serializable;

public class ToolMetaData1 implements Serializable {
    private static final long serialVersionUID = 1L;

    public String name;
    public String description;

    public ToolMetaData1(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public ToolMetaData1() {}

    public static ToolMetaData1 of(String name, String description) {
        return new ToolMetaData1(name, description);
    }

    public static ToolMetaData1 of() {
        return new ToolMetaData1();
    }

    @Override
    public String toString() {
        return "\"%s\": \"%s\"".formatted(name, description);
    }
}
