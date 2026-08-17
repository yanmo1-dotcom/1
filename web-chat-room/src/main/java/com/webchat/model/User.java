package com.webchat.model;

import java.util.ArrayList;
import java.util.List;

public class User {
    private Long id;
    private String username;
    private String password;
    private List<Long> friends = new ArrayList<>();
    private List<String> groups = new ArrayList<>(); // 【新增】群组列表

    // ===== 账户竞技体系（独立于聊天消息）=====
    private String nickname;                       // 昵称，缺省取 username
    private long registeredAt = 0;                 // 注册时间
    private long lastLoginAt = 0;                  // 最后登录时间
    private int rankPoints = 0;                    // 当前积分 (Rank)
    private int wins = 0;                          // 总胜场
    private int losses = 0;                        // 总负场
    private int draws = 0;                         // 总平局
    private int currentStreak = 0;                 // 连胜纪录(正=连胜,负=连败,0=无)
    private String status = "ACTIVE";              // ACTIVE / MUTED 禁言 / BANNED 封号
    private String inGameState = "IDLE";           // IDLE 空闲 / IN_GAME 游戏中（运行态，不持久化重要性）

    // Getter & Setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public List<Long> getFriends() { return friends; }
    public void setFriends(List<Long> friends) { this.friends = friends; }

    public List<String> getGroups() { return groups; }
    public void setGroups(List<String> groups) { this.groups = groups; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public long getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(long registeredAt) { this.registeredAt = registeredAt; }
    public long getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(long lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public int getRankPoints() { return rankPoints; }
    public void setRankPoints(int rankPoints) { this.rankPoints = rankPoints; }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
    public int getDraws() { return draws; }
    public void setDraws(int draws) { this.draws = draws; }
    public int getCurrentStreak() { return currentStreak; }
    public void setCurrentStreak(int currentStreak) { this.currentStreak = currentStreak; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInGameState() { return inGameState; }
    public void setInGameState(String inGameState) { this.inGameState = inGameState; }

    /** 胜率（0-100），0 场返回 0 */
    public double getWinRate() {
        int total = wins + losses + draws;
        return total == 0 ? 0.0 : (wins * 100.0 / total);
    }
}