package com.qi.langchain4j_learn_4_9;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import tool.WeatherTools;

import java.util.List;

/**
 * 使用 @Tool 注解方式的测试类
 */
public class ToolChainTestWithAnnotation {

    // 自动从 WeatherTools 类中提取所有工具规范
    private final List<ToolSpecification> toolSpecifications =
            ToolSpecifications.toolSpecificationsFrom(WeatherTools.class);

    @Test
    void testWeatherToolWithAnnotation() {
        // 1. 创建 ChatModel（请根据你的网络环境配置代理或 baseUrl）
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                // 如果需要代理，取消注释下面两行并设置正确的代理
                // .proxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890)))
                .build();

        // 2. 第一轮请求：用户询问天气，并要求华氏度
        ChatRequest firstRequest = ChatRequest.builder()
                .messages(UserMessage.from("今天济南的天气怎么样，用华氏度显示"))
                .toolSpecifications(toolSpecifications)
                .build();

        ChatResponse firstResponse = chatModel.chat(firstRequest);
        AiMessage aiMessage = firstResponse.aiMessage();

        // 3. 检查模型是否请求调用工具
        if (aiMessage.hasToolExecutionRequests()) {
            System.out.println("模型请求调用工具，共 " + aiMessage.toolExecutionRequests().size() + " 个请求：");

            // 创建工具实例
            WeatherTools weatherTools = new WeatherTools();

            for (ToolExecutionRequest req : aiMessage.toolExecutionRequests()) {
                System.out.println("工具名称：" + req.name());
                System.out.println("参数：" + req.arguments());

                // 这里可以更通用地调用任意工具，但为了清晰，我们只处理 getWeather
                if ("getWeather".equals(req.name())) {
                    // 手动解析参数并调用真实方法（更严谨的方式是使用 ToolExecutor，但手动也足够）
                    // 为了简化，我们直接使用已知的 getWeather 方法
                    // 实际生产环境可以使用反射或 LangChain4j 提供的 ToolExecutor
                    String result = executeWeatherTool(weatherTools, req);
                    // 将工具结果返回给模型
                    ToolExecutionResultMessage toolResultMsg = ToolExecutionResultMessage.from(req, result);
                    // 第二轮请求：将原始用户消息、模型的工具调用请求、工具结果一起发送
                    ChatRequest secondRequest = ChatRequest.builder()
                            .messages(
                                    UserMessage.from("今天济南的天气怎么样，用华氏度显示"),
                                    aiMessage,
                                    toolResultMsg
                            )
                            .build();  // 不再需要 toolSpecifications，因为本轮不应再调用工具

                    ChatResponse finalResponse = chatModel.chat(secondRequest);
                    System.out.println("最终回答：" + finalResponse.aiMessage().text());
                }
            }
        } else {
            System.out.println("模型直接回答：" + aiMessage.text());
        }
    }

    /**
     * 根据 ToolExecutionRequest 执行真实的 getWeather 方法
     * 这里简化处理，仅用于演示
     */
    private String executeWeatherTool(WeatherTools tools, ToolExecutionRequest req) {
        // 解析参数 JSON，例如 {"city": "济南", "temperatureUnit": "FAHRENHEIT"}
        com.google.gson.JsonObject args = com.google.gson.JsonParser.parseString(req.arguments()).getAsJsonObject();
        String city = args.get("city").getAsString();
        String unitStr = args.get("temperatureUnit").getAsString();
        WeatherTools.TemperatureUnit unit = WeatherTools.TemperatureUnit.valueOf(unitStr);
        return tools.getWeather(city, unit);
    }
}