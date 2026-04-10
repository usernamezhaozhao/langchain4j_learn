package com.qi.langchain4j_learn_4_9;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.*;
import dev.langchain4j.service.tool.ToolExecution;
import lombok.ToString;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

interface Assistant {
    String chat(String userMessage);
}
interface TokenStreamAssistant {
    TokenStream chat(String userMessage);
    @UserMessage("你必须使用 imply 工具来对暗号：{{it}}")
    TokenStream chatAsImply(String userMessage);
}
interface Gugugaga {
    @SystemMessage("你是一个只会叫“咕咕嘎嘎”的臭企鹅")
    TokenStream chat(String userMessage);
}
interface Ask {
    @UserMessage("请你说下Java的多态 {{it}}")
    TokenStream chat(String userMessage);
}
interface ResultAssistant<T> {
    @UserMessage("生成一个文章，两句话结束: {{it}}")
    Result<List<String>> generateOutlineFor(String topic);
}
interface SentimentAnalyzer {
    @UserMessage("{{it}} 是男生吗")
    boolean isMale(String text);
}
enum Height {
    HIGH, MEDIUM, LOW
}
interface HeightAnalyzer {
    @UserMessage("{{it}} 它的身高是很高中等还是矮?")
    Height analyzeHeight(String text);
}
//使用对象类输出时，对格式要求非常严格。但大模型又是概率模型，这里隐患是不小的，很容易报错
@ToString
class Person {
    @Description("名") // 你可以添加一个可选的描述来帮助 LLM 更好地理解
    String firstName;
    @Description("姓")
    String lastName;
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Description("出生日期，如果只有年份则格式为 yyyy，如果有完整日期则 yyyy-MM-dd")
    LocalDate birthDate;
    Address address;
}
@Description("一个地址") // 你可以添加一个可选的描述来帮助 LLM 更好地理解
class Address {
    String street;
    Integer streetNumber;
    String city;
}
interface PersonExtractor {
    @UserMessage("""
            从以下文本中提取人物信息，输出严格符合以下JSON格式：
             {
               "firstName": "约翰",
               "lastName": "多伊",
               "birthDate": "1968",
               "address": {
                 "street": "低语松林大道",
                 "streetNumber": 345,
                 "city": "斯普林菲尔德"
               }
             }
             注意：
             - birthDate 可以是只有年份的字符串（如 "1968"），也可以是完整日期 "1968-01-01"。
             - 如果文本中没有街道号，streetNumber 可以为 null。
             - 不要输出任何额外文字或注释。
             文本：{{it}}
            """)
    Person extractPersonFrom(String text);
}
interface FluxAssistant {
    Flux<String> chat(String message);
}
interface MemoryAssistant {
    Flux<String> chat(@MemoryId int memoryId, @UserMessage String message);
}
class Tools {
    @Tool(name = "imply", value = "用于对暗号。输入暗号文本，返回对应的暗号答案。如果输入是'歌未竟'，返回'东方白'；否则返回'只有天知道'。")
    String imply(String text) {
        return text.equals("歌未竟") ? "东方白" : "只有天知道";
    }

}
@SpringBootTest
public class AssistantChain {
    //AI服务类的非流式
    @Test
    void Test1 (){
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("Qwen/Qwen3-Omni-30B-A3B-Instruct")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        Assistant assistant = AiServices.create(Assistant.class, chatModel);
        String answer = assistant.chat("你是谁");
        System.out.println(answer);
    }
    //AI服务类的流式
    @Test
    void Test2 (){
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(chatModel) // 关键：使用 streamingChatModel 方法
                .build();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        TokenStream tokenStream = assistant.chat("你是谁");
        tokenStream.onPartialResponse(System.out::print)
                .onCompleteResponse(response -> future.complete(response))
                .onError(future::completeExceptionally)
                .start();
        future.join();
    }
    //AI服务类的流式，角色定义
    @Test
    void Test3 (){
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        Gugugaga assistant = AiServices.builder(Gugugaga.class)
                .streamingChatModel(chatModel) // 关键：使用 streamingChatModel 方法
//                .systemMessageProvider(chatMemoryId -> "你是一个只会叫“咕咕嘎嘎”的臭企鹅")
                .build();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        TokenStream tokenStream = assistant.chat("你是谁");
        tokenStream.onPartialResponse(System.out::print)
                .onCompleteResponse(response -> future.complete(response))
                .onError(future::completeExceptionally)
                .start();
        future.join();
    }
    //@UserMessage
    @Test
    void Test4(){
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        Ask assistant = AiServices.builder(Ask.class)
                .streamingChatModel(chatModel) // 关键：使用 streamingChatModel 方法
                .build();
        TokenStream chat = assistant.chat("用两句话说完");
        chat.onPartialResponse(System.out::print)
                .onCompleteResponse(response -> future.complete(response))
                .onError(future::completeExceptionally)
                .start();
        future.join();
    }
    //多模态
    @Test
    void Test5(){
        //AI 服务目前不支持多模态，
        //请使用低级 API 来实现。
    }
    //result
    @Test
    void Test6(){
        ChatModel syncModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        ResultAssistant syncAssistant = AiServices.create(ResultAssistant.class, syncModel);
        Result<List<String>> result = syncAssistant.generateOutlineFor("Java");
        System.out.println(result.content());
        System.out.println(result.tokenUsage());
        System.out.println(result.sources());
        System.out.println(result.toolExecutions());
        System.out.println(result.finishReason());
    }
    //结构化输出
    @Test
    void Test7(){
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        SentimentAnalyzer sentimentAnalyzer = AiServices.create(SentimentAnalyzer.class, chatModel);
        boolean positive = sentimentAnalyzer.isMale("特朗普");
        System.out.println(positive);
    }
    //enum
    @Test
    void Test8(){
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        HeightAnalyzer heightAnalyzer = AiServices.create(HeightAnalyzer.class, chatModel);
        Height height = heightAnalyzer.analyzeHeight("特朗普");
        System.out.println(height.name());
    }
    //对象类输出
    @Test
    void Test9(){
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA) //开启JSON结构化输出
                .build();
        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, chatModel);
        String text = """
             1968年，在独立日渐渐消退的回声中，
             一个名叫约翰的孩子在宁静的夜空下降生。这位新生儿姓多伊，象征着一段新旅程的开始。
             他在低语松林大道345号降临人世，这是一条坐落在斯普林菲尔德心脏地带的古雅街道，一个回响着郊区梦想与期望的住所。
             """;
        Person person = personExtractor.extractPersonFrom(text);
        System.out.println(person);
    }
    //flux
    /*
    *   异步非阻塞：不会因为等待数据而阻塞线程，当数据准备好时自动推送。
        发布-订阅模型：Flux 是 发布者，你需要用 subscribe() 成为 订阅者，数据才会流动。
        声明式操作符：用类似 Stream 的 API 对数据进行转换、过滤、合并等，但操作是 懒加载 的，只有订阅时才执行。
        背压支持：下游可以告诉上游“我处理得慢，慢点发”，防止压垮消费者。
    * */
    @Test
    void Test10(){
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        FluxAssistant assistant = AiServices.builder(FluxAssistant.class)
                .streamingChatModel(chatModel) // 关键：使用 streamingChatModel 方法
                .build();
        assistant.chat("你是谁")
                .doOnNext(System.out::print)   // 每收到一个 token 就打印
                .doOnComplete(() -> System.out.println("\n[完成]"))
                .subscribe();                  // 触发执行
        // 或者阻塞等待（适合测试）
        List<String> result = assistant.chat("你是谁").collectList().block();
        System.out.println(result);
    }
    //AI服务记忆
    @Test
    void Test11() throws InterruptedException {
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        MemoryAssistant assistant = AiServices.builder(MemoryAssistant.class)
                .streamingChatModel(chatModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();
        // 用户1
        CountDownLatch latch1 = new CountDownLatch(1);
        assistant.chat(1, "你好，我是七七")
                .doOnNext(System.out::print)
                .doOnComplete(() -> {
                    System.out.println("\n[用户1 完成]");
                    latch1.countDown();
                })
                .subscribe();
        latch1.await(30, TimeUnit.SECONDS);  // 等待用户1完成
        // 用户2
        CountDownLatch latch2 = new CountDownLatch(1);
        assistant.chat(1, "你还记得我是谁吗")
                .doOnNext(System.out::print)
                .doOnComplete(() -> {
                    System.out.println("\n[用户2 完成]");
                    latch2.countDown();
                })
                .subscribe();
        latch2.await(30, TimeUnit.SECONDS);
    }
    //工具调用
    @Test
    void Test12(){
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(chatModel)
                .tools(new Tools()) //注册工具
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .build();
        TokenStream tokenStream = assistant.chatAsImply("请使用 imply 工具对暗号：人猿相揖别！");
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        tokenStream.onPartialResponse(System.out::print)
                .onCompleteResponse(response -> future.complete(response))
                .onError(future::completeExceptionally)
                .start();
        future.join();
    }

}
