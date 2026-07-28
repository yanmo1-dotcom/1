package com.webchat.model;

/** 一局对战结束后的战绩记录，持久化在 matches.json，独立于聊天消息 */
public class MatchRecord {
    private Long id;
    private String gameType = "TIC_TAC_TOE";
    private Long playerAId;        // 先手
    private Long playerBId;        // 后手
    private Long winnerId;         // 平局为 null
    private int playerAScore;      // 终局比分（如3=三连子数，简化可用1）
    private int playerBScore;
    private int rankDeltaA;       // 积分变动 (+/-)
    private int rankDeltaB;
    private String result;         // WIN / LOSS / DRAW（相对 playerA 视角）
    private String boardLog;       // JSON 棋谱（终局9格或走子序列）
    private long createdAt;
    private long finishedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }
    public Long getPlayerAId() { return playerAId; }
    public void setPlayerAId(Long playerAId) { this.playerAId = playerAId; }
    public Long getPlayerBId() { return playerBId; }
    public void setPlayerBId(Long playerBId) { this.playerBId = playerBId; }
    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }
    public int getPlayerAScore() { return playerAScore; }
    public void setPlayerAScore(int playerAScore) { this.playerAScore = playerAScore; }
    public int getPlayerBScore() { return playerBScore; }
    public void setPlayerBScore(int playerBScore) { this.playerBScore = playerBScore; }
    public int getRankDeltaA() { return rankDeltaA; }
    public void setRankDeltaA(int rankDeltaA) { this.rankDeltaA = rankDeltaA; }
    public int getRankDeltaB() { return rankDeltaB; }
    public void setRankDeltaB(int rankDeltaB) { this.rankDeltaB = rankDeltaB; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getBoardLog() { return boardLog; }
    public void setBoardLog(String boardLog) { this.boardLog = boardLog; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getFinishedAt() { return finishedAt; }
    public void setFinishedAt(long finishedAt) { this.finishedAt = finishedAt; }
}
