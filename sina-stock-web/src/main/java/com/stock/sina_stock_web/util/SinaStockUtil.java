package com.stock.sina_stock_web.util; // ⚠️ 必须加上 .util

import com.stock.sina_stock_web.StockInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

@Component
public class SinaStockUtil {
    private final OkHttpClient client = new OkHttpClient();

    // 原有的获取原始数据方法保留
    public String getRawData(String code) throws Exception {
        String url = "http://hq.sinajs.cn/list=" + code;
        Request request = new Request.Builder().url(url)
                .addHeader("Referer", "https://finance.sina.com.cn").build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().string();
            }
        }
        return null;
    }

    // ⬇️️ 新增：解析数据的方法 ️⬇️
    public StockInfo parseStock(String rawData, String code) {
        if (rawData == null || rawData.equals("var hq_str_" + code + "=\"\";")) {
            return null; // 无效代码
        }
        
        // 截取引号内的内容
        int start = rawData.indexOf("\"") + 1;
        int end = rawData.lastIndexOf("\"");
        String content = rawData.substring(start, end);
        String[] items = content.split(",");

        if (items.length < 32) return null;

        String name = items[0];
        double open = Double.parseDouble(items[1]);
        double close = Double.parseDouble(items[2]); // 昨日收盘价
        double current = Double.parseDouble(items[3]);
        long volume = Long.parseLong(items[8]);      // 成交量（股）
        double amount = Double.parseDouble(items[9]); // 成交额（元）
        String date = items[30];
        String time = items[31];

        // 计算涨跌幅
        double change = 0;
        if (close != 0) {
            change = ((current - close) / close) * 100;
        }

        return new StockInfo(name, code, current, change, volume, amount, date + " " + time);
    }
}