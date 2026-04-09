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



