package com.webchat.model;

/** 积分规则配置 POJO，持久化在 rank-rules.json，管理员可热修改 */
public class RankRule {
    private int winPoints = 15;       // 胜利加分
    private int lossPoints = -10;     // 失败扣分
    private int drawPoints = 2;      // 平局加分
    private int diffThreshold = 200;  // 触发动态调整的双方分差阈值
    private int upsetBonus = 5;      // 低分赢高分 / 高分输低分 的额外调整幅度
    private int minPoints = 0;       // 积分保底
    private int initialPoints = 0;   // 新用户初始积分

    public int getWinPoints() { return winPoints; }
    public void setWinPoints(int winPoints) { this.winPoints = winPoints; }
    public int getLossPoints() { return lossPoints; }
    public void setLossPoints(int lossPoints) { this.lossPoints = lossPoints; }
    public int getDrawPoints() { return drawPoints; }
    public void setDrawPoints(int drawPoints) { this.drawPoints = drawPoints; }
    public int getDiffThreshold() { return diffThreshold; }
    public void setDiffThreshold(int diffThreshold) { this.diffThreshold = diffThreshold; }
    public int getUpsetBonus() { return upsetBonus; }
    public void setUpsetBonus(int upsetBonus) { this.upsetBonus = upsetBonus; }
    public int getMinPoints() { return minPoints; }
    public void setMinPoints(int minPoints) { this.minPoints = minPoints; }
    public int getInitialPoints() { return initialPoints; }
    public void setInitialPoints(int initialPoints) { this.initialPoints = initialPoints; }
}
