package com.aiqa.project1.nodes;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

import java.util.List;


// 查询拆分工具类
@Component
public class QuerySplitter {
    private final OpenAiChatModel douBaoLite;
    private final String SUB_QUERY_TEMPLATE = """
                你的目的是通过生成可以用工具回答的子问题列表来帮助解答复杂的用户问题。
                请你按照以下的拆解方法来思考如何生成子问题：
                1、理解任务	任务不能模糊，明确输入输出
                2、提取子目标	将大问题拆成多个小问题
                3、识别依赖关系	主题→大纲→段落内容
                4、定义子任务	每个子任务必须独立可执行
                以下的拆分子问题时遇到的常见错误，请你在拆分时绝对不能出现：
                1、子任务目标不明确 模型行为混乱
                2、子任务之间逻辑冲突 输出不稳定
                3、把子任务设计得过大 非原子任务
                4、太多子任务 流程复杂难维护
                在完成任务时，你需要考虑以下指导原则：
                * 尽可能具体
                * 子问题应与用户问题相关
                * 子问题应能通过提供的工具回答
                * 每个工具可以生成多个子问题
                * 必须使用工具的名称，而不是描述
                如果你认为某个工具不相关，你无需使用它
                
                # 示例 1
                <Tools>
                ```json
                {
                "uber_10k": "提供 Uber 2021 年度财务信息",
                "lyft_10k": "提供 Lyft 2021 年度财务信息"
                }
                ```
                
                <User Question>
                比较并对比 Uber 和 Lyft 在 2021 年的收入增长和 EBITDA
                
                <Output>
                ```json
                {
                "items": [
                {
                "sub_question": "Uber 的收入增长是多少",
                "tool_name": "uber_10k"
                },
                {
                "sub_question": "Uber 的 EBITDA 是多少",
                "tool_name": "uber_10k"
                },
                {
                "sub_question": "Lyft 的收入增长是多少",
                "tool_name": "lyft_10k"
                },
                {
                "sub_question": "Lyft 的 EBITDA 是多少",
                "tool_name": "lyft_10k"
                }
                ]
                }
                ```
                
                # 示例 2
                <Tools>
                ```json
                {
                "pg_essay": "Paul Graham关于《我所从事的工作》的文章",
                }
                ```
                
                <User Question>
                Paul Graham在 YC 之前、期间和之后的生活有何不同?
                
                <Output>
                ```json
                {
                "items": [
                {
                "sub_question": "Paul Graham在YC之前的生活是怎样的",
                "tool_name": "pg_essay"
                },
                {
                "sub_question": "Paul Graham在YC期间的生活是怎样的",
                "tool_name": "pg_essay"
                },
                {
                "sub_question": "Paul Graham在YC之后的生活是怎样的",
                "tool_name": "pg_essay"
                }
                ]
                }
                ```
                
                # 示例 3
                <Tools>
                ```json
                %s
                ```
                
                <User Question>
                %s
                
                <Output>
                """;

        // 将原始查询拆分为多个子查询
        public List<SubQuery1> splitQuery(String originalQuery, List<ToolMetaData1> toolMetaDataList) {

            String prompt = String.format(SUB_QUERY_TEMPLATE, toolMetaDataList.toString(), originalQuery);

            // 调用LLM获取拆分结果
            String splitResult = douBaoLite.chat(prompt);
            // 解析结果为子查询列表
            JSONObject childQuery = new JSONObject(splitResult.replace("```json", "").replace("```", ""));

            JSONArray subQuery = childQuery.getJSONArray("items");
            return subQuery.stream()
                    .map(obj -> {
                        JSONObject jsonObject = (JSONObject) obj;
                        return new SubQuery1(jsonObject.get("sub_question", String.class), jsonObject.get("tool_name", String.class));
                    })
                    .toList();
        }

    public QuerySplitter(OpenAiChatModel douBaoLite) {
        // 初始化OpenAI Chat模型（用于拆分查询）
        this.douBaoLite = douBaoLite;
    }

    // 将原始查询拆分为多个子查询
    public List<SubQuery> splitQuery(String originalQuery, ToolMetaDataList toolMetaDataList) {

        String prompt = String.format(SUB_QUERY_TEMPLATE, toolMetaDataList.toString(), originalQuery);

        // 调用LLM获取拆分结果
        String splitResult = douBaoLite.chat(prompt);
        // 解析结果为子查询列表
        JSONObject childQuery = new JSONObject(splitResult.replace("```json", "").replace("```", ""));

        JSONArray subQuery = childQuery.getJSONArray("items");
        return subQuery.stream()
                .map(obj -> {
                    JSONObject jsonObject = (JSONObject) obj;
                    return new SubQuery(jsonObject.get("sub_question", String.class), jsonObject.get("tool_name", String.class));
                })
                .toList();
    }
}

