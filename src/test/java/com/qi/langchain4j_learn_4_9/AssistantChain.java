package com.qi.langchain4j_learn_4_9;

import com.fasterxml.jackson.annotation.JsonFormat;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
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
import java.util.function.UnaryOperator;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.openai.OpenAiModerationModelName.TEXT_MODERATION_LATEST;

/**
 * 基础 AI 助手接口
 *
 * 功能: 最简单的聊天接口，接收用户消息，返回 AI 回复
 * 使用场景: 简单的问答系统、单轮对话
 */
interface Assistant {
    String chat(String userMessage);
}

/**
 * 流式输出 AI 助手接口
 *
 * 功能: 支持流式输出（打字机效果）的聊天接口
 * 使用场景: 需要实时反馈的聊天界面、长文本生成
 */
interface TokenStreamAssistant {
    // 基础聊天方法，返回 TokenStream 用于流式输出
    TokenStream chat(String userMessage);

    // 使用 @UserMessage 注解定义消息模板
    // {{it}} 是占位符，会被方法参数替换
    // 这个方法强制 AI 使用 imply 工具来对暗号
    @UserMessage("你必须使用 imply 工具来对暗号：{{it}}")
    TokenStream chatAsImply(String userMessage);
}

/**
 * 角色定义示例：咕咕嘎嘎企鹅
 *
 * 功能: 使用 @SystemMessage 定义 AI 的角色和行为
 * 使用场景: 需要特定角色扮演的场景（客服、助手、游戏 NPC 等）
 */
interface Gugugaga {
    // @SystemMessage 定义系统消息，告诉 AI 它的角色
    // 这里定义 AI 只会叫"咕咕嘎嘎"
    @SystemMessage("你是一个只会叫咕咕嘎嘎的臭企鹅")
    TokenStream chat(String userMessage);
}

/**
 * 消息模板示例接口
 *
 * 功能: 使用 @UserMessage 定义消息模板，简化重复性问题
 * 使用场景: 需要固定格式的问题（如"请解释 Java 的 XXX"）
 */
interface Ask {
    // {{it}} 会被方法参数替换
    // 例如: explain("多态") -> "请你说下Java的多态"
    @UserMessage("请你说下Java的多态 {{it}}")
    TokenStream chat(String userMessage);
}

/**
 * Result 返回类型示例接口
 *
 * 功能: 使用 Result<T> 返回类型，可以获取更多元数据（Token 使用量、结束原因等）
 * 使用场景: 需要监控 Token 使用量、调试、判断是否因长度限制截断
 */
interface ResultAssistant<T> {
    // 返回 Result<List<String>> 而不是 List<String>
    // Result 包含: content（实际内容）、tokenUsage（Token 使用量）、finishReason（结束原因）等
    @UserMessage("生成一个文章，两句话结束: {{it}}")
    Result<List<String>> generateOutlineFor(String topic);
}

/**
 * 布尔值返回示例接口
 *
 * 功能: AI 返回布尔值（true/false），用于判断类任务
 * 使用场景: 情感分析、分类任务、是非判断
 */
interface SentimentAnalyzer {
    // AI 会根据问题返回 true 或 false
    @UserMessage("{{it}} 是男生吗")
    boolean isMale(String text);
}

/**
 * 枚举类型：身高分类
 *
 * 功能: 定义身高的三个等级
 * 使用场景: 让 AI 返回枚举值，实现分类任务
 */
enum Height {
    HIGH,    // 高
    MEDIUM,  // 中等
    LOW      // 矮
}

/**
 * 枚举返回示例接口
 *
 * 功能: AI 返回枚举值，用于分类任务
 * 使用场景: 多分类任务（情感分类、主题分类等）
 */
interface HeightAnalyzer {
    // AI 会返回 Height 枚举中的一个值
    @UserMessage("{{it}} 它的身高是很高中等还是矮?")
    Height analyzeHeight(String text);
}

/**
 * 人物信息类
 *
 * 功能: 用于结构化输出，AI 会将提取的信息填充到这个类中
 * 使用场景: 信息提取、表单填充、数据结构化
 *
 * 注意: 使用对象类输出时，对格式要求非常严格。但大模型又是概率模型，
 *       这里隐患是不小的，很容易报错。建议使用 RESPONSE_FORMAT_JSON_SCHEMA 能力。
 */
@ToString  // Lombok 注解，自动生成 toString() 方法
class Person {
    @Description("名")  // 帮助 LLM 理解字段含义
    String firstName;

    @Description("姓")
    String lastName;

    @JsonFormat(pattern = "yyyy-MM-dd")  // 指定日期格式
    @Description("出生日期，如果只有年份则格式为 yyyy，如果有完整日期则 yyyy-MM-dd")
    LocalDate birthDate;

    Address address;  // 嵌套对象
}

/**
 * 地址信息类
 *
 * 功能: 嵌套在 Person 中，表示地址信息
 */
