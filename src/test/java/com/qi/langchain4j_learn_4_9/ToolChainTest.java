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

@SpringBootTest
public class ToolChainTest {

    // 有效的高德 API Key（你 curl 测试成功的那个）
    private static final String AMAP_KEY = "1111111111111111111";

    // 城市名 -> adcode 映射表
    private static final Map<String, String> CITY_TO_ADCODE = new HashMap<>();

    @BeforeAll
    static void loadAdcodeMapping() throws Exception {
        // 读取 resources 下的 Excel 文件
        try (InputStream is = ToolChainTest.class.getResourceAsStream("/AMap_adcode_citycode.xlsx")) {
            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // 跳过标题行
                String chineseName = getCellString(row.getCell(0));
                String adcode = getCellString(row.getCell(1));
                if (chineseName != null && !chineseName.isEmpty() && adcode != null && !adcode.isEmpty()) {
                    CITY_TO_ADCODE.put(chineseName, adcode);
                    // 同时存入无"市"/"区"后缀的简称，比如 "济南" 也能匹配 "济南市"
                    if (chineseName.endsWith("市")) {
                        CITY_TO_ADCODE.put(chineseName.substring(0, chineseName.length() - 1), adcode);
                    } else if (chineseName.endsWith("区")) {
                        CITY_TO_ADCODE.put(chineseName.substring(0, chineseName.length() - 1), adcode);
                    }
                }
            }
        }
        System.out.println("加载城市编码完成，共 " + CITY_TO_ADCODE.size() + " 条映射");
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    public ToolSpecification toolSpecification = ToolSpecification.builder()
            .name("getWeather")
            .description("根据城市名称获取该城市的实时天气信息")
            .parameters(JsonObjectSchema.builder()
                    .addStringProperty("city", "城市名称，例如：济南、北京、上海")
                    .required("city")
                    .build())
            .build();

    private String getWeatherFromAmap(String cityName) {
        // 1. 从映射表中获取 adcode
        String adcode = CITY_TO_ADCODE.get(cityName);
        if (adcode == null) {
            // 尝试模糊匹配：比如用户输入 "济南市" 但映射只有 "济南"
            for (Map.Entry<String, String> entry : CITY_TO_ADCODE.entrySet()) {
                if (entry.getKey().contains(cityName) || cityName.contains(entry.getKey())) {
                    adcode = entry.getValue();
                    break;
                }
            }
        }
        if (adcode == null) {
            return "未找到城市编码：" + cityName;
        }

        // 2. 调用高德天气 API
        String url = "https://restapi.amap.com/v3/weather/weatherInfo?key=" + AMAP_KEY
                + "&city=" + adcode
                + "&extensions=base"
                + "&output=JSON";

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                return "天气服务请求失败，HTTP状态码：" + response.code();
            }
            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            String status = json.get("status").getAsString();
            if (!"1".equals(status)) {
                return "天气接口返回错误：" + json.get("info").getAsString();
            }
            JsonObject live = json.getAsJsonArray("lives").get(0).getAsJsonObject();
            String province = live.get("province").getAsString();
            String city = live.get("city").getAsString();
            String weather = live.get("weather").getAsString();
            String temperature = live.get("temperature").getAsString();
            String windDirection = live.get("winddirection").getAsString();
            String windPower = live.get("windpower").getAsString();
            String humidity = live.get("humidity").getAsString();

            return String.format("%s%s，天气：%s，温度：%s℃，风向：%s，风力：%s级，湿度：%s%%",
                    province, city, weather, temperature, windDirection, windPower, humidity);
        } catch (Exception e) {
            return "获取天气时发生异常：" + e.getMessage();
        }
    }
    //工具调用。AI真好用
    //*
    // 工具方法的限制
    //使用 @Tool 注解的方法：
    //
    //可以是静态的或非静态的
    //可以是任意可见性（public、private 等）
    //工具方法参数
    //带有 @Tool 注解的方法可以接受任意数量、类型的参数：
    //
    //基本类型：int、double 等
    //对象类型：String、Integer、Double 等
    //自定义 POJO（可包含嵌套 POJO）
    //enum 枚举
    //List<T> / Set<T>，其中 T 是上述类型之一
    //Map<K,V>（需通过 @P 明确指定 K 和 V 的类型）
    //不带参数的方法也支持。
    //
    //必填与可选
    //默认情况下，所有工具方法参数都被视为 必填。
    //这意味着 LLM 必须为这些参数生成值。
    //
    //通过在参数上使用 @P(required = false)，可以将其设为可选：
    // *//
    @Test
    void testWeatherToolStreaming() throws Exception {
        StreamingChatModel chatModel = OpenAiStreamingChatModel.builder()
                .apiKey("sk-wlwldolewwramfvxtxdkpkoqgfryvyixovzhnkdqysjxvooz")
                .modelName("deepseek-ai/DeepSeek-V3.2")
                .baseUrl("https://api.siliconflow.cn/v1")
                .build();

        // 第一轮请求：用户询问天气
        ChatRequest firstRequest = ChatRequest.builder()
                .messages(UserMessage.from("今天济南的天气怎么样，用华氏度显示"))
                .toolSpecifications(toolSpecification)
                .build();

        // 异步等待第一轮完整响应
        CompletableFuture<ChatResponse> firstResponseFuture = new CompletableFuture<>();
        chatModel.chat(firstRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // 第一轮不输出部分内容，因为可能只是工具调用请求
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                firstResponseFuture.complete(response);
            }

            @Override
            public void onError(Throwable error) {
                firstResponseFuture.completeExceptionally(error);
            }
        });

        ChatResponse firstResponse = firstResponseFuture.get(); // 阻塞等待完整响应
        AiMessage aiMessage = firstResponse.aiMessage();

        if (!aiMessage.hasToolExecutionRequests()) {
            System.out.println("模型直接回答：" + aiMessage.text());
            return;
        }

        // 处理工具调用
        System.out.println("模型请求调用工具，共 " + aiMessage.toolExecutionRequests().size() + " 个请求：");
        ToolExecutionRequest toolReq = aiMessage.toolExecutionRequests().get(0);
        System.out.println("工具名称：" + toolReq.name());
        System.out.println("参数：" + toolReq.arguments());

        // 执行真实天气查询
        JsonObject args = JsonParser.parseString(toolReq.arguments()).getAsJsonObject();
        String city = args.get("city").getAsString();
        String weatherResult = getWeatherFromAmap(city);
        ToolExecutionResultMessage toolResultMsg = ToolExecutionResultMessage.from(toolReq, weatherResult);

        // 第二轮请求：将工具结果发给模型，并流式输出最终回答
        ChatRequest secondRequest = ChatRequest.builder()
                .messages(
                        UserMessage.from("今天济南的天气怎么样，用华氏度显示"),
                        aiMessage,
                        toolResultMsg
                )
                .build(); // 不再需要 toolSpecifications

        CompletableFuture<Void> finalOutputFuture = new CompletableFuture<>();
        chatModel.chat(secondRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                System.out.print(partialResponse); // 实时流式输出
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

        finalOutputFuture.get(); // 等待流式输出完成
    }
}