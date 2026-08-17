package com.stock.sina_stock_web.controller;

import com.stock.sina_stock_web.StockInfo;
import com.stock.sina_stock_web.StockMarketService;
import com.stock.sina_stock_web.util.SinaStockUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
public class StockController {

    @Autowired
    private SinaStockUtil sinaStockUtil;

    @Autowired
    private StockMarketService marketService;

    // 内存中的全量股票列表 (代码 -> 名称)
    private final Map<String, String> ALL_STOCKS = new HashMap<>();
    // 反向索引 (名称 -> 代码)，方便搜索
    private final Map<String, String> NAME_TO_CODE = new HashMap<>();

    /**
     * 项目启动时执行：加载全量股票代码表
     */
    @PostConstruct
    public void initStockData() {
        try {
            ClassPathResource resource = new ClassPathResource("stocks.csv");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );
            
            String line;
            reader.readLine(); // 跳过表头
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    String code = parts[0].trim().toLowerCase();
                    String name = parts[1].trim();
                    ALL_STOCKS.put(code, name);
                    NAME_TO_CODE.put(name, code);
                }
            }
            System.out.println("✅ 成功加载 " + ALL_STOCKS.size() + " 只股票数据到内存！");
        } catch (Exception e) {
            System.err.println("❌ 加载股票数据失败: " + e.getMessage());
        }
    }

    /**
     * 1. 首页入口
     */
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> index() throws Exception {
        ClassPathResource resource = new ClassPathResource("stock.html");
        if (!resource.exists()) return ResponseEntity.notFound().build();
        
        byte[] content = resource.getInputStream().readAllBytes();
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(content);
    }

    /**
     * 2. 智能搜索接口
     */
    @GetMapping("/api/search")
    public List<StockInfo> search(@RequestParam String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return Collections.emptyList();
        
        String k = keyword.trim().toLowerCase();
        List<String> targetCodes = new ArrayList<>();

        if (k.startsWith("sh") || k.startsWith("sz")) {
            if (ALL_STOCKS.containsKey(k)) targetCodes.add(k);
        } else {
            for (Map.Entry<String, String> entry : NAME_TO_CODE.entrySet()) {
                if (entry.getKey().contains(k)) {
                    targetCodes.add(entry.getValue());
                    if (targetCodes.size() >= 10) break; 
                }
            }
        }

        List<StockInfo> results = new ArrayList<>();
        for (String code : targetCodes) {
            try {
                String raw = sinaStockUtil.getRawData(code);
                StockInfo info = sinaStockUtil.parseStock(raw, code);
                if (info != null) results.add(info);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return results;
    }

    /**
     * 3. 排行榜接口 (聚合分析 - 本地内存排序)
     * GET /api/ranking?type=volume&top=10
     */
    @GetMapping("/api/ranking")
    public List<StockInfo> getRanking(
            @RequestParam(defaultValue = "volume") String type,
            @RequestParam(defaultValue = "10") int top) {
        return marketService.getRanking(type, top);
    }
}