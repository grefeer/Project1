package com.aiqa.project1.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq.Function;

import java.util.*;

import io.milvus.param.ConnectParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    @Value("${custom.host}")
    private String host;

    @Bean
    public MilvusClientV2  milvusClient() {
        return new MilvusClientV2(ConnectConfig.builder()
                .uri("http://%s:19530".formatted(host))
                .token("root:Milvus")
                .dbName("Project1")
                .build());
    }
}

