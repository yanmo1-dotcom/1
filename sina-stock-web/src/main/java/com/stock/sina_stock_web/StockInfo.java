package com.stock.sina_stock_web;

public class StockInfo {
    private String name;
    private String code;
    private double currentPrice;
    private double changePercent;
    private long volume;
    private double amount;
    private String time;

    // 1. 必须加上这个无参构造函数！
    public StockInfo() {
    }

    // 2. 保留原来的带参构造函数（方便其他地方初始化）
    public StockInfo(String name, String code, double currentPrice, 
                    double changePercent, long volume, double amount, String time) {
        this.name = name;
        this.code = code;
        this.currentPrice = currentPrice;
        this.changePercent = changePercent;
        this.volume = volume;
        this.amount = amount;
        this.time = time;
    }

    // 3. 必须加上所有的 Setter 方法（用于解析 JSON 时赋值）
    public void setName(String name) { this.name = name; }
    public void setCode(String code) { this.code = code; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }
    public void setChangePercent(double changePercent) { this.changePercent = changePercent; }
    public void setVolume(long volume) { this.volume = volume; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setTime(String time) { this.time = time; }

    // 4. 必须加上所有的 Getter 方法（用于前端获取数据）
    public String getName() { return name; }
    public String getCode() { return code; }
    public double getCurrentPrice() { return currentPrice; }
    public double getChangePercent() { return changePercent; }
    public long getVolume() { return volume; }
    public double getAmount() { return amount; }
    public String getTime() { return time; }
}