package com.aiqa.project1.utils;

import cn.hutool.core.lang.UUID;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本结构化/向量化工具类
 */
@Component
public class ChildParentTextStructUtil {
    private final EmbeddingModel onnxMiniLML12V2EmbeddingModel;
    private final OpenAiChatModel douBaoLite;
    private final OllamaChatModel qwen2_5Instruct;
    private final OllamaChatModel gemma3_270m;

    // 文本分块配置
    @Value("${langchain4j.TextConfig.BIG_CHUNK_SIZE}")
    private int BIG_CHUNK_SIZE;
    @Value("${langchain4j.TextConfig.BIG_CHUNK_OVERLAP}")
    private int BIG_CHUNK_OVERLAP;
    @Value("${langchain4j.TextConfig.SMALL_CHUNK_SIZE}")
    private int SMALL_CHUNK_SIZE;
    @Value("${langchain4j.TextConfig.SMALL_CHUNK_OVERLAP}")
    private int SMALL_CHUNK_OVERLAP;

    private final String TOKENIZER_PATH = System.getenv("bgeM3Onnx") + "\\tokenizer.json";

    public ChildParentTextStructUtil(@Qualifier("bgeM3") EmbeddingModel onnxMiniLML12V2EmbeddingModel, OpenAiChatModel douBaoLite, OllamaChatModel qwen2_5Instruct, OllamaChatModel gemma3_270m) {
        this.onnxMiniLML12V2EmbeddingModel = onnxMiniLML12V2EmbeddingModel;
        this.douBaoLite = douBaoLite;
        this.qwen2_5Instruct = qwen2_5Instruct;
        this.gemma3_270m = gemma3_270m;
    }

    // 长文本分块（适配大模型/检索）
    public List<TextSegment> splitText(Document document) {
        return DocumentSplitters.recursive(BIG_CHUNK_SIZE, BIG_CHUNK_OVERLAP).split(document);
    }

    // 生成文本向量（适配Milvus）
    public Embedding generateVector(String text) {
        return onnxMiniLML12V2EmbeddingModel.embed(text).content();
    }

