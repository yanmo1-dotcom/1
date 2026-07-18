package com.stock.sina_stock_web;

import com.stock.sina_stock_web.util.SinaStockUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class StockMarketService {

    @Autowired
    private SinaStockUtil sinaStockUtil;

    // 1. 内存数据库：存储所有股票的实时数据 (代码 -> StockInfo)
    private final Map<String, StockInfo> marketData = new ConcurrentHashMap<>();
    
    // 2. 全量代码列表 (从 CSV 加载)
    private List<String> allCodes = new ArrayList<>();

    /**
     * 项目启动时初始化：读取 CSV 并立即扫描一次
     */
    @PostConstruct
    public void init() {
        System.out.println("🚀 正在初始化全市场扫描器...");
        loadAllCodesFromFile(); 
        
        if (allCodes.isEmpty()) {
            System.err.println("⚠️ 警告：未加载到任何股票代码，请检查 stocks.csv 是否存在！");
        } else {
            System.out.println("✅ 已加载 " + allCodes.size() + " 只股票代码待扫描");
            // 启动后立即执行一次扫描，让排行榜马上有数据
            scanMarket();
        }
    }

    /**
     * 从 resources/stocks.csv 读取所有代码
     */
    private void loadAllCodesFromFile() {
        try {
            ClassPathResource resource = new ClassPathResource("stocks.csv");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );
            
            String line;
            reader.readLine(); // 跳过表头 (code,name)
            
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length >= 1) {
                    // 只取第一列代码，并转为小写统一格式
                    allCodes.add(parts[0].trim().toLowerCase());
                }
            }
        } catch (Exception e) {
            System.err.println("❌ 读取 stocks.csv 失败: " + e.getMessage());
        }
    }

    /**
     * 定时任务：每 180 秒 (3分钟) 扫描一次全市场
     */
    @Scheduled(fixedRate = 180000) 
    public void scheduledScan() {
        System.out.println("⏰ 定时任务触发：开始扫描全市场数据...");
        scanMarket();
    }

    /**
     * 核心扫描逻辑 (分批处理，防止请求过快)
     */
    private void scanMarket() {
        int successCount = 0;
        int failCount = 0;

        // 遍历所有代码进行请求
        for (String code : allCodes) {
            try {
                String raw = sinaStockUtil.getRawData(code);
                StockInfo info = sinaStockUtil.parseStock(raw, code);
                
                if (info != null) {
                    marketData.put(code, info);
                    successCount++;
                } else {
                    failCount++;
                }
                
                // 【可选】如果以后代码多了（比如超过100个），建议在这里加个微小延迟
                // Thread.sleep(20); 

            } catch (Exception e) {
                failCount++;
            }
        }
        System.out.println("✅ 扫描完成！成功: " + successCount + ", 失败/无效: " + failCount);
    }

    /**
     * 获取排行榜 (聚合分析)
     * @param type "volume" (成交量), "amount" (成交额), "change" (涨跌幅)
     * @param topN 取前几名
     */
    public List<StockInfo> getRanking(String type, int topN) {
        Collection<StockInfo> allStocks = marketData.values();
        
        // 如果内存里还没数据，返回空列表
        if (allStocks.isEmpty()) return Collections.emptyList();

        Comparator<StockInfo> comparator;
        switch (type) {
            case "volume": // 成交量
                comparator = Comparator.comparingDouble(StockInfo::getVolume).reversed();
                break;
            case "amount": // 成交额
                comparator = Comparator.comparingDouble(StockInfo::getAmount).reversed();
                break;
            case "change": // 涨跌幅
                comparator = Comparator.comparingDouble(StockInfo::getChangePercent).reversed();
                break;
            default:
                return Collections.emptyList();
        }

        return allStocks.stream()
                .sorted(comparator)
                .limit(topN)
                .collect(Collectors.toList());
    }
}