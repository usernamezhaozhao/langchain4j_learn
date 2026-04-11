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
 * 天气查询工具，使用 @Tool 注解自动生成 ToolSpecification
 */
public class WeatherTools {

    // 高德 API Key（请替换为你自己的有效 key）
    private static final String AMAP_KEY = "2d866721e3ee378cbd3124b735c4c3fc";

    // 城市名 -> adcode 映射表（静态加载）
    private static final Map<String, String> CITY_TO_ADCODE = new HashMap<>();

    // 温度单位枚举（供模型选择）
    public enum TemperatureUnit {
        CELSIUS, FAHRENHEIT
    }

    static {
        // 加载 Excel 中的城市编码表
        try (InputStream is = WeatherTools.class.getResourceAsStream("/AMap_adcode_citycode.xlsx")) {
            Workbook workbook = WorkbookFactory.create(is);
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // 跳过标题行
                String chineseName = getCellString(row.getCell(0));
                String adcode = getCellString(row.getCell(1));
                if (chineseName != null && !chineseName.isEmpty() && adcode != null && !adcode.isEmpty()) {
                    CITY_TO_ADCODE.put(chineseName, adcode);
                    // 添加无后缀的简称
                    if (chineseName.endsWith("市") || chineseName.endsWith("区")) {
                        CITY_TO_ADCODE.put(chineseName.substring(0, chineseName.length() - 1), adcode);
                    }
                }
            }
            System.out.println("加载城市编码完成，共 " + CITY_TO_ADCODE.size() + " 条映射");
        } catch (Exception e) {
            System.err.println("加载城市编码失败：" + e.getMessage());
        }
    }

    private static String getCellString(Cell cell) {
        if (cell == null) return null;
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    /**
     * 获取指定城市的实时天气
     * @param city 城市名称，如“济南”
     * @param temperatureUnit 温度单位（CELSIUS 或 FAHRENHEIT）
     * @return 天气描述字符串
     */
    @Tool("Returns the weather forecast for a given city")
    public String getWeather(
            @P("The city for which the weather forecast should be returned") String city,
            @P("The temperature unit: CELSIUS or FAHRENHEIT") TemperatureUnit temperatureUnit
    ) {
        // 1. 根据城市名获取 adcode
        String adcode = CITY_TO_ADCODE.get(city);
        if (adcode == null) {
            // 模糊匹配
            for (Map.Entry<String, String> entry : CITY_TO_ADCODE.entrySet()) {
                if (entry.getKey().contains(city) || city.contains(entry.getKey())) {
                    adcode = entry.getValue();
                    break;
                }
            }
        }
        if (adcode == null) {
            return "未找到城市：" + city;
        }

        // 2. 调用高德天气 API（实况天气）
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
            String cityName = live.get("city").getAsString();
            String weather = live.get("weather").getAsString();
            double celsius = Double.parseDouble(live.get("temperature").getAsString());
            String windDirection = live.get("winddirection").getAsString();
            String windPower = live.get("windpower").getAsString();
            String humidity = live.get("humidity").getAsString();

            // 根据请求的温度单位返回不同格式
            if (temperatureUnit == TemperatureUnit.FAHRENHEIT) {
                double fahrenheit = celsius * 9.0 / 5.0 + 32;
                return String.format("%s%s，天气：%s，温度：%.1f℉，风向：%s，风力：%s级，湿度：%s%%",
                        province, cityName, weather, fahrenheit, windDirection, windPower, humidity);
            } else {
                return String.format("%s%s，天气：%s，温度：%.1f℃，风向：%s，风力：%s级，湿度：%s%%",
                        province, cityName, weather, celsius, windDirection, windPower, humidity);
            }
        } catch (Exception e) {
            return "获取天气时发生异常：" + e.getMessage();
        }
    }
}