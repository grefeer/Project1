package com.aiqa.project1.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import io.milvus.common.clientenum.FunctionType;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.*;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.ranker.BaseRanker;
import io.milvus.v2.service.vector.request.ranker.RRFRanker;
import io.milvus.v2.service.vector.request.ranker.WeightedRanker;
import io.milvus.v2.service.vector.response.DeleteResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
@Component
public class MilvusSearchUtils {

    private final MilvusClientV2 milvusClient;
    private final EmbeddingModel onnxMiniLML12V2EmbeddingModel;

    @Value("${milvus.collection-name}")
    private String collectionName;

    public MilvusSearchUtils(MilvusClientV2 milvusClient, @Qualifier("bgeM3") EmbeddingModel onnxMiniLML12V2EmbeddingModel) {
        this.milvusClient = milvusClient;
        this.onnxMiniLML12V2EmbeddingModel = onnxMiniLML12V2EmbeddingModel;
    }

    /**
     * 再milvus中创建collection
     * @param userId
     */
    public void createMilvusCollection(String userId) {
        String userCollectionName = collectionName + "_" + userId;
        HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                .collectionName(userCollectionName)
                .build();

        boolean isCollectionExists = milvusClient.hasCollection(hasCollectionReq);

        if (isCollectionExists) {
            System.out.println("Collection已存在，跳过创建");
            return;
        }

        // 定义 Schema
        CreateCollectionReq.CollectionSchema schema = milvusClient.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName("id")
                .dataType(DataType.Int64)
                .isPrimaryKey(true)
                .autoID(true)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("session_id")
                .dataType(DataType.Int64)
                .isPrimaryKey(false)
                .autoID(false)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("title")
                .dataType(DataType.VarChar)
                .maxLength(200)
                .enableAnalyzer(true)
                .enableMatch(true)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("timestamp")
                .dataType(DataType.VarChar)
                .maxLength(100)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("author")
                .dataType(DataType.VarChar)
                .maxLength(200)
                .enableAnalyzer(true)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("text")
                .dataType(DataType.VarChar)
                .maxLength(1000)
                .enableAnalyzer(true)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("come_from")
                .dataType(DataType.VarChar)
                .maxLength(150)
                .enableAnalyzer(true)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("text_dense")
                .dataType(DataType.FloatVector)
                .dimension(1024)
                .build());

        schema.addField(AddFieldReq.builder()
                .fieldName("text_sparse")
                .dataType(DataType.SparseFloatVector)
                .build());


        schema.addFunction(CreateCollectionReq.Function.builder()
                .functionType(FunctionType.BM25)
                .name("text_bm25_emb")
                .inputFieldNames(Collections.singletonList("text"))
                .outputFieldNames(Collections.singletonList("text_sparse"))
                .build());

        // 创建索引
        Map<String, Object> denseParams = new HashMap<>();

        IndexParam indexParamForTextDense = IndexParam.builder()
                .fieldName("text_dense")
                .indexName("text_dense_index")
                .indexType(IndexParam.IndexType.HNSW)
                .metricType(IndexParam.MetricType.COSINE)
                .extraParams(Map.of(
                        "M", 16,                // 每个节点16个连接
                        "efConstruction", 100      // 构建时探索100个近邻
                ))
                .build();

        Map<String, Object> sparseParams = new HashMap<>();
        sparseParams.put("inverted_index_algo", "DAAT_MAXSCORE");
        IndexParam indexParamForTextSparse = IndexParam.builder()
                .fieldName("text_sparse")
                .indexName("text_sparse_index")
                .indexType(IndexParam.IndexType.SPARSE_INVERTED_INDEX)
                .metricType(IndexParam.MetricType.BM25)
                .extraParams(sparseParams)
                .build();


        List<IndexParam> indexParams = new ArrayList<>();
        indexParams.add(indexParamForTextDense);
        indexParams.add(indexParamForTextSparse);

