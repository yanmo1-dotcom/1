package com.webchat.service;

import com.webchat.model.GameRoom;
import com.webchat.model.MatchRecord;
import com.webchat.model.User;
import com.webchat.service.engine.ActionResult;
import com.webchat.service.engine.GameEngine;
import com.webchat.service.engine.GameEngineRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 通用对战房间管理 + 游戏引擎分发。
 * 状态机：WAITING(等待/准备) -> READY(锁定+倒计时) -> PLAYING(引擎接管) -> FINISHED(结算+解散)。
 * 服务端权威：所有胜负判定在引擎内完成。
 */
@Service
public class RoomService {

    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private UserService userService;
    @Autowired private MatchService matchService;
    @Autowired private RankRuleService rankRuleService;
    @Autowired private GameEngineRegistry engineRegistry;

    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final Map<Long, String> userToRoom = new ConcurrentHashMap<>();
    private final Map<String, PendingMatch> matchQueueByType = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> roomTimers = new ConcurrentHashMap<>();

    private static final long LOCK_COUNTDOWN_MS = 3000;
    private static final long FINISH_DISMISS_MS = 3000;

    @PostConstruct
    public void init() { System.out.println("✅ RoomService 初始化，游戏引擎: " + engineRegistry); }
    @PreDestroy
    public void destroy() { scheduler.shutdownNow(); }

    // ============ 房间生命周期 ============

    public GameRoom createRoom(Long hostId, String gameType, String password) {
        GameEngine engine = engineRegistry.get(gameType);
        if (engine == null) return null;
        leaveRoom(hostId);
        String roomId = "R" + UUID.randomUUID().toString().substring(0, 6);
        int cap = engine.maxPlayers(gameType);
        GameRoom room = new GameRoom(roomId, hostId, password, gameType, cap);
        room.getPlayers().add(hostId);
        rooms.put(roomId, room);
        userToRoom.put(hostId, roomId);
        User u = userService.findById(hostId);
        if (u != null) u.setInGameState("IN_GAME");
        broadcastRoster(room);
        return room;
    }

    public List<GameRoom> getOpenRooms() {
        return new ArrayList<>(rooms.values()).stream()
                .filter(r -> "WAITING".equals(r.getState()) && !r.isFull())
                .collect(java.util.stream.Collectors.toList());
    }

    public GameRoom getRoom(String roomId) { return rooms.get(roomId); }

    public GameRoom joinRoom(String roomId, Long uid, String password) {
        if (userToRoom.containsKey(uid) && !roomId.equals(userToRoom.get(uid))) return null;
        GameRoom room = rooms.get(roomId);
        if (room == null) return null;
        if (!"WAITING".equals(room.getState())) return null;
        if (room.getPassword() != null && !room.getPassword().isEmpty()
                && !room.getPassword().equals(password)) return null;
        if (room.isFull()) return null;
        room.getPlayers().add(uid);
        userToRoom.put(uid, roomId);
        User u = userService.findById(uid);
        if (u != null) u.setInGameState("IN_GAME");
        broadcastRoster(room);
        return room;
    }

