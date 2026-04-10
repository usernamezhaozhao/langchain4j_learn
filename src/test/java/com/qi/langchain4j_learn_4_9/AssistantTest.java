package com.qi.langchain4j_learn_4_9;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

interface MyAssistant {
    // 带记忆ID，支持多用户
    String chat(@MemoryId String userId, @UserMessage String message);
}
interface MultiUserAssistant {
    String chat(@MemoryId Long userId, @UserMessage String msg);
}


@SpringBootTest(classes = {AssistantTest.class})
public class AssistantTest {
    //持久化存储记忆
    @Test
    void Test4(){
        //移步Assistant
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        // 3. 构建 AI 服务
        MyAssistant assistant = AiServices.builder(MyAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();
        // 4. 第一轮对话
        String ans1 = assistant.chat("user_001", "我叫小明");
        System.out.println("AI: " + ans1);

        // 5. 第二轮对话（自动带记忆）
        String ans2 = assistant.chat("user_001", "我叫什么？");
        System.out.println("AI: " + ans2);
    }
    //持久化存储记忆
    @Test
    void Test5(){
        //移步Assistant
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        // 3. 构建 AI 服务
        MultiUserAssistant assistant = AiServices.builder(MultiUserAssistant.class)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .chatModel(chatModel)
                .build();
        // 用户 1
        System.out.println(assistant.chat(1L, "我叫张三"));
        System.out.println(assistant.chat(1L, "我是谁？"));

        // 用户 2（互不干扰）
        System.out.println(assistant.chat(2L, "我叫李四"));
        System.out.println(assistant.chat(2L, "我是谁？"));
    }

}
