## langchain

##### 非流式

```java
OpenAiChatModel chatModel = OpenAiChatModel.builder()
        .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
        .modelName("deepseek-ai/DeepSeek-V3.2")
        .baseUrl("https://api.siliconflow.cn/v1")
        .build();
String chat = chatModel.chat("你是谁呀");
System.out.println(chat);
```

##### 流式

```java
StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
        .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
        .modelName("deepseek-ai/DeepSeek-V3.2")
        .baseUrl("https://api.siliconflow.cn/v1")
        .build();
CompletableFuture<ChatResponse> futureChatResponse = new CompletableFuture<>();
chatModel.chat("你是谁呀", new StreamingChatResponseHandler() {
    @Override
    public void onPartialResponse(String partialResponse) {
        System.out.print(partialResponse);
    }

    @Override
    public void onCompleteResponse(ChatResponse completeResponse) {
        futureChatResponse.complete(completeResponse);
    }

    @Override
    public void onError(Throwable error) {
        futureChatResponse.completeExceptionally(error);
    }
});

futureChatResponse.join();
```



##### ChatResponse和AiMessage有区别吗

**有区别**，而且区别很明显。简单说：**`ChatResponse` 是一个“信封”，`AiMessage` 是信封里的“信件内容”**。

###### 详细对比

|                            | `ChatResponse`                                               | `AiMessage`                                                  |
| :------------------------- | :----------------------------------------------------------- | :----------------------------------------------------------- |
| **是什么**                 | 模型的**完整响应结果**，包含元数据                           | **仅代表 AI 生成的消息内容**（角色为 `assistant`）           |
| **包含什么**               | • `aiMessage()`：AI 回复的消息对象 • `tokenUsage()`：本次请求消耗的 token 数（输入+输出） • `finishReason()`：结束原因（如 `STOP`、`LENGTH`） • 其他元数据 | • `text()`：回复文本 • `toolExecutionRequests()`：工具调用请求（如果有） • 继承自 `ChatMessage` 的通用属性 |
| **从 `ChatResponse` 获取** | 直接返回                                                     | 调用 `chatResponse.aiMessage()`                              |
| **典型用途**               | 需要监控用量、调试、判断是否因 token 限制截断等              | 只需要拿到 AI 说的内容                                       |

##### 分类

- `ChatModel`。它们接收多个 `ChatMessage` 作为输入，并返回一个单一的 `AiMessage` 作为输出。

- `EmbeddingModel` —— 该模型可以将文本转换为 `Embedding`。
- `ImageModel` —— 该模型可以生成和编辑 `Image`。
- `ModerationModel` —— 该模型可以检查文本是否包含有害内容。
- `ScoringModel` —— 该模型可以针对查询对多段文本进行打分（或排序）



##### 模型参数

根据你选择的模型和提供商，你可以调整许多参数，这些参数将决定：

- **模型的输出**：生成内容（文本、图片）的创造性或确定性水平、生成内容的数量等。
- **连接性**：基础 URL、授权密钥、超时、重试、日志记录等。

通常，你可以在模型提供商的网站上找到所有参数及其含义。
例如，OpenAI API 的参数可以在 [官方文档](https://platform.openai.com/docs/api-reference/chat)（最新版本）中找到，包括以下选项：

| 参数               | 描述                                                         | 类型      |
| ------------------ | ------------------------------------------------------------ | --------- |
| `modelName`        | 要使用的模型名称（例如 gpt-4o、gpt-4o-mini 等）。            | `String`  |
| `temperature`      | 采样温度，取值范围 0 到 2。较高的值（如 0.8）会使输出更随机，较低的值（如 0.2）会使输出更专注、更确定性。 | `Double`  |
| `maxTokens`        | 在聊天补全中可以生成的最大 token 数。                        | `Integer` |
| `frequencyPenalty` | 范围 -2.0 到 2.0。正值会根据 token 在文本中已出现的频率来惩罚新 token，从而降低模型逐字重复相同行的可能性。 | `Double`  |

```
OpenAiChatModel model = OpenAiChatModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("gpt-4o-mini")
        .temperature(0.3)
        .timeout(ofSeconds(60))
        .logRequests(true)
        .logResponses(true)
        .build();
```

