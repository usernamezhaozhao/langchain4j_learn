## LangChain4j 学习笔记

---

## 目录
1. [基础对话](#基础对话)
2. [ChatResponse 和 AiMessage 的区别](#chatresponse和aimessage有区别吗)
3. [模型分类](#分类)
4. [模型参数](#模型参数)
5. [对话记忆](#对话记忆)
6. [AI 服务](#ai-服务)
7. [工具调用](#工具调用)
8. [RAG 检索增强生成](#rag-检索增强生成)

---

## 基础对话

### 非流式对话

**功能说明**: 一次性返回完整的 AI 响应，适合不需要实时反馈的场景。

**使用场景**:
- 简单的问答系统
- 批量处理任务
- 不需要实时反馈的场景

**代码示例**:
```java
// 创建非流式聊天模型
OpenAiChatModel chatModel = OpenAiChatModel.builder()
        .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")  // API 密钥
        .modelName("deepseek-ai/DeepSeek-V3.2")  // 模型名称
        .baseUrl("https://api.siliconflow.cn/v1")  // API 基础 URL
        .build();

// 发送消息并获取完整响应
String chat = chatModel.chat("你是谁呀");
System.out.println(chat);  // 等待完整响应后一次性输出
```

**关键点**:
- `chat()` 方法会阻塞，直到 LLM 生成完整响应
- 返回值是完整的字符串
- 适合短文本生成

---

### 流式对话

**功能说明**: 逐字逐句返回 AI 响应，提供打字机效果，提升用户体验。

**使用场景**:
- 需要实时反馈的聊天界面
- 长文本生成（文章、代码等）
- 提升用户体验的场景

**代码示例**:
```java
// 创建流式聊天模型
StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
        .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
        .modelName("deepseek-ai/DeepSeek-V3.2")
        .baseUrl("https://api.siliconflow.cn/v1")
        .build();

// 使用 CompletableFuture 等待流式响应完成
CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();

// 发送消息并处理流式响应
chatModel.chat("你是谁呀", new StreamingChatResponseHandler() {
    @Override
    public void onPartialResponse(String partialResponse) {
        // 每收到一个 Token 就调用一次，实现打字机效果
        System.out.print(partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        // 流式响应完成时调用
        futureChatResponse.complete(completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        // 发生错误时调用
        futureChatResponse.completeExceptionally(error);
    }
});

// 阻塞等待流式响应完成
futureChatResponse.join();
```

**关键点**:
- `onPartialResponse()` 会被多次调用，每次返回一小段文本
- 需要实现 `StreamingChatResponseHandler` 接口
- 使用 `CompletableFuture` 等待异步操作完成

**非流式 vs 流式对比**:

| 特性 | 非流式 (ChatModel) | 流式 (StreamingChatModel) |
|------|-------------------|--------------------------|
| 响应方式 | 一次性返回完整响应 | 逐字逐句返回 |
| 用户体验 | 需要等待，可能感觉卡顿 | 实时反馈，体验更好 |
| 首字延迟 | 较高（需等待全部生成） | 较低（立即开始输出） |
| 适用场景 | 短文本、批量处理 | 长文本、聊天界面 |
| 实现复杂度 | 简单 | 稍复杂（需处理异步） |

---


##### ChatResponse和AiMessage有区别吗

**有区别**，而且区别很明显。简单说：**`ChatResponse` 是一个”信封”，`AiMessage` 是信封里的”信件内容”**。

###### 详细对比

|                            | `ChatResponse`                                               | `AiMessage`                                                  |
| :------------------------- | :----------------------------------------------------------- | :----------------------------------------------------------- |
| **是什么**                 | 模型的**完整响应结果**，包含元数据                           | **仅代表 AI 生成的消息内容**（角色为 `assistant`）           |
| **包含什么**               | • `aiMessage()`：AI 回复的消息对象 <br>• `tokenUsage()`：本次请求消耗的 token 数（输入+输出） <br>• `finishReason()`：结束原因（如 `STOP`、`LENGTH`） <br>• 其他元数据 | • `text()`：回复文本 <br>• `toolExecutionRequests()`：工具调用请求（如果有） <br>• 继承自 `ChatMessage` 的通用属性 |
| **从 `ChatResponse` 获取** | 直接返回                                                     | 调用 `chatResponse.aiMessage()`                              |
| **典型用途**               | 需要监控用量、调试、判断是否因 token 限制截断等              | 只需要拿到 AI 说的内容                                       |

**代码示例**:
```java
// 获取完整响应
ChatResponse response = chatModel.chat(“你好”);

// 从 ChatResponse 中提取 AiMessage
AiMessage aiMessage = response.aiMessage();
String text = aiMessage.text();  // 获取 AI 回复的文本

// 获取元数据
TokenUsage tokenUsage = response.tokenUsage();  // Token 使用情况
System.out.println(“输入 Token: “ + tokenUsage.inputTokenCount());
System.out.println(“输出 Token: “ + tokenUsage.outputTokenCount());
System.out.println(“总 Token: “ + tokenUsage.totalTokenCount());

FinishReason finishReason = response.finishReason();  // 结束原因
System.out.println(“结束原因: “ + finishReason);  // STOP(正常结束) 或 LENGTH(达到长度限制)
```

**使用建议**:
- 如果只需要 AI 的回复文本，使用 `aiMessage.text()`
- 如果需要监控 Token 使用量，使用 `response.tokenUsage()`
- 如果需要判断是否因长度限制截断，检查 `response.finishReason()`

---

##### 分类

LangChain4j 支持多种类型的模型，每种模型有不同的用途：

| 模型类型 | 功能说明 | 输入 | 输出 | 使用场景 |
|---------|---------|------|------|---------|
| **ChatModel** | 对话模型 | 多个 `ChatMessage` | 单个 `AiMessage` | 聊天机器人、问答系统 |
| **EmbeddingModel** | 嵌入模型 | 文本 | `Embedding`（向量） | RAG、语义搜索、文本相似度 |
| **ImageModel** | 图像模型 | 文本提示词 | `Image` | AI 绘画、图像编辑 |
| **ModerationModel** | 审核模型 | 文本 | 审核结果 | 内容安全、敏感词过滤 |
| **ScoringModel** | 评分模型 | 查询 + 多段文本 | 相关性分数 | 搜索排序、文档重排序 |

**ChatModel 详解**:
- 接收多个 `ChatMessage` 作为输入（包括用户消息、系统消息、AI 消息等）
- 返回一个 `AiMessage` 作为输出
- 支持多轮对话（通过传入历史消息）

**EmbeddingModel 详解**:
- 将文本转换为高维向量（通常是 768 或 1536 维）
- 语义相似的文本，向量也相似
- 用于 RAG 系统中的文档检索

**代码示例**:
```java
// ChatModel 示例
ChatModel chatModel = OpenAiChatModel.builder()
    .apiKey(“your-api-key”)
    .modelName(“gpt-4”)
    .build();
String response = chatModel.chat(“你好”);

// EmbeddingModel 示例
EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
    .apiKey(“your-api-key”)
    .modelName(“text-embedding-ada-002”)
    .build();
Embedding embedding = embeddingModel.embed(“你好”).content();
```

---



##### 模型参数

根据你选择的模型和提供商，你可以调整许多参数，这些参数将决定：

- **模型的输出**：生成内容（文本、图片）的创造性或确定性水平、生成内容的数量等。
- **连接性**：基础 URL、授权密钥、超时、重试、日志记录等。

通常，你可以在模型提供商的网站上找到所有参数及其含义。
例如，OpenAI API 的参数可以在 [官方文档](https://platform.openai.com/docs/api-reference/chat)（最新版本）中找到，包括以下选项：

| 参数               | 描述                                                         | 类型      | 默认值 | 取值范围 |
| ------------------ | ------------------------------------------------------------ | --------- | ------ | -------- |
| `modelName`        | 要使用的模型名称（例如 gpt-4o、gpt-4o-mini 等）。            | `String`  | 必填 | - |
| `temperature`      | 采样温度，取值范围 0 到 2。较高的值（如 0.8）会使输出更随机，较低的值（如 0.2）会使输出更专注、更确定性。 | `Double`  | 1.0 | 0.0 - 2.0 |
| `maxTokens`        | 在聊天补全中可以生成的最大 token 数。                        | `Integer` | 无限制 | 1 - 模型上限 |
| `frequencyPenalty` | 范围 -2.0 到 2.0。正值会根据 token 在文本中已出现的频率来惩罚新 token，从而降低模型逐字重复相同行的可能性。 | `Double`  | 0.0 | -2.0 - 2.0 |
| `presencePenalty`  | 范围 -2.0 到 2.0。正值会根据 token 是否已出现在文本中来惩罚新 token，增加模型谈论新主题的可能性。 | `Double`  | 0.0 | -2.0 - 2.0 |
| `topP`             | 核采样参数，模型考虑概率质量为 topP 的 token。例如 0.1 表示只考虑概率最高的 10% 的 token。 | `Double`  | 1.0 | 0.0 - 1.0 |
| `timeout`          | 请求超时时间。 | `Duration` | 60秒 | - |
| `logRequests`      | 是否记录请求日志（用于调试）。 | `Boolean` | false | true/false |
| `logResponses`     | 是否记录响应日志（用于调试）。 | `Boolean` | false | true/false |

**参数详解**:

1. **temperature（温度）**:
   - 控制输出的随机性和创造性
   - 0.0: 完全确定性，每次输出相同（适合翻译、代码生成）
   - 1.0: 平衡创造性和一致性（默认值）
   - 2.0: 高度随机，输出更有创意（适合创意写作、头脑风暴）

2. **maxTokens（最大 Token 数）**:
   - 限制生成的最大长度
   - 防止生成过长的响应
   - 注意：输入 + 输出的总 Token 数不能超过模型的上下文窗口

3. **frequencyPenalty（频率惩罚）**:
   - 减少重复内容
   - 正值：降低已出现 token 的概率
   - 负值：增加已出现 token 的概率（不推荐）

4. **presencePenalty（存在惩罚）**:
   - 鼓励模型谈论新主题
   - 正值：降低已出现过的 token 的概率
   - 与 frequencyPenalty 的区别：不考虑出现次数，只考虑是否出现过

5. **topP（核采样）**:
   - 与 temperature 类似，控制输出的多样性
   - 0.1: 只考虑概率最高的 10% 的 token（输出更确定）
   - 1.0: 考虑所有 token（输出更多样）
   - 建议：temperature 和 topP 只调整一个，不要同时调整

**配置示例**:
```java
OpenAiChatModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .temperature(0.3)  // 降低随机性，输出更确定
        .maxTokens(500)  // 限制最大输出长度
        .frequencyPenalty(0.5)  // 减少重复内容
        .timeout(ofSeconds(60))  // 设置超时时间
        .logRequests(true)  // 开启请求日志（调试用）
        .logResponses(true)  // 开启响应日志（调试用）
        .build();
```

**使用建议**:
- **代码生成、翻译**: temperature=0.0, topP=0.1（追求准确性）
- **聊天机器人**: temperature=0.7, topP=0.9（平衡创造性和一致性）
- **创意写作**: temperature=1.2, topP=1.0（追求创造性）
- **减少重复**: frequencyPenalty=0.5-1.0
- **探索新主题**: presencePenalty=0.5-1.0

---

## 对话记忆

### 为什么需要对话记忆？

LLM 本身是**无状态**的，每次请求都是独立的，不会记住之前的对话。如果不使用记忆机制，会出现以下问题：

```java
// 没有记忆的对话
chatModel.chat("我叫小明");  // AI: "你好，小明！"
chatModel.chat("我叫什么？");  // AI: "抱歉，我不知道你的名字。" ❌
```

通过 `ChatMemory`，可以让 AI 记住之前的对话：

```java
// 有记忆的对话
ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
memory.add(UserMessage.from("我叫小明"), aiMessage1);
// 第二轮对话时，将历史消息一起发送
List<ChatMessage> history = memory.messages();
history.add(UserMessage.from("我叫什么？"));
chatModel.chat(history);  // AI: "你叫小明。" ✅
```

### 记忆类型对比

| 记忆类型 | 限制方式 | 优点 | 缺点 | 适用场景 |
|---------|---------|------|------|---------|
| **MessageWindowChatMemory** | 消息数量（如最多 10 条） | 简单直观，易于理解 | 无法精确控制 Token 数量 | 消息长度相对均匀的场景 |
| **TokenWindowChatMemory** | Token 数量（如最多 1000 个） | 精确控制上下文长度 | 需要 Token 计数器，稍复杂 | 需要严格控制成本的场景 |

### MessageWindowChatMemory 示例

**功能说明**: 保留最近 N 条消息，超过限制时自动删除最早的消息（FIFO 队列）。

**代码示例**:
```java
// 创建只保留最近 3 条消息的记忆
MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(3);

memory.add(UserMessage.from("1"));  // 第 1 条
memory.add(UserMessage.from("2"));  // 第 2 条
memory.add(UserMessage.from("3"));  // 第 3 条
memory.add(UserMessage.from("4"));  // 第 4 条，此时第 1 条会被删除

System.out.println("当前消息数量：" + memory.messages().size());  // 输出: 3
memory.messages().forEach(System.out::println);  // 输出: 2, 3, 4
```

### TokenWindowChatMemory 示例

**功能说明**: 保留最近 N 个 Token 的消息，超过限制时自动删除最早的消息。

**什么是 Token？**
- Token 是 LLM 处理文本的基本单位
- 英文: 1 个单词 ≈ 1-2 个 Token
- 中文: 1 个汉字 ≈ 1-2 个 Token
- 例如: "你好" ≈ 2 个 Token, "Hello" ≈ 1 个 Token

**代码示例**:
```java
// 创建只保留最近 100 个 Token 的记忆
TokenWindowChatMemory memory = TokenWindowChatMemory.builder()
        .maxTokens(100, new OpenAiTokenCountEstimator("gpt-3.5-turbo"))
        .build();

memory.add(UserMessage.from("我是七七"));  // 假设占用 5 个 Token
memory.add(UserMessage.from("你还记得我是谁吗"));  // 假设占用 8 个 Token

// 当总 Token 数超过 100 时，最早的消息会被删除
```

### 持久化记忆

**问题**: 默认的记忆存储在内存中，应用重启后会丢失。

**解决方案**: 实现 `ChatMemoryStore` 接口，将记忆持久化到数据库。

**代码示例**:
```java
// 自定义持久化存储（实际应用中应该用数据库）
class PersistentChatMemoryStore implements ChatMemoryStore {
    private final Map<Object, List<ChatMessage>> store = new ConcurrentHashMap<>();
    
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return store.getOrDefault(memoryId, List.of());
    }
    
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        store.put(memoryId, messages);
    }
    
    @Override
    public void deleteMessages(Object memoryId) {
        store.remove(memoryId);
    }
}

// 使用持久化存储
ChatMemory memory = MessageWindowChatMemory.builder()
        .chatMemoryStore(new PersistentChatMemoryStore())
        .maxMessages(10)
        .build();
```

**实际应用建议**:
- **MySQL**: 创建 `chat_messages` 表，存储 `session_id`, `role`, `content`, `timestamp`
- **Redis**: 使用 List 数据结构，key 为 `chat:session:{id}`
- **MongoDB**: 每个会话一个文档，包含消息数组

---

## AI 服务

### 什么是 AiServices？

`AiServices` 是 LangChain4j 提供的高级抽象，通过**接口定义**的方式简化 AI 集成，类似于 Spring Data JPA。

**优势**:
- **声明式编程**: 只需定义接口，框架自动生成实现
- **自动记忆管理**: 框架自动处理对话历史的存储和检索
- **多用户支持**: 通过 `@MemoryId` 轻松实现多用户隔离
- **类型安全**: 编译时检查，减少运行时错误

### 基础用法

**代码示例**:
```java
// 1. 定义 AI 助手接口
interface Assistant {
    String chat(String userMessage);
}

// 2. 创建 AI 服务实例
ChatModel chatModel = OpenAiChatModel.builder()
        .apiKey("your-api-key")
        .modelName("gpt-4")
        .build();

Assistant assistant = AiServices.create(Assistant.class, chatModel);

// 3. 使用 AI 助手
String response = assistant.chat("你好");
System.out.println(response);
```

### 多用户记忆隔离

**功能说明**: 使用 `@MemoryId` 注解实现多用户的记忆隔离，每个用户有独立的对话历史。

**代码示例**:
```java
// 1. 定义支持多用户的接口
interface MultiUserAssistant {
    String chat(@MemoryId Long userId, @UserMessage String message);
}

// 2. 创建 AI 服务（配置记忆提供器）
MultiUserAssistant assistant = AiServices.builder(MultiUserAssistant.class)
        .chatModel(chatModel)
        .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
        .build();

// 3. 用户 1 的对话
assistant.chat(1L, "我叫张三");
assistant.chat(1L, "我是谁？");  // AI: "你叫张三"

// 4. 用户 2 的对话（完全独立）
assistant.chat(2L, "我叫李四");
assistant.chat(2L, "我是谁？");  // AI: "你叫李四"
```

**工作原理**:
- 框架根据 `userId` 查找或创建对应的 `ChatMemory`
- 不同 `userId` 的对话记忆完全隔离
- 框架自动管理多个 `ChatMemory` 实例

### 流式输出

**代码示例**:
```java
// 1. 定义流式接口
interface TokenStreamAssistant {
    TokenStream chat(String userMessage);
}

// 2. 创建流式 AI 服务
StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
        .apiKey("your-api-key")
        .modelName("gpt-4")
        .build();

TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
        .streamingChatModel(streamingModel)
        .build();

// 3. 使用流式输出
CompletableFuture<ChatResponse> future = new CompletableFuture<>();
TokenStream stream = assistant.chat("写一篇文章");

stream.onPartialResponse(System.out::print)  // 实时输出
      .onCompleteResponse(future::complete)
      .onError(future::completeExceptionally)
      .start();

future.join();  // 等待完成
```

### 系统消息和用户消息

**代码示例**:
```java
interface CustomAssistant {
    // 使用 @SystemMessage 定义系统角色
    @SystemMessage("你是一个只会叫'咕咕嘎嘎'的臭企鹅")
    String chat(String userMessage);
    
    // 使用 @UserMessage 定义消息模板
    @UserMessage("请你说下 Java 的{{it}}")
    String explain(String topic);
}

// 使用
assistant.chat("你是谁");  // AI: "咕咕嘎嘎"
assistant.explain("多态");  // 实际发送: "请你说下 Java 的多态"
```

---

## 工具调用

### 什么是工具调用？

**工具调用(Tool Calling/Function Calling)** 让 LLM 能够调用外部函数来获取实时数据或执行操作。

**为什么需要工具调用？**
- LLM 的知识是静态的，无法获取实时数据（天气、股票、数据库查询等）
- LLM 无法执行操作（发送邮件、创建订单等）

**工作流程**:
```
1. 用户: "今天济南的天气怎么样？"
2. LLM 分析: 需要调用 getWeather 工具
3. LLM 返回: 工具调用请求 { "name": "getWeather", "arguments": { "city": "济南" } }
4. 应用执行: 调用真实的天气 API，获取结果 "济南，晴，25℃"
5. 应用发送: 将工具结果返回给 LLM
6. LLM 生成: "今天济南天气晴朗，温度 25℃，适合出行。"
```

### 方式一：手动定义工具规范

**代码示例**:
```java
// 1. 定义工具规范
ToolSpecification toolSpec = ToolSpecification.builder()
        .name("getWeather")
        .description("根据城市名称获取该城市的实时天气信息")
        .parameters(JsonObjectSchema.builder()
                .addStringProperty("city", "城市名称，例如：济南、北京、上海")
                .required("city")
                .build())
        .build();

// 2. 第一轮请求：发送用户消息 + 工具规范
ChatRequest firstRequest = ChatRequest.builder()
        .messages(UserMessage.from("今天济南的天气怎么样"))
        .toolSpecifications(toolSpec)
        .build();

ChatResponse firstResponse = chatModel.chat(firstRequest);
AiMessage aiMessage = firstResponse.aiMessage();

// 3. 检查是否需要调用工具
if (aiMessage.hasToolExecutionRequests()) {
    ToolExecutionRequest toolReq = aiMessage.toolExecutionRequests().get(0);
    
    // 4. 执行工具函数
    String result = getWeatherFromAPI(toolReq.arguments());
    
    // 5. 第二轮请求：发送工具结果
    ChatRequest secondRequest = ChatRequest.builder()
            .messages(
                    UserMessage.from("今天济南的天气怎么样"),
                    aiMessage,
                    ToolExecutionResultMessage.from(toolReq, result)
            )
            .build();
    
    ChatResponse finalResponse = chatModel.chat(secondRequest);
    System.out.println(finalResponse.aiMessage().text());
}
```

### 方式二：使用 @Tool 注解

**代码示例**:
```java
// 1. 定义工具类
class WeatherTools {
    @Tool("Returns the weather forecast for a given city")
    public String getWeather(
            @P("The city for which the weather forecast should be returned") String city,
            @P("The temperature unit: CELSIUS or FAHRENHEIT") TemperatureUnit unit
    ) {
        // 调用真实的天气 API
        return callWeatherAPI(city, unit);
    }
}

// 2. 使用 AiServices 自动处理工具调用
interface WeatherAssistant {
    String chat(String userMessage);
}

WeatherAssistant assistant = AiServices.builder(WeatherAssistant.class)
        .chatModel(chatModel)
        .tools(new WeatherTools())  // 注册工具
        .build();

// 3. 使用（框架自动处理工具调用）
String response = assistant.chat("今天济南的天气怎么样，用华氏度显示");
System.out.println(response);
```

**@Tool 注解的优势**:
- 自动生成 `ToolSpecification`
- 自动处理工具调用流程
- 代码更简洁，易于维护

**工具方法的限制**:
- 可以是静态的或非静态的
- 可以是任意可见性（public、private 等）
- 参数类型支持：基本类型、对象类型、自定义 POJO、enum、List、Set、Map
- 默认所有参数都是必填，使用 `@P(required = false)` 设为可选

---

## RAG 检索增强生成

### 什么是 RAG？

**RAG(Retrieval-Augmented Generation)** = 检索 + 生成

**核心思想**:
- LLM 的知识是有限的（训练数据截止日期、无法访问私有数据）
- RAG 通过检索外部知识库来增强 LLM 的回答能力
- 工作流程: 用户提问 → 检索相关文档 → 将文档作为上下文发送给 LLM → LLM 基于文档生成回答

**RAG vs 微调**:
| 特性 | RAG | 微调 |
|------|-----|------|
| 成本 | 低（只需存储文档） | 高（需要训练模型） |
| 更新 | 容易（更新文档即可） | 困难（需要重新训练） |
| 准确性 | 高（基于真实文档） | 中（依赖训练数据质量） |
| 适用场景 | 知识库问答、文档检索 | 特定领域的语言风格 |

### RAG 核心组件

1. **Document（文档）**: 原始知识的载体（PDF、Word、网页等）
2. **DocumentSplitter（文档分割器）**: 将长文档切分为小块（Chunk）
3. **EmbeddingModel（嵌入模型）**: 将文本转换为向量
4. **EmbeddingStore（向量存储）**: 存储文档向量，支持相似度搜索
5. **ContentRetriever（内容检索器）**: 根据用户查询检索相关文档

### RAG 完整示例

**代码示例**:
```java
// 1. 加载文档
String pdfPath = "D:/桌面/文档.pdf";
DocumentParser parser = new ApacheTikaDocumentParser();
List<Document> documents = List.of(
        FileSystemDocumentLoader.loadDocument(Paths.get(pdfPath), parser)
);

// 2. 文档分割
List<TextSegment> segments = DocumentSplitters
        .recursive(200, 50)  // 每块最大 200 字符，重叠 50 字符
        .split(documents.get(0));

// 3. 创建向量存储
InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

// 4. 将文档注入向量数据库
EmbeddingStoreIngestor.ingest(documents, embeddingStore);

// 5. 创建内容检索器
ContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
        .embeddingStore(embeddingStore)
        .maxResults(3)  // 最多返回 3 条相关文档
        .minScore(0.5)  // 相似度阈值
        .build();

// 6. 创建 RAG 助手
StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
        .apiKey("your-api-key")
        .modelName("gpt-4")
        .build();

interface RagAssistant {
    TokenStream chat(@MemoryId Long userId, @UserMessage String msg);
}

RagAssistant assistant = AiServices.builder(RagAssistant.class)
        .streamingChatModel(chatModel)
        .contentRetriever(retriever)  // 配置检索器
        .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
        .build();

// 7. 使用 RAG 助手
CompletableFuture<ChatResponse> future = new CompletableFuture<>();
TokenStream stream = assistant.chat(1L, "这个文档在说什么");

stream.onPartialResponse(System.out::print)
      .onCompleteResponse(future::complete)
      .onError(future::completeExceptionally)
      .start();

future.join();
```

### 文档元数据

**功能说明**: 文档可以包含元数据（文件名、作者、日期等），用于过滤和排序。

**代码示例**:
```java
// 加载文档后查看元数据
Document doc = documents.get(0);
Metadata metadata = doc.metadata();

System.out.println("文件名: " + metadata.getString("file_name"));
System.out.println("文件大小: " + metadata.getLong("file_size"));
System.out.println("绝对路径: " + metadata.getString("absolute_path"));
```

**注意**: LangChain4j 默认只填充部分元数据（`file_name`），其他元数据需要手动添加。

---

## 总结

本文档涵盖了 LangChain4j 的核心功能：

1. **基础对话**: 非流式 vs 流式
2. **ChatResponse vs AiMessage**: 理解响应结构
3. **模型分类**: ChatModel、EmbeddingModel 等
4. **模型参数**: temperature、maxTokens 等
5. **对话记忆**: MessageWindowChatMemory、TokenWindowChatMemory
6. **AI 服务**: AiServices 的声明式编程
7. **工具调用**: 让 AI 调用外部函数
8. **RAG**: 结合外部知识库增强回答

通过合理使用这些功能，可以构建强大的 AI 应用。



