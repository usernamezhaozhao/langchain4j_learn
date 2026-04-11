package tool;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 天气查询工具类
 *
 * 功能: 使用 @Tool 注解自动生成 ToolSpecification，让 AI 能够调用天气查询功能
 * 使用场景: 演示工具调用（Function Calling）的完整流程
 *
 * 技术要点:
 * - 使用高德地图天气 API 获取实时天气
 * - 从 Excel 文件加载城市编码映射表
 * - 支持摄氏度和华氏度两种温度单位
 * - 使用 @Tool 和 @P 注解让 AI 自动识别和调用
 *
 * 工作流程:
 * 1. 用户: “今天济南的天气怎么样，用华氏度显示”
 * 2. AI 判断需要调用 getWeather 工具
 * 3. AI 返回: { “city”: “济南”, “temperatureUnit”: “FAHRENHEIT” }
 * 4. 框架自动调用 getWeather() 方法
 * 5. 方法返回天气信息
 * 6. AI 根据天气信息生成最终回答
 */
public class WeatherTools {

    // 高德地图 API Key（需要替换为你自己的有效 key）
    // 申请地址: https://lbs.amap.com/
    private static final String AMAP_KEY = “2d866721e3ee378cbd3124b735c4c3fc”;

    // 城市名 -> adcode（行政区划代码）映射表
    // 高德 API 需要使用 adcode 而不是城市名称
    // 例如: “济南” -> “370100”, “北京” -> “110000”
    private static final Map<String, String> CITY_TO_ADCODE = new HashMap<>();

    /**
     * 温度单位枚举
     *
     * 功能: 定义温度单位，供 AI 选择
     * CELSIUS: 摄氏度（℃）
     * FAHRENHEIT: 华氏度（℉）
     *
     * 为什么使用枚举?
     * - 类型安全，避免传入无效值
     * - AI 可以从枚举值中选择，不会出现拼写错误
     * - 代码更清晰，易于维护
     */
    public enum TemperatureUnit {
        CELSIUS,     // 摄氏度
        FAHRENHEIT   // 华氏度
    }

