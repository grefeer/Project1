# TechDocRAG
TechDocRAG 是基于 **Java 技术栈** 构建的企业内部技术交流及设备操作指南智能问答系统，依托 RAG（检索增强生成）技术实现多格式技术文档的上传解析、语义检索，并结合大语言模型为用户提供精准的技术咨询服务。系统内置多级权限管控机制保障敏感信息安全，通过流式对话与上下文记忆能力提升问答交互效率。

## 核心特性
### 🔑 精细化权限管控
* 基于标签体系的文件访问权限：用户上传文件时绑定专属标签，仅拥有该标签 / 标签父级的用户可访问文件  
* 区分普通用户 / 管理员双角色权限体系，权限粒度覆盖文件、用户、标签、系统监控全维度
### 📤 多格式文档管理
* 支持多格式技术文档上传与自动解析（PDF、Word、Excel、Markdown 等）
* 文档上传后自动完成内容提取、清洗、向量化处理，为语义检索提供数据基础
### 💬 智能问答与对话管理
* 支持流式对话交互，保留上下文记忆，对话体验更流畅
* 对话全生命周期管理：支持收藏、删除、重命名对话
* 多策略检索模式，兼顾检索精度与召回率：
  * 朴素检索：基础语义匹配检索
  * 子问题检索：拆分复杂问题为子问题，多维度检索
  * 反思检索：基于检索结果迭代优化，提升答案准确性
### 🛠️ 管理员专属功能
* 用户管理：查看全量普通用户信息、单用户注册、CSV 批量导入用户
* 标签管理：查看全量标签、单标签注册、CSV 批量注册标签
* 系统监控：可视化监控核心数据：
* 文档维度：总文档数、有效文档数
* 用户维度：用户总数、各部门用户占比、近 7 天活跃用户数
* 行为维度：近 7 天文档保存数、用户提问使用量
# 快速开始
## 环境要求
* JDK 17 及以上
* Maven 3.8+ / Gradle 7.0+
* 关系型数据库（MySQL 8.0+ / PostgreSQL 14+）
* 向量数据库（Milvus 2.2+ / Redis Stack / Elasticsearch 8.x）
* 大语言模型服务（支持 OpenAI API / 本地化大模型如智谱 AI / 通义千问 / 讯飞星火）
## 安装步骤
  1. 克隆代码仓库
```Bash
git clone https://github.com/grefeer/Project1.git
cd Project1
```
  2. 配置系统参数
复制配置文件模板并修改核心配置（数据库连接、向量库地址、大模型 API 等）：
```Bash
# 复制并修改application.yml配置
cp src/main/resources/application-example.yml src/main/resources/application.yml
```
关键配置示例：
```yaml
# 数据库配置
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/techdoc_rag?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: your-password
    driver-class-name: com.mysql.cj.jdbc.Driver
# 向量库配置
vector:
  milvus:
    host: localhost
    port: 19530
    database: techdoc_rag
# 大模型配置
llm:
  type: zhipu # 可选：openai/zhipu/tongyi
  api-key: your-api-key
  base-url: https://open.bigmodel.cn/api/paas/v4/
```

## 功能详解
### 1. 文件上传与权限管控
普通用户在文件上传界面选择自有标签完成文件上传
系统通过 Apache POI、PDFBox 等工具自动完成文件解析、内容提取，再通过 Embedding 模型完成向量化入库
权限校验逻辑：仅标签匹配（含父标签）的用户可检索 / 访问该文件内容，核心通过 RBAC 模型 + 标签过滤实现
### 2. 智能问答交互
文档向量化完成后，用户可在问答界面新建对话，输入技术咨询问题
系统根据选择的检索模式（朴素 / 子问题 / 反思）检索向量库，结合大模型生成答案，并通过 SSE（Server-Sent Events）实现流式返回
支持对话收藏（便于后续查阅）、删除（清理无效对话）、重命名（语义化管理）
### 3. 管理员功能模块
#### 3.1 用户管理界面
查看所有普通用户的基础信息（账号、部门、所属标签、注册时间等）
单条注册新用户：填写账号、密码、部门、标签等信息完成注册
批量导入用户：上传符合模板的 CSV 文件，一键批量创建用户（支持数据校验与异常提示）
#### 3.2 标签管理界面
可视化展示全量标签及标签层级关系
单标签注册：创建新标签并配置父标签（若有），支持标签分类管理
批量注册标签：通过 CSV 文件批量导入标签体系，自动建立父子标签关联
#### 3.3 控制监控界面
核心指标实时监控：文档数、有效文档数、用户总数、部门用户占比
趋势化数据展示：近 7 天活跃用户数、近 7 天文档保存数、用户提问使用量
数据可视化图表（饼图 / 折线图），基于 ECharts 实现，直观呈现系统运行状态
## 技术栈

| 模块 | 核心技术/框架 |
| ------------- | ------------- |
| 后端框架 | Spring Boot 3.2+、MyBatis-Plus |
| 大模型集成	 |   LangChain4j（RAG 流程编排）/ 火山引擎 SDK / 嵌入模型 |
| 文档解析	 	 |   Apache POI、PDFBox、Tika |
| 权限控制	 	 |   JWT / 自定义标签权限过滤器 |
| 数据库		   |   MySQL 8.0+ / Redis / Milvus Java SDK |
| 流式交互		  |   SSE / WebSocket |
| 部署	      |   Docker / Ollama |



### 注意
此respiratory仅是后端代码，前端代码请见本账号的project1frontend项目


