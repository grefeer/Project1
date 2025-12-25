package com.aiqa.project1.worker;

import com.aiqa.project1.config.SystemConfig;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 检索Worker工厂类
 * 负责创建和管理不同类型的检索Worker实例
 */
@Component
@Slf4j
public class RetrieveWorkerFactory {

    @Autowired
    private MilvusFilterRetrieveWorker milvusFilterRetrieveWorker;
    
    @Autowired
    private MilvusHybridRetrieveWorker milvusHybridRetrieveWorker;
    
    @Autowired
    private MilvusQueryRetrieveWorker milvusQueryRetrieveWorker;
    
    @Autowired
    private WebSearchWorker webSearchWorker;
    
    private Map<String, AbstractRetrieveWorker> workerMap;

    @PostConstruct
    public void init() {
        workerMap = new HashMap<>();
        workerMap.put(SystemConfig.RETRIEVER_MILVUS_FILTER, milvusFilterRetrieveWorker);
        workerMap.put(SystemConfig.RETRIEVER_MILVUS_HYBRID, milvusHybridRetrieveWorker);
        workerMap.put(SystemConfig.RETRIEVER_MILVUS_QUERY, milvusQueryRetrieveWorker);
        workerMap.put(SystemConfig.RETRIEVER_WEB_SEARCH, webSearchWorker);
        
        log.info("检索Worker工厂初始化完成，共注册{}个检索器", workerMap.size());
    }

    /**
     * 根据检索器名称获取对应的Worker实例
     * @param retrieverName 检索器名称
     * @return 对应的Worker实例
     */
    public AbstractRetrieveWorker getWorker(String retrieverName) {
        AbstractRetrieveWorker worker = workerMap.get(retrieverName);
        if (worker == null) {
            throw new IllegalArgumentException("未找到名为 " + retrieverName + " 的检索器");
        }
        return worker;
    }

    /**
     * 获取所有可用的检索器名称
     * @return 检索器名称集合
     */
    public java.util.Set<String> getAllRetrieverNames() {
        return workerMap.keySet();
    }

    /**
     * 检查指定的检索器是否存在
     * @param retrieverName 检索器名称
     * @return 是否存在
     */
    public boolean hasRetriever(String retrieverName) {
        return workerMap.containsKey(retrieverName);
    }

    /**
     * 获取默认检索器
     * @return 默认检索器实例
     */
    public AbstractRetrieveWorker getDefaultWorker() {
        return getWorker(SystemConfig.RETRIEVER_DEFAULT);
    }
}