    /**
     * 静态初始化块
     *
     * 功能: 在类加载时自动执行，加载城市编码映射表
     * 时机: 类第一次被使用时执行一次
     *
     * 为什么使用静态初始化块?
     * - 只需要加载一次，提升性能
     * - 所有实例共享同一份数据
     * - 在任何方法调用之前完成初始化
     */
    static {
        // 加载 Excel 中的城市编码表
        try (InputStream is = WeatherTools.class.getResourceAsStream(“/AMap_adcode_citycode.xlsx”)) {
            // 使用 Apache POI 解析 Excel 文件
            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = workbook.getSheetAt(0);  // 获取第一个 Sheet

            // 遍历所有行
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;  // 跳过标题行

                // 读取城市名称（第一列）和 adcode（第二列）
                String chineseName = getCellString(row.getCell(0));
                String adcode = getCellString(row.getCell(1));

                // 验证数据有效性
                if (chineseName != null && !chineseName.isEmpty() && adcode != null && !adcode.isEmpty()) {
                    // 存入映射表
                    CITY_TO_ADCODE.put(chineseName, adcode);

                    // 添加无后缀的简称
                    // 例如: “济南市” -> “370100”, “济南” -> “370100”
                    // 这样用户输入”济南”或”济南市”都能匹配
                    if (chineseName.endsWith(“市”) || chineseName.endsWith(“区”)) {
                        CITY_TO_ADCODE.put(chineseName.substring(0, chineseName.length() - 1), adcode);
                    }
                }
            }
            System.out.println(“加载城市编码完成，共 “ + CITY_TO_ADCODE.size() + “ 条映射”);
        } catch (Exception e) {
            System.err.println(“加载城市编码失败：” + e.getMessage());
        }
    }

    /**
     * 从 Excel 单元格中读取字符串
     *
     * @param cell Excel 单元格
     * @return 单元格的字符串值，去除首尾空格
     *
     * 技术细节:
     * - Excel 单元格可能是多种类型（数字、字符串、日期等）
     * - setCellType(CellType.STRING) 强制转换为字符串类型
     * - trim() 去除首尾空格，避免匹配失败
     */
    private static String getCellString(Cell cell) {
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    /**
     * 获取指定城市的实时天气
     *
     * @param city 城市名称，如”济南”、”北京”
     * @param temperatureUnit 温度单位（CELSIUS 或 FAHRENHEIT）
     * @return 天气描述字符串，格式: “山东济南，天气：晴，温度：25℃，风向：南风，风力：3级，湿度：60%”
     *
     * @Tool 注解说明:
     * - value: 工具描述，AI 根据这个判断何时调用工具
     * - 描述要清晰准确，帮助 AI 理解工具的功能
     *
     * @P 注解说明:
     * - 用于描述参数的含义
     * - 帮助 AI 正确填充参数值
     * - 例如: AI 看到 “The city for which...” 就知道这个参数是城市名称
     *
     * 工作流程:
     * 1. 根据城市名获取 adcode（行政区划代码）
     * 2. 调用高德天气 API 获取实时天气
     * 3. 解析 JSON 响应，提取天气数据
     * 4. 根据温度单位转换温度（摄氏度 <-> 华氏度）
     * 5. 格式化天气信息并返回
     */
    @Tool(“Returns the weather forecast for a given city”)
    public String getWeather(
            @P(“The city for which the weather forecast should be returned”) String city,
            @P(“The temperature unit: CELSIUS or FAHRENHEIT”) TemperatureUnit temperatureUnit
    ) {
        // 1. 根据城市名获取 adcode
        String adcode = CITY_TO_ADCODE.get(city);

        // 如果直接匹配失败，尝试模糊匹配
        if (adcode == null) {
            for (Map.Entry<String, String> entry : CITY_TO_ADCODE.entrySet()) {
                if (entry.getKey().contains(city) || city.contains(entry.getKey())) {
                    adcode = entry.getValue();
                    break;
                }
            }
        }

        // 如果还是找不到，返回错误信息
        if (adcode == null) {
            return “未找到城市：” + city;
        }

        // 2. 调用高德天气 API（实况天气）
        // API 文档: https://lbs.amap.com/api/webservice/guide/api/weatherinfo
        // 参数说明:
        // - key: API Key
        // - city: 城市 adcode
        // - extensions: base(实况天气) 或 all(预报天气)
        // - output: JSON 或 XML
        String url = “https://restapi.amap.com/v3/weather/weatherInfo?key=” + AMAP_KEY
                + “&city=” + adcode
                + “&extensions=base”
                + “&output=JSON”;

        // 3. 使用 OkHttp 发送 HTTP GET 请求
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(url).get().build();

        try (Response response = client.newCall(request).execute()) {
            // 检查 HTTP 状态码
            if (!response.isSuccessful()) {
                return “天气服务请求失败，HTTP状态码：” + response.code();
            }

            // 4. 解析 JSON 响应
            String body = response.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();

            // 检查 API 返回状态
            // status = “1” 表示成功, “0” 表示失败
            String status = json.get(“status”).getAsString();
            if (!”1”.equals(status)) {
                return “天气接口返回错误：” + json.get(“info”).getAsString();
            }

            // 提取天气数据
            JsonObject live = json.getAsJsonArray(“lives”).get(0).getAsJsonObject();
            String province = live.get(“province”).getAsString();        // 省份
            String cityName = live.get(“city”).getAsString();            // 城市
            String weather = live.get(“weather”).getAsString();          // 天气状况（晴、多云、雨等）
            double celsius = Double.parseDouble(live.get(“temperature”).getAsString());  // 温度（摄氏度）
            String windDirection = live.get(“winddirection”).getAsString();  // 风向
            String windPower = live.get(“windpower”).getAsString();      // 风力等级
            String humidity = live.get(“humidity”).getAsString();        // 湿度

            // 5. 根据请求的温度单位返回不同格式
            if (temperatureUnit == TemperatureUnit.FAHRENHEIT) {
                // 摄氏度转华氏度: F = C × 9/5 + 32
                double fahrenheit = celsius * 9.0 / 5.0 + 32;
                return String.format(“%s%s，天气：%s，温度：%.1f℉，风向：%s，风力：%s级，湿度：%s%%”,
                        province, cityName, weather, fahrenheit, windDirection, windPower, humidity);
            } else {
                // 返回摄氏度
                return String.format(“%s%s，天气：%s，温度：%.1f℃，风向：%s，风力：%s级，湿度：%s%%”,
                        province, cityName, weather, celsius, windDirection, windPower, humidity);
            }
        } catch (Exception e) {
            return “获取天气时发生异常：” + e.getMessage();
        }
    }
}