    /** 玩家准备/取消准备。全员 Ready 且满员 -> 锁定 + 倒计时。 */
    public GameRoom setReady(String roomId, Long uid, boolean ready) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return null;
        if (!"WAITING".equals(room.getState())) return null;
        if (!room.hasPlayer(uid)) return null;
        if (ready) room.getReadyPlayers().add(uid);
        else room.getReadyPlayers().remove(uid);
        broadcastReady(room);
        if (room.allReady()) {
            startCountdown(room);
        }
        return room;
    }

    private void startCountdown(GameRoom room) {
        room.setState("READY");
        room.setCountdownEndsAt(System.currentTimeMillis() + LOCK_COUNTDOWN_MS);
        broadcastState(room, "countdown", "全员准备，" + (LOCK_COUNTDOWN_MS/1000) + " 秒后开始");
        scheduler.schedule(() -> {
            try { beginMatch(room.getRoomId()); }
            catch (Exception e) { e.printStackTrace(); }
        }, LOCK_COUNTDOWN_MS, TimeUnit.MILLISECONDS);
    }

    private void beginMatch(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return;
        if (!"READY".equals(room.getState())) return;
        if (!room.allReady()) return;
        GameEngine engine = engineRegistry.get(room.getGameType());
        if (engine == null) { dissolve(room); return; }
        room.setState("PLAYING");
        Map<String, Object> startPayload = engine.onMatchStart(room);
        if (engine.hasActionTimeout(room.getGameType()) && startPayload != null) {
            long deadlineMs = toLong(startPayload.get("deadline"));
            scheduleActionTimeout(room, deadlineMs);
        }
        broadcastState(room, startPayload == null ? "match-start" : (String) startPayload.getOrDefault("event", "match-start"),
                startPayload == null ? "对局开始" : String.valueOf(startPayload.getOrDefault("message", "对局开始")));
        if (startPayload != null) {
            messagingTemplate.convertAndSend("/topic/game/" + room.getRoomId() + "/state", startPayload);
        }
    }

    /** 快速匹配：按 gameType 队列撮合 */
    public GameRoom quickMatch(Long uid, String gameType) {
        GameEngine engine = engineRegistry.get(gameType);
        if (engine == null) return null;
        if (userToRoom.containsKey(uid)) return null;
        int min = engine.minPlayers(gameType);
        PendingMatch pm = matchQueueByType.computeIfAbsent(gameType, k -> new PendingMatch());
        synchronized (pm) {
            if (pm.queue.contains(uid)) return null;
            pm.queue.add(uid);
            if (pm.queue.size() < min) return null;
            List<Long> picked = new ArrayList<>();
            for (int i = 0; i < min; i++) picked.add(pm.queue.poll());
            String roomId = "R" + UUID.randomUUID().toString().substring(0, 6);
            int cap = engine.maxPlayers(gameType);
            GameRoom room = new GameRoom(roomId, picked.get(0), null, gameType, cap);
            room.getPlayers().addAll(picked);
            rooms.put(roomId, room);
            for (Long p : picked) {
                userToRoom.put(p, roomId);
                User u = userService.findById(p);
                if (u != null) u.setInGameState("IN_GAME");
                room.getReadyPlayers().add(p);
            }
            broadcastRoster(room);
            startCountdown(room);
            return room;
        }
    }

    public boolean spectate(String roomId, Long uid) {
        GameRoom room = rooms.get(roomId);
        if (room == null) return false;
        if (!room.getSpectators().contains(uid)) room.getSpectators().add(uid);
        broadcastRoster(room);
        return true;
    }

    public void leaveRoom(Long uid) {
        String rid = userToRoom.remove(uid);
        if (rid == null) {
            for (PendingMatch pm : matchQueueByType.values()) pm.queue.remove(uid);
            return;
        }
        GameRoom room = rooms.get(rid);
        if (room == null) return;
        User u = userService.findById(uid);
        if (u != null) u.setInGameState("IDLE");

        String st = room.getState();
        if ("PLAYING".equals(st)) {
            GameEngine engine = engineRegistry.get(room.getGameType());
            if (engine != null) {
                ActionResult res = engine.onLeaveMidGame(room, uid);
                // 先结算再移除玩家：finishMatch 依赖完整的 players 列表判定败者，
                // 提前 remove 会导致败者列表为空、双方数据都不更新。
                res.success = true;
                handleActionResult(room, res);
                room.getPlayers().remove(uid);
            } else {
                dissolve(room);
            }
        } else if ("FINISHED".equals(st)) {
            // 结算后房间尚在延迟移除窗口，直接清理该玩家的映射即可
            room.getPlayers().remove(uid);
            userToRoom.remove(uid); // 已移除，幂等
        } else if ("WAITING".equals(st) || "READY".equals(st)) {
            room.getPlayers().remove(uid);
            room.getReadyPlayers().remove(uid);
            room.getSpectators().remove(uid);
            if (room.getPlayers().isEmpty()) {
                cancelTimer(rid);
                rooms.remove(rid);
            } else {
                if (uid.equals(room.getHostId())) room.setHostId(room.getPlayers().get(0));
                if ("READY".equals(st)) {
                    room.setState("WAITING");
                    room.setCountdownEndsAt(0);
                    cancelTimer(rid);
                    broadcastState(room, "back-to-waiting", "有玩家离开，回到等待");
                }
                broadcastRoster(room);
            }
        }
    }

    public void notifyDisconnect(Long uid) { leaveRoom(uid); }

    // ============ 动作分发 ============

    public ActionResult applyAction(String roomId, Long uid, Object action) {
        GameRoom room = rooms.get(roomId);
        ActionResult res = new ActionResult();
        if (room == null) { res.message = "房间不存在"; return res; }
        if (!"PLAYING".equals(room.getState())) { res.message = "对局未进行中"; return res; }
        GameEngine engine = engineRegistry.get(room.getGameType());
        if (engine == null) { res.message = "引擎未找到"; return res; }
        ActionResult r = engine.onAction(room, uid, action);
        handleActionResult(room, r);
        return r;
    }

    /**
     * @return 1=request recorded, 2=opponent accepted and match ended, negative=invalid request.
     */
    public synchronized int requestDraw(String roomId, Long uid) {
        GameRoom room = rooms.get(roomId);
        if (room == null || !"PLAYING".equals(room.getState()) || !room.hasPlayer(uid)) return -1;
        Object offered = room.getGameData().get("drawOfferBy");
        if (offered == null) {
            room.getGameData().put("drawOfferBy", uid);
            broadcastState(room, "draw-offered", "玩家 " + uid + " 请求平局，等待对手同意");
            return 1;
        }
        Long offeredBy;
        try { offeredBy = Long.valueOf(offered.toString()); } catch (Exception e) { return -1; }
        if (offeredBy.equals(uid)) return -2;
        room.getGameData().remove("drawOfferBy");
        finishMatch(room, null, true);
        return 2;
    }

    private void handleActionResult(GameRoom room, ActionResult res) {
        if (res == null) return;
        // 结算类结果（finished/timeout/forfeit）即使 success=false 也要推进，否则房间会卡死在 PLAYING。
        // success 仅用于回执是否需要给客户端发 game-error，不再决定是否广播/结算。
        if (!res.success && !res.finished) return;
        Map<String, Object> state = roomPublicView(room);
        state.put("event", res.event);
        state.put("message", res.message);
        if (res.extra != null) state.putAll(res.extra);
        messagingTemplate.convertAndSend("/topic/game/" + room.getRoomId() + "/state", state);

        if (res.scheduleTimeout && res.nextActionDeadline > 0) {
            scheduleActionTimeout(room, res.nextActionDeadline);
        }
        if (res.finished) {
            finishMatch(room, res.winnerId, res.draw);
        }
    }

    private void scheduleActionTimeout(GameRoom room, long timeoutMs) {
        cancelTimer(room.getRoomId());
        long deadline = System.currentTimeMillis() + timeoutMs;
        room.setActionDeadline(deadline);
        ScheduledFuture<?> f = scheduler.schedule(() -> {
            try { onActionTimeout(room.getRoomId()); }
            catch (Exception e) { e.printStackTrace(); }
        }, timeoutMs, TimeUnit.MILLISECONDS);
        roomTimers.put(room.getRoomId(), f);
    }

    private void onActionTimeout(String roomId) {
        GameRoom room = rooms.get(roomId);
        if (room == null || !"PLAYING".equals(room.getState())) return;
        GameEngine engine = engineRegistry.get(room.getGameType());
        if (engine == null) return;
        ActionResult res = engine.onTimeout(room);
        handleActionResult(room, res);
    }

    private void cancelTimer(String roomId) {
        ScheduledFuture<?> f = roomTimers.remove(roomId);
        if (f != null) f.cancel(false);
    }

    // ============ 结算（支持多人 FFA） ============

    private void finishMatch(GameRoom room, Long winnerId, boolean draw) {
        room.setState("FINISHED");
        room.setFinishedAt(System.currentTimeMillis());
        room.setWinnerId(winnerId);
        room.setDraw(draw);
        cancelTimer(room.getRoomId());

        List<Long> players = new ArrayList<>(room.getPlayers());
        if (winnerId != null) players.remove(winnerId);

        if (draw || winnerId == null) {
            int d = rankRuleService.drawDelta();
            for (int i = 0; i < players.size(); i++) {
                for (int j = i + 1; j < players.size(); j++) {
                    userService.updateMatchStats(players.get(i), players.get(j), true, d, d);
                }
            }
            writeMatchRecord(room, null, players, true);
        } else {
            User winner = userService.findById(winnerId);
            for (Long loserId : players) {
                User loser = userService.findById(loserId);
                int[] delta = rankRuleService.calcRankDelta(
                        winner != null ? winner.getRankPoints() : 0,
                        loser != null ? loser.getRankPoints() : 0);
                userService.updateMatchStats(winnerId, loserId, false, delta[0], delta[1]);
            }
            writeMatchRecord(room, winnerId, players, false);
        }

        Map<String, Object> fin = roomPublicView(room);
        fin.put("event", "finished");
        fin.put("winnerId", winnerId);
        fin.put("draw", draw);
        messagingTemplate.convertAndSend("/topic/game/" + room.getRoomId() + "/finished", fin);

        for (Long pid : room.getPlayers()) {
            User pu = userService.findById(pid);
            Map<String, Object> notice = new HashMap<>();
            notice.put("roomId", room.getRoomId());
            notice.put("result", pid.equals(winnerId) ? "WIN" : (draw ? "DRAW" : "LOSS"));
            notice.put("rankPoints", pu != null ? pu.getRankPoints() : 0);
            notice.put("gameType", room.getGameType());
            messagingTemplate.convertAndSendToUser(String.valueOf(pid), "/queue/game-finished", notice);
        }

        for (Long pid : room.getPlayers()) {
            User pu = userService.findById(pid);
            if (pu != null) pu.setInGameState("IDLE");
        }
        for (Long pid : room.getPlayers()) userToRoom.remove(pid);

        String rid = room.getRoomId();
        scheduler.schedule(() -> { rooms.remove(rid); },
                FINISH_DISMISS_MS, TimeUnit.MILLISECONDS);
    }

    private void writeMatchRecord(GameRoom room, Long winnerId, List<Long> losers, boolean draw) {
        try {
            MatchRecord rec = new MatchRecord();
            rec.setGameType(room.getGameType());
            rec.setPlayerAId(room.getPlayers().isEmpty() ? null : room.getPlayers().get(0));
            rec.setPlayerBId(room.getPlayers().size() > 1 ? room.getPlayers().get(1) : null);
            rec.setWinnerId(winnerId);
            rec.setResult(draw ? "DRAW" : (winnerId != null ? "WIN" : "DRAW"));
            rec.setBoardLog(objectMapper.writeValueAsString(room.getGameData()));
            rec.setCreatedAt(room.getCreatedAt());
            rec.setFinishedAt(room.getFinishedAt());
            matchService.save(rec);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void dissolve(GameRoom room) {
        cancelTimer(room.getRoomId());
        for (Long pid : room.getPlayers()) {
            userToRoom.remove(pid);
            User u = userService.findById(pid);
            if (u != null) u.setInGameState("IDLE");
        }
        rooms.remove(room.getRoomId());
    }

    // ============ 广播 ============

    private void broadcastState(GameRoom room, String event, String msg) {
        Map<String, Object> state = roomPublicView(room);
        state.put("event", event);
        state.put("message", msg);
        messagingTemplate.convertAndSend("/topic/game/" + room.getRoomId() + "/state", state);
    }

    private void broadcastRoster(GameRoom room) {
        Map<String, Object> roster = new HashMap<>();
        roster.put("event", "roster");
        roster.put("roomId", room.getRoomId());
        roster.put("state", room.getState());
        roster.put("gameType", room.getGameType());
        roster.put("hostId", room.getHostId());
        roster.put("capacity", room.getCapacity());
        List<Map<String, Object>> ps = new ArrayList<>();
        for (Long pid : room.getPlayers()) {
            User u = userService.findById(pid);
            Map<String, Object> p = new HashMap<>();
            p.put("id", pid);
            p.put("name", u != null ? u.getUsername() : "");
            p.put("rankPoints", u != null ? u.getRankPoints() : 0);
            p.put("ready", room.getReadyPlayers().contains(pid));
            ps.add(p);
        }
        roster.put("players", ps);
        roster.put("spectators", room.getSpectators());
        messagingTemplate.convertAndSend("/topic/game/" + room.getRoomId() + "/roster", roster);
    }

    private void broadcastReady(GameRoom room) {
        Map<String, Object> m = new HashMap<>();
        m.put("event", "ready");
        m.put("roomId", room.getRoomId());
        m.put("readyPlayers", new ArrayList<>(room.getReadyPlayers()));
        m.put("allReady", room.allReady());
        messagingTemplate.convertAndSend("/topic/game/" + room.getRoomId() + "/ready", m);
    }

    public Map<String, Object> roomPublicView(GameRoom room) {
        Map<String, Object> m = new HashMap<>();
        m.put("roomId", room.getRoomId());
        m.put("state", room.getState());
        m.put("gameType", room.getGameType());
        m.put("capacity", room.getCapacity());
        m.put("currentTurnPlayerId", room.getCurrentTurnPlayerId());
        m.put("winnerId", room.getWinnerId());
        m.put("draw", room.isDraw());
        m.put("players", room.getPlayers());
        m.put("readyPlayers", room.getReadyPlayers());
        m.put("spectators", room.getSpectators());
        m.put("hostId", room.getHostId());
        m.put("countdownEndsAt", room.getCountdownEndsAt());
        m.put("actionDeadline", room.getActionDeadline());
        m.put("createdAt", room.getCreatedAt());
        // Gomoku runs in a standalone page and must be able to recover the
        // complete visible state even when it missed an earlier WebSocket event.
        if ("GOMOKU".equals(room.getGameType())) {
            m.put("board", room.getGameData().get("board"));
            m.put("lastMove", room.getGameData().get("lastMove"));
            m.put("drawOfferBy", room.getGameData().get("drawOfferBy"));
        }
        List<Map<String, Object>> playerDetails = new ArrayList<>();
        for (Long playerId : room.getPlayers()) {
            User user = userService.findById(playerId);
            Map<String, Object> detail = new HashMap<>();
            detail.put("id", playerId);
            detail.put("name", user != null ? user.getUsername() : String.valueOf(playerId));
            detail.put("rankPoints", user != null ? user.getRankPoints() : 0);
            detail.put("ready", room.getReadyPlayers().contains(playerId));
            playerDetails.add(detail);
        }
        m.put("playerDetails", playerDetails);
        return m;
    }

    private long toLong(Object o) {
        if (o == null) return 0;
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0; }
    }

    private static class PendingMatch {
        final ConcurrentLinkedQueue<Long> queue = new ConcurrentLinkedQueue<>();
    }
}
