package com.qi.langchain4j_learn_4_9;

import ch.qos.logback.core.subst.Tokenizer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@SpringBootTest
public class MemoryTest {

    //存储记忆
    @Test
    void Test2() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        // 第一轮对话
        UserMessage userMessage1 = UserMessage.from("你好我是七七");
        AiMessage message1 = chatModel.chat(userMessage1).aiMessage();
        chatMemory.add(userMessage1, message1);
        System.out.println("AI: " + message1.text());

        // 第二轮对话：需要带上历史
        List<ChatMessage> history = chatMemory.messages();
        UserMessage userMessage2 = UserMessage.from("你还记得我是谁吗，请说出你的记忆，我叫什么");
        List<ChatMessage> allMessages = new ArrayList<>(history);
        allMessages.add(userMessage2);

        AiMessage message2 = chatModel.chat(allMessages).aiMessage(); // 或者 chat(allMessages)
        System.out.println("AI: " + message2.text());

        // 将本轮对话也存入记忆（可选）
        chatMemory.add(userMessage2, message2);

        chatMemory.clear();
    }
    public final Map<Object, List<ChatMessage>> store = new ConcurrentHashMap<>();
    class PersistentChatMemoryStore implements ChatMemoryStore {
        @Override
        public List<ChatMessage> getMessages(Object o) {
            return store.getOrDefault(o, List.of());
        }
        @Override
        public void updateMessages(Object o, List<ChatMessage> list) {
            store.put(o, list);
        }
        @Override
        public void deleteMessages(Object o) {
            store.remove(o);
        }
    }
    //持久化存储记忆
    @Test
    void Test3(){
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryStore(new PersistentChatMemoryStore())
                .maxMessages(10)
                .build();
        // 第一轮对话
        UserMessage userMessage1 = UserMessage.from("你好我是七七");
        AiMessage message1 = chatModel.chat(userMessage1).aiMessage();
        chatMemory.add(userMessage1, message1);
        System.out.println("AI: " + message1.text());
        // 第二轮对话：需要带上历史
        List<ChatMessage> history = chatMemory.messages();
        UserMessage userMessage2 = UserMessage.from("你还记得我是谁吗，请说出你的记忆，我叫什么");
        List<ChatMessage> allMessages = new ArrayList<>(history);
        allMessages.add(userMessage2);
        AiMessage message2 = chatModel.chat(allMessages).aiMessage(); // 或者 chat(allMessages)
        System.out.println("AI: " + message2.text());
        // 将本轮对话也存入记忆（可选）
        chatMemory.add(userMessage2, message2);
        //输出记忆
        List<ChatMessage> messages = chatMemory.messages();
        messages.forEach(System.out::println);
        for (Map.Entry<Object, List<ChatMessage>> entry : store.entrySet()) {
            Object sessionId = entry.getKey();
            List<ChatMessage> messages1 = entry.getValue();
            System.out.println("会话: " + sessionId);
            for (ChatMessage msg : messages1) {
                System.out.println("  " + msg);
            }
        }
        chatMemory.clear();
        store.clear();
    }
    //持久化存储记忆
    @Test
    void Test4() {
        //移步Assistant
    }
    //消息数量窗口
    @Test
    void Test5() {
        // 只保留最近 3 条消息
        MessageWindowChatMemory memory = MessageWindowChatMemory.withMaxMessages(3);

        memory.add(UserMessage.from("1"));
        memory.add(UserMessage.from("2"));
        memory.add(UserMessage.from("3"));
        memory.add(UserMessage.from("4")); // 第 1 条会被挤出

        System.out.println("当前消息数量：" + memory.messages().size()); // 3
        memory.messages().forEach(System.out::println); // 2,3,4
    }
    //Token窗口
    @Test
    void Test6() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        // 只保留最近 3 条消息
        TokenWindowChatMemory memory = TokenWindowChatMemory
                .builder()
                .maxTokens(100,new OpenAiTokenCountEstimator("gpt-3.5-turbo"))
                .build();
        memory.add(UserMessage.from("我是七七"));
        List<ChatMessage> messages = memory.messages();
        UserMessage userMessage = UserMessage.from("你还记得我是谁吗");
        messages.add(userMessage);
        AiMessage aiMessage = chatModel.chat(messages).aiMessage();
        System.out.println(aiMessage.text());
        System.out.println("当前token数：" + memory.messages().size());
        System.out.println("消息：" + memory.messages());
    }

}