    // 提取文档元数据
    public String extractDocumentInfo(String text, String modelName) {
        // 简化实现：基于分词+词频，可替换为LangChain4j调用LLM提取
        // 示例：return llmClient.extractDocumentInfo(text);

        String temple = """
                你现在是文本元信息提取专员，服务于通用信息处理场景，核心职责是精准识别并提取文本中的标题、作者名、时间三类关键元信息。
                
                ## 执行准则
                ### 必须遵守
                1. 仅提取标题、作者名、时间三类信息，不得遗漏或新增其他字段
                2. 若文本中某类信息缺失，对应字段标注为"无"
                3. 时间信息需转换为"YYYY-MM-DD"格式（如"2023年5月1日"→"2023-05-01"）
                
                ### 禁止操作
                1. 禁止修改原始文本内容
                2. 禁止主观推断未明确提及的信息
                3. 禁止混淆不同类型信息（如将作者名填入标题字段）
                
                ## 文本解析流程
                1. 通读原始文本，标记所有可能包含目标信息的段落
                2. 按标题→作者名→时间顺序逐一提取：
                   - 标题：优先选择文本开头加粗/大字体内容，无明显标识时选择首段核心概括句
                   - 作者名：寻找"作者""文/""by"等标识后的署名
                   - 时间：识别包含年月日的完整时间表述
                3. 验证提取结果：确认三类信息无交叉重叠，时间格式符合要求
                
                ## 输出规范
                采用JSON格式输出，包含以下三个字段：
                - title：文本标题（字符串）
                - author：作者姓名（字符串）
                - date：发布时间（格式：YYYY-MM-DD，无则为"无"）
                
                示例输出：
                {
                  "title": "人工智能发展趋势报告",
                  "author": "张明",
                  "date": "2023-10-15"
                }
                
                开始处理：<原始文本>%s</原始文本>
                
                """;
        String prompt = temple.formatted(text);

        String responses = "";
        try {
//            responses = deepSeek.chat(prompt);
            System.out.println("--------------------------------------");
            if (modelName.contains("qwen"))
                responses = qwen2_5Instruct.chat(prompt);
            else if (modelName.contains("gemma"))
                responses = gemma3_270m.chat(prompt);
            else
                responses = douBaoLite.chat(prompt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return responses; // 实际需替换为真实逻辑
    }

    // 提取文档摘要
    public String extractAbstract(String text, String modelName) {

        String temple = """
                # 文档摘要生成助手指令
                
                ## 身份与职责
                你是一位专业的文档处理助手，核心职责是高效处理用户提供的文档，精准生成符合要求的摘要内容，为用户节省信息筛选时间。
                
                ## 核心准则
                1. 原文优先：若文档自带摘要，必须以原文摘要为唯一依据，禁止自行改写或补充内容
                2. 语言规范：输出内容必须为简体中文，确保语义准确、语句通顺
                3. 信息完整：严格保留原文摘要的核心信息，不得遗漏关键内容
                4. 边界清晰：仅处理摘要相关内容，不涉及文档其他部分的解读或分析
                
                ## 输入处理规则
                <文档内容>%s</文档内容>
                - 读取优先级：优先识别文档中明确标注为"摘要"或"Summary"的段落
                - 异常处理：若文档无摘要内容，自己生成摘要
                
                ## 执行流程
                1. **摘要识别**：逐段扫描文档内容，定位标题包含"摘要"或"Summary"的段落
                2. **内容提取**：完整提取已识别的摘要段落全部内容
                3. **语言转换**：若原文摘要为非中文，将其翻译为简体中文；若为中文则直接保留
                4. **结果校验**：检查输出内容是否完整覆盖原文摘要信息，确认无遗漏或错误
                
                ## 输出规范
                - 输出内容：仅包含处理后的中文摘要文本
                - 格式要求：直接输出纯文本，无任何额外标签或说明
                - 字数限制：与原文摘要长度保持一致，原则上不超过300字
                - 示例参考：
                  原文摘要（英文）："This study examines the impact of climate change on agricultural productivity..."
                  输出："本研究探讨了气候变化对农业生产力的影响..."
                """;
        String prompt = temple.formatted(text);

        String responses = "";
        try {
//            responses = deepSeek.chat(prompt);
            System.out.println("--------------------------------------");
            if (modelName.contains("qwen"))
                responses = qwen2_5Instruct.chat(prompt);
            else if (modelName.contains("gemma"))
                responses = gemma3_270m.chat(prompt);
            else
                responses = douBaoLite.chat(prompt);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return responses;
    }

    /**
     * 父子分块逻辑
     * 父块：较大的文本段，用于召回后提供上下文
     * 子块：较小的文本段，用于生成向量提高检索精度
     */
    public List<TextSegment> splitTextParentChild(Document document) {
        // 1. 先切分出父块 (例如 1000 tokens)
        List<TextSegment> parentSegments = DocumentSplitters.recursive(
                BIG_CHUNK_SIZE,
                BIG_CHUNK_OVERLAP
        ).split(document);
        List<TextSegment> allSegments = new ArrayList<>();

        for (TextSegment parent : parentSegments) {
            String parentId = UUID.randomUUID().toString(); // 为父块生成唯一ID

            // 2. 将每个父块切分为更小的子块 (例如 300 tokens)
            List<TextSegment> children = DocumentSplitters.recursive(
                    SMALL_CHUNK_SIZE,
                    SMALL_CHUNK_OVERLAP
            ).split(Document.from(parent.text()));

            for (TextSegment child : children) {
                // 将父块的全文和 ID 存入子块的元数据中
                Metadata metadata = child.metadata();
                metadata.put("parent_id", parentId);
                metadata.put("parent_text", parent.text()); // 关键：存储父块文本以便检索时直接获取
                allSegments.add(child);
            }
        }
        return allSegments;
    }
}