        // 创建 Collections
        CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                .collectionName(userCollectionName)
                .collectionSchema(schema)
                .indexParams(indexParams)
                .build();
        milvusClient.createCollection(createCollectionReq);
    }


    /**
     * 混合查询，即稀疏+稠密（向量）查询
     * @param query
     * @param keywords
     * @param userId
     * @param topK
     * @param rerankerParams
     * @return
     */
    public SearchResp hybridSearch(
            String query,
            String keywords,
            Integer userId,
            Integer sessionId,
            int topK,
            Object rerankerParams
    ) {
        // 混合检索
        float[] queryDense = onnxMiniLML12V2EmbeddingModel.embed(query).content().vector();

        List<BaseVector> queryTexts = Collections.singletonList(new EmbeddedText(keywords));
        List<BaseVector> queryDenseVectors = Collections.singletonList(new FloatVec(queryDense));

        List<AnnSearchReq> searchRequests = new ArrayList<>();
        searchRequests.add(AnnSearchReq.builder()
                .vectorFieldName("text_dense")
                .vectors(queryDenseVectors)
                .params("{\"nprobe\": 10, \"ef\": 50, \"M\": 16}")
                .topK(topK)
                .expr((sessionId == 0) ? "":"session_id==%s".formatted(sessionId))
                .build());
        searchRequests.add(AnnSearchReq.builder()
                .vectorFieldName("text_sparse")
                .vectors(queryTexts)
                .params("{\"drop_ratio_search\": 0.2}")
                .topK(topK)
                .expr((sessionId == 0) ? "":"session_id==%s".formatted(sessionId))
                .build());



        // 配置Reranker策略
        // 加权排名：如果结果需要强调某个向量场，请使用该策略。WeightedRanker 可以为某些向量场赋予更大的权重，使其更加突出。
        //RRFRanker（互易排名融合排名器）：在不需要特别强调的情况下选择此策略。RRFRanker 能有效平衡每个向量场的重要性。
        BaseRanker reranker = null;
        if (rerankerParams instanceof Integer) {
            reranker = new RRFRanker((Integer) rerankerParams);
        }else if (rerankerParams instanceof List<?> && ((List<?>) rerankerParams).getFirst() instanceof Float) {
            List<Float> data = new ArrayList<>();
            for (Object text : (List<?>) rerankerParams) {
                data.add((Float) text);
            }
            reranker = new WeightedRanker(data);
        } else {
            reranker = new BaseRanker() {
                @Override
                public Map<String, String> getProperties() {
                    return Map.of();
                }
            };
        }
        String[] outFields = {"text", "come_from"};
        HybridSearchReq hybridSearchReq = HybridSearchReq.builder()
                .collectionName(collectionName + "_" +userId.toString())
                .searchRequests(searchRequests)
                .ranker(reranker)
                .outFields(Arrays.asList(outFields))
                .topK(topK)
                .build();
        return milvusClient.hybridSearch(hybridSearchReq);
    }

    /**
     * 精确匹配过滤：查询 come_from 等于指定值的结果(无向量检索)
     * @param userId 用户ID
     * @param targetValue 目标值
     * @param outputFields 需要返回的字段列表
     * @return 查询结果
     */
    public QueryResp filterByComeFromExact(
            Integer userId,
            Integer sessionId,
            String targetValue,
            List<String> outputFields
    ) throws Exception {
        List<String> filterConditions = new ArrayList<>();

        // 构建过滤表达式：字符串需用双引号包裹
        String filterExpr = String.format("come_from == \"%s\"", targetValue);
        filterConditions.add(filterExpr);
        if (sessionId != -1) {
            filterConditions.add(String.format("session_id == %d", sessionId));
        }

        QueryReq queryParam = QueryReq.builder()
                .collectionName(collectionName + "_" +userId.toString())
                .filter(String.join(" AND ", filterConditions))
                .outputFields(outputFields)
                .build();

        // 此处假设已初始化 MilvusClient 实例（实际使用时需传入）
        return milvusClient.query(queryParam);
    }

    /**
     * 多值匹配过滤：查询 come_from 在指定值列表中的结果(无向量检索)
     * @param userId 用户ID
     * @param values 目标值列表
     * @param outputFields 需要返回的字段列表
     * @return 查询结果
     */
    public QueryResp filterByComeFromIn(Integer userId,
                                        Integer sessionId,
                                        List<String> values,
                                        List<String> outputFields) throws Exception {
        // 构建 in 表达式：数组元素用双引号包裹
        StringBuilder valuesStr = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            valuesStr.append("\"").append(values.get(i)).append("\"");
            if (i != values.size() - 1) {
                valuesStr.append(", ");
            }
        }

        List<String> filterConditions = new ArrayList<>();
        String filterExpr = String.format("come_from in [%s]", valuesStr);
        filterConditions.add(filterExpr);
        if (sessionId != -1) {
            filterConditions.add(String.format("session_id == %d", sessionId));
        }
        System.out.println("filterExpr:" + filterExpr);

        QueryReq queryParam = QueryReq.builder()
                .collectionName(collectionName + "_" +userId.toString())
                .filter(String.join(" AND ", filterConditions))
                .outputFields(outputFields)
                .build();

        // 此处假设已初始化 MilvusClient 实例（实际使用时需传入）
        return milvusClient.query(queryParam);
    }

    /**
     * 排除匹配过滤：查询 come_from 不等于指定值的结果
     * @param userId 用户ID
     * @param excludeValue 需排除的值
     * @param outputFields 需要返回的字段列表
     * @return 查询结果
     */
    public QueryResp filterByComeFromNotEqual(Integer userId,
                                                    Integer sessionId,
                                                    String excludeValue,
                                                    List<String> outputFields) throws Exception {

        List<String> filterConditions = new ArrayList<>();
        String filterExpr = String.format("come_from == \"%s\"", excludeValue);
        filterConditions.add(filterExpr);
        if (sessionId != -1) {
            filterConditions.add(String.format("session_id == %d", sessionId));
        }

        QueryReq queryParam = QueryReq.builder()
                .collectionName(collectionName + "_" +userId.toString())
                .filter(String.join(" AND ", filterConditions))
                .outputFields(outputFields)
                .build();

        return milvusClient.query(queryParam);
    }


    /**
     * 向量搜索+过滤：结合向量相似度搜索与 come_from 过滤
     * @param query 用户提问
     * @param topK 返回的TopK结果
     * @param userId 用户ID
     * @param filteredWords 过滤的 come_from 值
     * @return 搜索结果
     */
    public SearchResp filterSearch(
            String query,
            String filteredWords,
            Integer userId,
            Integer sessionId,
            int topK
    ) {
        float[] queryDense = onnxMiniLML12V2EmbeddingModel.embed(query).content().vector();

        List<BaseVector> queryVector = Collections.singletonList(new FloatVec(queryDense));
        HashMap<String, Object> searchParamsMap = new HashMap<>();
        searchParamsMap.put("hints", "iterative_filter");
        searchParamsMap.put("ef", "50");
        searchParamsMap.put("anns_field", "text_dense");

        List<String> filterConditions = new ArrayList<>();
        String filterExpr = String.format("come_from == \"%s\"", filteredWords);
        filterConditions.add(filterExpr);
        if (sessionId != -1) {
            filterConditions.add(String.format("session_id == %d", sessionId));
        }

        SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName + "_" +userId.toString())
                .data(queryVector)
                .topK(topK)
                .filter(String.join(" AND ", filterConditions)) // 详见https://milvus.io/docs/zh/boolean.md
                .outputFields(Arrays.asList("text", "come_from", "title", "author"))
                .searchParams(searchParamsMap)
                .build();
        return milvusClient.search(searchReq);
    }

    /**
     * 向量搜索+过滤：结合向量相似度搜索与 come_from 过滤
     * @param query 用户提问
     * @param topK 返回的TopK结果
     * @param userId 用户ID
     * @param filteredWords 过滤的 come_from 值
     * @return 搜索结果
     */
    public SearchResp filterSearchWithFiles(
            String query,
            String filteredWords,
            Integer userId,
            Integer sessionId,
            int topK
    ) {
        float[] queryDense = onnxMiniLML12V2EmbeddingModel.embed(query).content().vector();

        List<BaseVector> queryVector = Collections.singletonList(new FloatVec(queryDense));
        HashMap<String, Object> searchParamsMap = new HashMap<>();
        searchParamsMap.put("hints", "iterative_filter");
        searchParamsMap.put("ef", "50");
        searchParamsMap.put("anns_field", "text_dense");

        List<String> filterConditions = new ArrayList<>();
        String filterExpr = String.format("come_from in [%s]", filteredWords);
        filterConditions.add(filterExpr);
        if (sessionId != -1) {
            filterConditions.add(String.format("session_id == %d", sessionId));
        }

        SearchReq searchReq = SearchReq.builder()
                .collectionName(collectionName + "_" +userId.toString())
                .data(queryVector)
                .topK(topK)
                .filter(String.join(" AND ", filterConditions)) // 详见https://milvus.io/docs/zh/boolean.md
                .outputFields(Arrays.asList("text", "come_from", "title", "author"))
                .searchParams(searchParamsMap)
                .build();
        return milvusClient.search(searchReq);
    }

    public Boolean deleteDocumentEmbeddingsByName(String documentName, String userId) {

        DeleteReq deleteReq = DeleteReq.builder()
                .collectionName(collectionName + "_" + userId)
                .filter("come_from == \"" + documentName + "\"")
                .build();
        try {
            DeleteResp deleteResp = milvusClient.delete(deleteReq);
            return deleteResp.getDeleteCnt() > 0;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 将查询结果转化为符合的langchain4j中rag的形式，方便集成到langchain4j中
     * @param searchResp
     * @return
     */
    public static List<Content> getContentsFromSearchResp(SearchResp searchResp) {
        List<List<SearchResp.SearchResult>> searchResultsList = searchResp.getSearchResults();

        List<Content> contents = new ArrayList<>();

        // 处理检索结果（外层List通常对应多查询向量，这里取第一个）
        if (searchResultsList == null || searchResultsList.isEmpty()) {
            return contents;
        }

        List<SearchResp.SearchResult> searchResults = searchResultsList.getFirst();

        for (SearchResp.SearchResult result : searchResults) {
            Map<String, Object> entity = result.getEntity();
            if (entity == null) {
                continue;
            }

            // 提取文本内容（对应Milvus中的"text"字段）
            String text = (String) entity.get("text");
            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            // 构建TextSegment的元数据（包含来源信息）
            Map<String, Object> textSegmentMetadata = new HashMap<>();

            String comeFrom = (String) entity.get("come_from");
            System.out.println("comeFrom:" + comeFrom);
            if (comeFrom != null) {
                textSegmentMetadata.put("come_from", comeFrom); // 存储来源信息
            }
            TextSegment textSegment = TextSegment.from(text, new Metadata(textSegmentMetadata));


            // 构建Content的元数据（包含得分、ID等）
            Map<ContentMetadata, Object> contentMetadata = new HashMap<>();
            Float score = result.getScore();
            if (score != null) {
                contentMetadata.put(ContentMetadata.SCORE, score); // 存储检索得分
                contentMetadata.put(ContentMetadata.RERANKED_SCORE, score); // 存储检索得分
            }
            Object id = result.getId();
            if (id != null) {
                contentMetadata.put(ContentMetadata.EMBEDDING_ID, id); // 存储Milvus中的实体ID
            }

            // 创建Content并添加到结果列表
            Content content = Content.from(textSegment, contentMetadata);
            contents.add(content);
        }

        return contents;
    }

    public static List<Content> getContentsFromQueryResp(QueryResp queryResp) {
        List<QueryResp.QueryResult> queryResultList = queryResp.getQueryResults();

        List<Content> contents = new ArrayList<>();

        // 处理检索结果（外层List通常对应多查询向量，这里取第一个）
        if (queryResultList == null || queryResultList.isEmpty()) {
            return contents;
        }

        for (QueryResp.QueryResult result : queryResultList) {
            Map<String, Object> entity = result.getEntity();
            if (entity == null) {
                continue;
            }

            // 提取文本内容（对应Milvus中的"text"字段）
            String text = (String) entity.get("text");
            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            // 构建TextSegment的元数据（包含来源信息）
            Map<String, Object> textSegmentMetadata = new HashMap<>();
            String comeFrom = (String) entity.get("come_from");
            System.out.println("comeFrom:" + comeFrom);
            if (comeFrom != null) {
                textSegmentMetadata.put("come_from", comeFrom); // 存储来源信息
            }
            TextSegment textSegment = TextSegment.from(text, new Metadata(textSegmentMetadata));

            // 构建Content的元数据（包含得分、ID等）
            Map<ContentMetadata, Object> contentMetadata = new HashMap<>();
            contentMetadata.put(ContentMetadata.EMBEDDING_ID, null); // 存储Milvus中的实体ID
            contentMetadata.put(ContentMetadata.RERANKED_SCORE, null); // 存储Milvus中的实体ID
            contentMetadata.put(ContentMetadata.SCORE, null); // 存储Milvus中的实体ID

            // 创建Content并添加到结果列表
            Content content = Content.from(textSegment, contentMetadata);
            contents.add(content);
        }

        return contents;
    }

}
