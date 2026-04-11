# LangChain4j 核心知识补充文档

## 目录
1. [工具调用(Tool Calling)详解](#工具调用详解)
2. [RAG(检索增强生成)详解](#rag详解)
3. [流式输出详解](#流式输出详解)
4. [结构化输出详解](#结构化输出详解)
5. [最佳实践](#最佳实践)

---

## 工具调用详解

### 什么是工具调用?

**工具调用(Tool Calling/Function Calling)** 是让 LLM 能够调用外部函数的机制。

**为什么需要工具调用?**
- LLM 的知识是静态的,训练数据有截止日期
- LLM 无法获取实时数据(天气、股票、数据库查询等)
- LLM 无法执行操作(发送邮件、创建订单等)

**工具调用的工作流程**:
```
1. 用户: "今天济南的天气怎么样?"
2. LLM 分析: 需要调用 getWeather 工具
3. LLM 返回: 工具调用请求 { "name": "getWeather", "arguments": { "city": "济南" } }
4. 应用执行: 调用真实的天气 API,获取结果 "济南,晴,25℃"
5. 应用发送: 将工具结果返回给 LLM
6. LLM 生成: "今天济南天气晴朗,温度 25℃,适合出行。"
```

### ToolChainTest.java 详细注释

```java
package com.qi.langchain4j_learn_4_9;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 工具调用测试类
 * 
 * 功能概述:
 * 演示如何让 AI 调用外部工具(天气查询 API)来获取实时数据
 * 
 * 核心组件:
 * 1. ToolSpecification: 工具规范,描述工具的名称、功能、参数
 * 2. ToolExecutionRequest: LLM 返回的工具调用请求
 * 3. ToolExecutionResultMessage: 工具执行结果,返回给 LLM
 * 
 * 实现方式:
 * - 手动定义 ToolSpecification(不使用 @Tool 注解)
 * - 手动执行工具函数
 * - 手动将结果返回给 LLM
 * 
 * 对比:
 * - ToolChainTest: 手动方式,更底层,更灵活
 * - ToolChainTestWithAnnotation: 注解方式,更简洁,更易用
 */
@SpringBootTest
public class ToolChainTest {

    // 高德地图 API Key(需要替换为你自己的)
    // 申请地址: https://lbs.amap.com/
    private static final String AMAP_KEY = "1111111111111111111";

    // 城市名 -> adcode(行政区划代码)映射表
    // 高德 API 需要使用 adcode 而不是城市名称
    // 例如: "济南" -> "370100", "北京" -> "110000"
    private static final Map<String, String> CITY_TO_ADCODE = new HashMap<>();

    /**
     * 加载城市编码映射表
     * 
     * @BeforeAll 注解说明:
     * - 在所有测试方法执行之前运行一次
     * - 用于初始化共享资源(如加载配置文件)
     * - 必须是 static 方法
     * 
     * 功能说明:
     * 从 resources 目录下的 Excel 文件中加载城市名称和 adcode 的映射关系
     * 
     * 为什么需要这个映射表?
     * - 高德天气 API 需要使用 adcode 参数,不能直接使用城市名称
     * - 用户输入的是城市名称(如"济南"),需要转换为 adcode(如"370100")
     * - 预加载映射表可以提升查询性能
     */
    @BeforeAll
    static void loadAdcodeMapping() throws Exception {
        // 从 resources 目录读取 Excel 文件
        // getResourceAsStream() 会在 classpath 中查找文件
        // 路径以 "/" 开头表示从 resources 根目录开始
        try (InputStream is = ToolChainTest.class.getResourceAsStream("/AMap_adcode_citycode.xlsx")) {
            // 使用 Apache POI 解析 Excel 文件
            Workbook workbook = WorkbookFactory.create(is);
            
            // 获取第一个 Sheet
            Sheet sheet = workbook.getSheetAt(0);
            
            // 遍历所有行
            for (Row row : sheet) {
                // 跳过标题行(第一行)
                if (row.getRowNum() == 0) continue;
                
                // 读取城市名称(第一列)和 adcode(第二列)
                String chineseName = getCellString(row.getCell(0));
                String adcode = getCellString(row.getCell(1));
                
                // 验证数据有效性
                if (chineseName != null && !chineseName.isEmpty() && adcode != null && !adcode.isEmpty()) {
                    // 存入映射表
                    CITY_TO_ADCODE.put(chineseName, adcode);
                    
                    // 同时存入无"市"/"区"后缀的简称
                    // 例如: "济南市" -> "370100", "济南" -> "370100"
                    // 这样用户输入"济南"或"济南市"都能匹配
                    if (chineseName.endsWith("市")) {
                        CITY_TO_ADCODE.put(chineseName.substring(0, chineseName.length() - 1), adcode);
                    } else if (chineseName.endsWith("区")) {
                        CITY_TO_ADCODE.put(chineseName.substring(0, chineseName.length() - 1), adcode);
                    }
                }
            }
        }
        System.out.println("加载城市编码完成,共 " + CITY_TO_ADCODE.size() + " 条映射");
    }

    /**
     * 从 Excel 单元格中读取字符串
     * 
     * 技术细节:
     * - Excel 单元格可能是多种类型(数字、字符串、日期等)
     * - setCellType(CellType.STRING) 强制转换为字符串类型
     * - trim() 去除首尾空格
     */
    private static String getCellString(Cell cell) {
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    /**
     * 工具规范定义
     * 
     * ToolSpecification 是告诉 LLM "有哪些工具可用" 的描述
     * 
     * 组成部分:
     * 1. name: 工具名称,LLM 会在返回的 ToolExecutionRequest 中使用这个名称
     * 2. description: 工具功能描述,LLM 根据这个描述判断何时调用工具
     * 3. parameters: 工具参数的 JSON Schema 定义
     * 
     * JSON Schema 说明:
     * - addStringProperty("city", "城市名称..."): 定义一个字符串类型的参数
     * - required("city"): 标记 city 参数为必填
     * - LLM 会根据 Schema 生成符合格式的参数
     * 
     * 重要提示:
     * - description 要写清楚,LLM 根据这个判断是否调用工具
     * - 参数描述要详细,帮助 LLM 正确填充参数值
     */
    public ToolSpecification toolSpecification = ToolSpecification.builder()
            .name("getWeather")  // 工具名称
            .description("根据城市名称获取该城市的实时天气信息")  // 工具描述
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("city", "城市名称,例如:济南、北京、上海")  // 参数定义
                    .required("city")  // 必填参数
                    .build())
            .build();

    /**
     * 从高德地图 API 获取天气信息
     * 
     * @param cityName 城市名称(如"济南")
     * @return 天气描述字符串
     * 
     * API 文档: https://lbs.amap.com/api/webservice/guide/api/weatherinfo
     * 
     * 工作流程:
     * 1. 根据城市名称查找 adcode
     * 2. 构建 API 请求 URL
     * 3. 发送 HTTP GET 请求
     * 4. 解析 JSON 响应
     * 5. 格式化天气信息并返回
     */
    private String getWeatherFromAmap(String cityName) {
        // 1. 从映射表中获取 adcode
        String adcode = CITY_TO_ADCODE.get(cityName);
        
        // 如果直接匹配失败,尝试模糊匹配
        // 例如: 用户输入"济南市",映射表中只有"济南"
        if (adcode == null) {
            for (Map.Entry<String, String> entry : CITY_TO_ADCODE.entrySet()) {
                if (entry.getKey().contains(cityName) || cityName.contains(entry.getKey())) {
                    adcode = entry.getValue();
                    break;
                }
            }
        }
        
        // 如果还是找不到,返回错误信息
        if (adcode == null) {
            return "未找到城市编码:" + cityName;
        }

        // 2. 构建高德天气 API 请求 URL
        // 参数说明:
        // - key: API Key
        // - city: 城市 adcode
        // - extensions: base(实况天气) 或 all(预报天气)
        // - output: JSON 或 XML
        String url = "https://restapi.amap.com/v3/weather/weatherInfo?key=" + AMAP_KEY
                + "&city=" + adcode
                + "&extensions=base"
                + "&output=JSON";

        // 3. 使用 OkHttp 发送 HTTP 请求
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).get().build();
        
        try (Response response = client.newCall(request).execute()) {
            // 检查 HTTP 状态码
            if (!response.isSuccessful()) {
                return "天气服务请求失败,HTTP状态码:" + response.code();
            }
            
            // 4. 解析 JSON 响应
            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            
            // 检查 API 返回状态
            // status = "1" 表示成功, "0" 表示失败
            String status = json.get("status").getAsString();
            if (!"1".equals(status)) {
                return "天气接口返回错误:" + json.get("info").getAsString();
            }
            
            // 提取天气数据
            JsonObject live = json.getAsJsonArray("lives").get(0).getAsJsonObject();
            String province = live.get("province").getAsString();  // 省份
            String city = live.get("city").getAsString();  // 城市
            String weather = live.get("weather").getAsString();  // 天气状况
            String temperature = live.get("temperature").getAsString();  // 温度
            String windDirection = live.get("winddirection").getAsString();  // 风向
            String windPower = live.get("windpower").getAsString();  // 风力
            String humidity = live.get("humidity").getAsString();  // 湿度

            // 5. 格式化天气信息
            return String.format("%s%s,天气:%s,温度:%s℃,风向:%s,风力:%s级,湿度:%s%%",
                    province, city, weather, temperature, windDirection, windPower, humidity);
        } catch (Exception e) {
            return "获取天气时发生异常:" + e.getMessage();
        }
    }
    
    /**
     * 工具调用流式测试
     * 
     * 功能说明:
     * 演示完整的工具调用流程(流式输出版本)
     * 
     * 工作流程:
     * 1. 用户提问: "今天济南的天气怎么样,用华氏度显示"
     * 2. 第一轮请求: 发送用户消息 + 工具规范给 LLM
     * 3. LLM 判断: 需要调用 getWeather 工具
     * 4. LLM 返回: ToolExecutionRequest { name: "getWeather", arguments: { "city": "济南" } }
     * 5. 执行工具: 调用 getWeatherFromAmap("济南")
     * 6. 获取结果: "山东济南,天气:晴,温度:25℃..."
     * 7. 第二轮请求: 发送用户消息 + LLM 的工具调用请求 + 工具执行结果
     * 8. LLM 生成: 根据工具结果生成最终回答(流式输出)
     * 
     * 关键点:
     * - 需要两轮请求: 第一轮获取工具调用请求,第二轮获取最终回答
     * - 第一轮必须包含 toolSpecifications
     * - 第二轮不需要 toolSpecifications(避免 LLM 再次调用工具)
     */
    @Test
    void testWeatherToolStreaming() throws Exception {
        // 1. 创建流式聊天模型
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();

        // 2. 第一轮请求:用户询问天气
        // 构建 ChatRequest,包含:
        // - 用户消息: "今天济南的天气怎么样,用华氏度显示"
        // - 工具规范: toolSpecification(告诉 LLM 有 getWeather 工具可用)
        ChatRequest firstRequest = ChatRequest.builder()
                .messages(UserMessage.from("今天济南的天气怎么样,用华氏度显示"))
                .toolSpecifications(toolSpecification)  // 关键: 告诉 LLM 可用的工具
                .build();

        // 3. 异步等待第一轮完整响应
        // 使用 CompletableFuture 等待流式响应完成
        CompletableFuture<ChatResponse> firstResponseFuture = new CompletableFuture<>();
        chatModel.chat(firstRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // 第一轮不输出部分内容,因为可能只是工具调用请求
                // 如果 LLM 决定调用工具,这里不会有文本输出
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                // 流式响应完成,将完整响应传递给 Future
                firstResponseFuture.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                // 发生错误,将异常传递给 Future
                firstResponseFuture.completeExceptionally(error);
            }
        });

        // 阻塞等待第一轮响应完成
        ChatResponse firstResponse = firstResponseFuture.get();
        AiMessage aiMessage = firstResponse.aiMessage();

        // 4. 检查 LLM 是否请求调用工具
        if (!aiMessage.hasToolExecutionRequests()) {
            // 如果 LLM 没有请求调用工具,说明它直接回答了问题
            // 这种情况通常发生在:
            // - LLM 认为不需要工具就能回答
            // - 工具描述不够清晰,LLM 没有理解何时使用工具
            System.out.println("模型直接回答:" + aiMessage.text());
            return;
        }

        // 5. 处理工具调用请求
        System.out.println("模型请求调用工具,共 " + aiMessage.toolExecutionRequests().size() + " 个请求:");
        ToolExecutionRequest toolReq = aiMessage.toolExecutionRequests().get(0);
        System.out.println("工具名称:" + toolReq.name());  // 输出: getWeather
        System.out.println("参数:" + toolReq.arguments());  // 输出: {"city":"济南"}

        // 6. 执行真实天气查询
        // 解析 LLM 返回的参数 JSON
        JsonObject args = JsonParser.parseString(toolReq.arguments()).getAsJsonObject();
        String city = args.get("city").getAsString();  // 提取城市名称
        
        // 调用真实的天气查询函数
        String weatherResult = getWeatherFromAmap(city);
        
        // 将工具执行结果封装为 ToolExecutionResultMessage
        // 这个消息会在第二轮请求中发送给 LLM
        ToolExecutionResultMessage toolResultMsg = ToolExecutionResultMessage.from(toolReq, weatherResult);

        // 7. 第二轮请求:将工具结果发给模型,并流式输出最终回答
        // 构建 ChatRequest,包含:
        // - 原始用户消息
        // - LLM 的工具调用请求(aiMessage)
        // - 工具执行结果(toolResultMsg)
        ChatRequest secondRequest = ChatRequest.builder()
                .messages(
                        UserMessage.from("今天济南的天气怎么样,用华氏度显示"),
                        aiMessage,  // LLM 的工具调用请求
                        toolResultMsg  // 工具执行结果
                )
                .build();  // 注意: 不再需要 toolSpecifications

        // 8. 流式输出最终回答
        CompletableFuture<Void> finalOutputFuture = new CompletableFuture<>();
        chatModel.chat(secondRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // 实时流式输出 LLM 的回答
                // 例如: "根据查询结果,今天济南天气晴朗,温度为 77℉..."
                System.out.print(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                System.out.println(); // 换行
                finalOutputFuture.complete(null);
            }

            @Override
            public void onError(Throwable error) {
                finalOutputFuture.completeExceptionally(error);
            }
        });

        // 等待流式输出完成
        finalOutputFuture.get();
    }
}
```

### 工具调用的关键点

1. **两轮请求机制**:
   - 第一轮: 用户消息 + 工具规范 → LLM 返回工具调用请求
   - 第二轮: 用户消息 + 工具调用请求 + 工具结果 → LLM 返回最终回答

2. **ToolSpecification 的重要性**:
   - description 要清晰,LLM 根据这个判断何时调用工具
   - 参数描述要详细,帮助 LLM 正确填充参数

3. **错误处理**:
   - 工具执行可能失败(API 错误、网络问题等)
   - 应该将错误信息返回给 LLM,让它生成友好的错误提示

---

## RAG(检索增强生成)详解

### 什么是 RAG?

**RAG(Retrieval-Augmented Generation)** = 检索 + 生成

**核心思想**:
- LLM 的知识是有限的(训练数据截止日期、无法访问私有数据)
- RAG 通过检索外部知识库来增强 LLM 的回答能力
- 工作流程: 用户提问 → 检索相关文档 → 将文档作为上下文发送给 LLM → LLM 基于文档生成回答

**RAG vs 微调**:
- 微调: 修改模型参数,成本高,更新困难
- RAG: 不修改模型,只提供外部知识,成本低,易更新

### RAG 的核心组件

1. **Document(文档)**:
   - 原始知识的载体(PDF、Word、网页等)
   - 包含文本内容和元数据(文件名、作者、日期等)

2. **DocumentSplitter(文档分割器)**:
   - 将长文档切分为小块(Chunk)
   - 原因: LLM 的上下文窗口有限,无法处理整个文档
   - 策略: 按句子、段落、Token 数量等切分

3. **EmbeddingModel(嵌入模型)**:
   - 将文本转换为向量(数值数组)
   - 语义相似的文本,向量也相似
   - 例如: "猫" 和 "小猫" 的向量很接近

4. **EmbeddingStore(向量存储)**:
   - 存储文档向量
   - 支持相似度搜索(找到与查询最相似的文档)
   - 常见实现: 内存、Pinecone、Weaviate、Milvus

5. **ContentRetriever(内容检索器)**:
   - 根据用户查询检索相关文档
   - 配置: 返回数量(maxResults)、相似度阈值(minScore)

### RagChainTest.java 详细注释

由于篇幅限制,我将在主文档中继续添加 RAG 的详细注释。

---

## 流式输出详解

### 什么是流式输出?

**流式输出(Streaming)** 是指 LLM 逐字逐句地返回响应,而不是等待全部生成完毕后一次性返回。

**优势**:
- 用户体验更好(类似打字机效果)
- 降低首字延迟(TTFB - Time To First Byte)
- 适合长文本生成场景

**实现方式**:
- 使用 `StreamingChatModel` 而不是 `ChatModel`
- 实现 `StreamingChatResponseHandler` 接口
- 在 `onPartialResponse()` 中处理每个 Token

---

## 结构化输出详解

### 什么是结构化输出?

**结构化输出** 是指让 LLM 返回符合特定格式的数据(JSON、Java 对象等),而不是自由文本。

**应用场景**:
- 数据提取(从文本中提取姓名、地址等)
- 分类任务(情感分析、主题分类等)
- 表单填充(自动填写结构化表单)

**实现方式**:
1. 定义 Java 类(POJO)
2. 使用 `@Description` 注解描述字段含义
3. LLM 自动将响应解析为 Java 对象

---

## 最佳实践

### 1. 记忆管理
- 生产环境使用持久化存储(MySQL、Redis)
- 合理设置窗口大小,避免 Token 超限
- 定期清理过期会话

### 2. 工具调用
- 工具描述要清晰准确
- 处理工具执行失败的情况
- 避免工具调用死循环

### 3. RAG 优化
- 文档切分大小要合理(200-500 Token)
- 使用高质量的 Embedding 模型
- 调整相似度阈值,平衡召回率和准确率

### 4. 错误处理
- 捕获 API 调用异常
- 提供友好的错误提示
- 实现重试机制

### 5. 性能优化
- 使用流式输出提升用户体验
- 缓存常见查询结果
- 异步处理耗时操作

---

## 总结

LangChain4j 提供了强大的 LLM 集成能力:
- **AiServices**: 声明式 AI 集成
- **ChatMemory**: 多轮对话记忆管理
- **Tool Calling**: 让 AI 调用外部函数
- **RAG**: 结合外部知识库增强回答
- **Streaming**: 流式输出提升体验
- **Structured Output**: 结构化数据提取

通过合理使用这些功能,可以构建强大的 AI 应用。
