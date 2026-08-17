package com.webchat.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 通用对战房间。承载房间生命周期状态机 + 游戏专属数据。
 * 状态：WAITING(等待,可进/出/准备) -> READY(全员准备,锁定,倒计时) -> PLAYING(引擎接管) -> FINISHED(结算)
 */
public class GameRoom {
    private String roomId;
    private Long hostId;
    private List<Long> players = new ArrayList<>();
    private List<Long> spectators = new ArrayList<>();
    private int capacity = 2;
    private String state = "WAITING";
    private String password;
    private Long winnerId;
    private boolean draw = false;
    private long createdAt;
    private long finishedAt;

    // ===== 通用房间状态机字段 =====
    private String gameType = "TIC_TAC_TOE";      // TIC_TAC_TOE / RPS_1V1 / RPS_FFA / GUESS_NUMBER
    private Set<Long> readyPlayers = new HashSet<>();   // 已准备的玩家
    private long countdownEndsAt = 0;             // 锁定倒计时结束时间戳
    private long actionDeadline = 0;              // 当前出招/猜数截止时间戳（超时判负）
    private Long currentTurnPlayerId;            // 当前轮到的玩家（回合制游戏用）
    private Map<String, Object> gameData = new HashMap<>(); // 游戏专属状态，由引擎读写

    public GameRoom() {}

    public GameRoom(String roomId, Long hostId, String password, String gameType, int capacity) {
        this.roomId = roomId;
        this.hostId = hostId;
        this.password = password;
        this.gameType = gameType;
        this.capacity = capacity;
        this.createdAt = System.currentTimeMillis();
    }

    public boolean isFull() { return players.size() >= capacity; }
    public boolean hasPlayer(Long uid) { return players.contains(uid); }
    public boolean isSpectator(Long uid) { return spectators.contains(uid); }
    public int seatOf(Long uid) { return players.indexOf(uid); }

    public boolean isPlayerTurn(Long uid) {
        return currentTurnPlayerId != null && currentTurnPlayerId.equals(uid);
    }

    public boolean allReady() {
        if (!isFull()) return false;
        for (Long p : players) if (!readyPlayers.contains(p)) return false;
        return true;
    }

    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public Long getHostId() { return hostId; }
    public void setHostId(Long hostId) { this.hostId = hostId; }
    public List<Long> getPlayers() { return players; }
    public void setPlayers(List<Long> players) { this.players = players; }
    public List<Long> getSpectators() { return spectators; }
    public void setSpectators(List<Long> spectators) { this.spectators = spectators; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Long getWinnerId() { return winnerId; }
    public void setWinnerId(Long winnerId) { this.winnerId = winnerId; }
    public boolean isDraw() { return draw; }
    public void setDraw(boolean draw) { this.draw = draw; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getFinishedAt() { return finishedAt; }
    public void setFinishedAt(long finishedAt) { this.finishedAt = finishedAt; }

    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }
    public Set<Long> getReadyPlayers() { return readyPlayers; }
    public void setReadyPlayers(Set<Long> readyPlayers) { this.readyPlayers = readyPlayers; }
    public long getCountdownEndsAt() { return countdownEndsAt; }
    public void setCountdownEndsAt(long countdownEndsAt) { this.countdownEndsAt = countdownEndsAt; }
    public long getActionDeadline() { return actionDeadline; }
    public void setActionDeadline(long actionDeadline) { this.actionDeadline = actionDeadline; }
    public Long getCurrentTurnPlayerId() { return currentTurnPlayerId; }
    public void setCurrentTurnPlayerId(Long currentTurnPlayerId) { this.currentTurnPlayerId = currentTurnPlayerId; }
    public Map<String, Object> getGameData() { return gameData; }
    public void setGameData(Map<String, Object> gameData) { this.gameData = gameData; }
}