@Description("一个地址")  // 类级别的描述，帮助 LLM 理解
class Address {
    String street;         // 街道名称
    Integer streetNumber;  // 街道号码
    String city;           // 城市
}

/**
 * 人物信息提取接口
 *
 * 功能: 从文本中提取人物信息，返回结构化的 Person 对象
 * 使用场景: 简历解析、名片识别、文本信息提取
 */
interface PersonExtractor {
    // 使用多行字符串定义详细的提示词
    // 包含: 输出格式示例、注意事项、输入文本
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

/**
 * Flux 流式输出接口
 *
 * 功能: 使用 Reactor 的 Flux 实现流式输出
 * 使用场景: 响应式编程、异步非阻塞场景
 *
 * Flux 特点:
 * - 异步非阻塞：不会因为等待数据而阻塞线程
 * - 发布-订阅模型：需要用 subscribe() 订阅，数据才会流动
 * - 声明式操作符：用类似 Stream 的 API 对数据进行转换、过滤等
 * - 背压支持：下游可以告诉上游"我处理得慢，慢点发"
 */
interface FluxAssistant {
    // 返回 Flux<String>，每个元素是一个 Token
    Flux<String> chat(String message);
}

/**
 * 带记忆的 Flux 接口
 *
 * 功能: 结合 Flux 流式输出和多用户记忆管理
 * 使用场景: 需要记忆的响应式聊天系统
 */
interface MemoryAssistant {
    // @MemoryId 用于区分不同用户的记忆
    Flux<String> chat(@MemoryId int memoryId, @UserMessage String message);
}

/**
 * 工具类：对暗号工具
 *
 * 功能: 演示如何定义工具，让 AI 调用
 * 使用场景: 让 AI 能够调用外部函数获取数据或执行操作
 */
class Tools {
    /**
     * 对暗号工具
     *
     * @Tool 注解说明:
     * - name: 工具名称，AI 会使用这个名称调用工具
     * - value: 工具描述，AI 根据这个判断何时调用工具
     *
     * 工作原理:
     * 1. AI 判断需要对暗号
     * 2. AI 返回工具调用请求: { "name": "imply", "arguments": { "text": "歌未竟" } }
     * 3. 框架自动调用这个方法
     * 4. 将返回值发送给 AI
     * 5. AI 生成最终回答
     */
    @Tool(name = "imply", value = "用于对暗号。输入暗号文本，返回对应的暗号答案。如果输入是'歌未竟'，返回'东方白'；否则返回'只有天知道'。")
    String imply(String text) {
        return text.equals("歌未竟") ? "东方白" : "只有天知道";
    }
}

/**
 * AI 服务链测试类
 *
 * 功能: 演示 LangChain4j 的各种高级功能
 * 包括: 非流式/流式对话、角色定义、消息模板、结构化输出、Flux、记忆管理、工具调用等
 */
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
//                .systemMessageProvider(chatMemoryId -> "你是一个只会叫"咕咕嘎嘎"的臭企鹅")
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
        背压支持：下游可以告诉上游"我处理得慢，慢点发"，防止压垮消费者。
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
    //配置审核模型
    @Test
    void Test13 (){
        OpenAiModerationModel moderationModel = OpenAiModerationModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName(TEXT_MODERATION_LATEST)
                .build();
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .moderationModel(moderationModel)  // 配置审核模型
                .streamingChatModel(chatModel) // 关键：使用 streamingChatModel 方法
                .build();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        TokenStream tokenStream = assistant.chat("和我聊聊张国焘的兵变吧");
        tokenStream.onPartialResponse(System.out::print)
                .onCompleteResponse(response -> future.complete(response))
                .onError(future::completeExceptionally)
                .start();
        future.join();
    }
    @Test
    void Test14 (){
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();
        UnaryOperator<ChatRequest> transformer = request -> {
            // 获取原始消息列表
            var originalMessages = request.messages();
            // 创建可变列表
            var newMessages = new ArrayList<>(originalMessages);
            // 在最前面插入系统消息
            newMessages.add(0, dev.langchain4j.data.message.SystemMessage.from("必须只输出：咕咕嘎嘎，我是臭企鹅，不要输出任何其他内容。"));
            // 返回一个新的 ChatRequest 对象
            return ChatRequest.builder()
                    .messages(newMessages)
                    .modelName(request.modelName())
                    .temperature(request.temperature())
                    .maxOutputTokens(request.maxOutputTokens())
                    .build();
        };
        TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                .streamingChatModel(chatModel) // 关键：使用 streamingChatModel 方法
                .chatRequestTransformer(transformer)
                .build();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        TokenStream tokenStream = assistant.chat("说说Java的spring注解");
        tokenStream.onPartialResponse(System.out::print)
                .onCompleteResponse(response -> future.complete(response))
                .onError(future::completeExceptionally)
                .start();
        future.join();
    }
}
