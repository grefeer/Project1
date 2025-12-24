package com.aiqa.project1.worker;

public enum MilvusRetrieverName {
    // 1. 混合检索节点：结合向量和关键词搜索（适用于大多数复杂问题）
    HYBRID_RETRIEVER(MilvusHybridRetrieveWorker.class),

    // 2. 过滤检索节点：首先提取文档名，然后进行强制元数据过滤（适用于用户指定文档）
    FILTER_RETRIEVER(MilvusFilterRetrieveWorker.class),

    // 3. 查询检索节点：基于元数据进行精确查询（非向量搜索）
    QUERY_RETRIEVER(MilvusQueryRetrieveWorker.class),

    WEB_SEARCH_RETRIEVER(WebSearchWorker.class);

    private final String className;

    MilvusRetrieverName(Class<?> clazz) {
        this.className = clazz.getSimpleName();
    }

    /**
     * 获取节点类名字符串
     * @return 节点类名
     */
    public String getClassName() {
        return className;
    }

}