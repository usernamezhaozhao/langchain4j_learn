package com.qi.langchain4j_learn_4_9;

import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static dev.langchain4j.internal.Utils.readBytes;

@SpringBootTest
class Langchain4jLearn49ApplicationTests {

    @Test
    void test1() {
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
    }

    @Test
    void test2() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        String chat = chatModel.chat("你是谁呀");
        System.out.println(chat);
    }
    //标准写法，仅2s便可回复
    @Test
    void test3() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        // 构造用户消息
        UserMessage userMessage = UserMessage.from("用一句话介绍Java");
        // 调用
        AiMessage aiMessage = chatModel.chat(userMessage).aiMessage();
        // 输出
        System.out.println(aiMessage.text());
    }
    //多轮对话
    @Test
    void test4() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        // 构造用户消息1
        UserMessage userMessage1 = UserMessage.from("讲讲Java的多态");
        // 调用
        AiMessage reply1 = chatModel.chat(userMessage1).aiMessage();
        // 输出
        System.out.println(reply1.text());
        // 构造用户消息2
        UserMessage userMessage2 = UserMessage.from("你刚刚说的重复一遍");
        // 构建历史记忆
        List<ChatMessage> history = List.of(userMessage1, reply1, userMessage2);
        AiMessage reply2 = chatModel.chat(history).aiMessage();
        // 输出
        System.out.println(reply2.text());
        // 构造用户消息3
        UserMessage userMessage3 = UserMessage.from("把你之前的输出再次输出一遍，附加我的问题");
        // 构建历史记忆
        history = List.of(userMessage1, reply1, userMessage2 , reply2 , userMessage3);
        AiMessage reply3 = chatModel.chat(history).aiMessage();
        // 输出
        System.out.println(reply3.text());
    }
    //角色设定
    @Test
    void test5() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        SystemMessage systemMessage = SystemMessage.from("你必须只输出咕咕嘎嘎");
        UserMessage userMessage = UserMessage.from("你是谁");
        AiMessage aiMessage = chatModel.chat(systemMessage,userMessage).aiMessage();
        System.out.println(aiMessage.text());
    }
    //base64多模态
    @Test
    void test6() throws IOException {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("zai-org/GLM-4.6V")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        byte[] imageBytes = Files.readAllBytes(Paths.get("D:/img2.jpg"));
        String base64Data = Base64.getEncoder().encodeToString(imageBytes);
        ImageContent imageContent = ImageContent.from(base64Data, "image/jpg");
        UserMessage userMessage = UserMessage.from(
                TextContent.from("描述这个图片"),
                imageContent
        );
        AiMessage aiMessage = chatModel.chat(userMessage).aiMessage();
        System.out.println(aiMessage.text());
    }
    //多模态
    @Test
    void test7() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("zai-org/GLM-4.6V")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        UserMessage userMessage = UserMessage.from(
                TextContent.from("描述这个图片"),
                ImageContent.from("https://agentpng.oss-cn-beijing.aliyuncs.com/agent/2026/04/08/1775640726038_pjolvc.jpg")
        );
        AiMessage aiMessage = chatModel.chat(userMessage).aiMessage();
        System.out.println(aiMessage.text());
    }
    //chatRequest
    @Test
    void test8() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("你是谁"))
                .build();
        System.out.println(chatModel.chat(chatRequest));
        System.out.println(chatModel.chat(chatRequest).aiMessage().text());
    }
    //finishReason
    @Test
    void test9() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("你是谁"))
                .build();
        ChatResponse response = chatModel.chat(chatRequest);
        System.out.println(response.finishReason().name());
        System.out.println(chatModel.chat(chatRequest).aiMessage().text());
    }
    //tokenUsage
    @Test
    void test10() {
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("你是谁"))
                .build();
        ChatResponse response = chatModel.chat(chatRequest);
        System.out.println(response.tokenUsage());
        System.out.println(chatModel.chat(chatRequest).aiMessage().text());
    }
    //声音
    //{"code":20029,"message":"Only text and image_url are supported.","data":null}好像平台不支持
    @Test
    void test11() throws IOException {
        // 1. 构建模型 (重点关注模型名称)
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct") // 请务必使用支持音频的模型
                .baseUrl("https://api.siliconflow.cn/v1")
                .logRequests(true)  // 强烈建议开启，用于查看请求格式
                .logResponses(true) // 用于调试
                .build();
        // 2. 读取音频文件并转换为 Base64
        byte[] audioBytes = Files.readAllBytes(Paths.get("D:/temp.mp3")); // 确保文件路径正确
        String base64Audio = Base64.getEncoder().encodeToString(audioBytes);
        // 3. 创建 AudioContent，必须指定 MIME 类型
        // 确保 mimeType 与你文件的实际格式匹配
        AudioContent audioContent = AudioContent.from(base64Audio, "audio/mpeg");
        // 4. 构建并发送请求
        UserMessage userMessage = UserMessage.from(
                TextContent.from("请用中文描述这个声音"),
                audioContent
        );
        String response = chatModel.chat(userMessage).aiMessage().text();
        System.out.println(response);
    }
    //视频
    @Test
    void test12() {
        // 1. 使用一个可公开访问的视频URL进行测试
        // 你可以先用这个示例视频，它来自LangChain4j的官方演示[reference:3]
        String videoUrl = "https://storage.googleapis.com/cloud-samples-data/generative-ai/video/behind_the_scenes_pixel.mp4";
        // 2. 构建模型，务必设置日志以便调试
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .logRequests(true)  // 开启请求日志，有助于定位问题
                .logResponses(true) // 开启响应日志
                .build();
        // 3. 使用URL构建VideoContent
        VideoContent videoContent = VideoContent.from(videoUrl);
        UserMessage userMessage = UserMessage.from(
                TextContent.from("请用中文描述这个视频的内容"),
                videoContent
        );
        // 4. 发送请求
        String response = chatModel.chat(userMessage).aiMessage().text();
        System.out.println(response);
    }
    //pdf
    //{"code":20029,"message":"Only text and image_url are supported.","data":null}
    @Test
    void test13() {
        String pdfUrl = "https://agentpng.oss-cn-beijing.aliyuncs.com/agent/2026/04/08/qcby12_10.pdf";
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .logRequests(true)  // 开启请求日志，有助于定位问题
                .logResponses(true) // 开启响应日志
                .build();
        PdfFileContent pdfContent = PdfFileContent.from(pdfUrl);
        UserMessage userMessage = UserMessage.from(
                TextContent.from("请用中文描述这个文档"),
                pdfContent
        );
        String response = chatModel.chat(userMessage).aiMessage().text();
        System.out.println(response);
    }




}
