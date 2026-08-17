package com.webchat.controller;

import com.webchat.TokenManager;
import com.webchat.model.GameRoom;
import com.webchat.model.MatchRecord;
import com.webchat.model.RankRule;
import com.webchat.model.User;
import com.webchat.service.MatchService;
import com.webchat.service.RankRuleService;
import com.webchat.service.RoomService;
import com.webchat.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/game")
public class GameController {

    @Autowired private UserService userService;
    @Autowired private RoomService roomService;
    @Autowired private MatchService matchService;
    @Autowired private RankRuleService rankRuleService;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    @Value("${admin.password:default_admin_pass}")
    private String adminPassword;

    // ============ 房间 ============

    @PostMapping("/rooms")
    public Map<String, Object> createRoom(@RequestBody(required = false) Map<String, String> body,
                                           @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        if (isBanned(uid)) { r.put("code", 403); r.put("msg", "账号已封禁"); return r; }
        String password = body != null ? body.get("password") : null;
        String gameType = body != null ? body.get("gameType") : null;
        if (gameType == null || gameType.isEmpty()) gameType = "TIC_TAC_TOE";
        GameRoom room = roomService.createRoom(uid, gameType, password);
        if (room == null) { r.put("code", 400); r.put("msg", "不支持的游戏类型"); return r; }
        r.put("code", 200); r.put("msg", "房间已创建");
        r.put("data", roomService.roomPublicView(room));
        return r;
    }

    @GetMapping("/rooms")
    public Map<String, Object> listRooms(@RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        List<Map<String, Object>> data = new ArrayList<>();
        for (GameRoom room : roomService.getOpenRooms()) {
            Map<String, Object> m = new HashMap<>();
            m.put("roomId", room.getRoomId());
            m.put("hostId", room.getHostId());
            User host = userService.findById(room.getHostId());
            m.put("hostName", host != null ? host.getUsername() : "");
            m.put("rankPoints", host != null ? host.getRankPoints() : 0);
            m.put("seated", room.getPlayers().size());
            m.put("capacity", room.getCapacity());
            m.put("locked", room.getPassword() != null && !room.getPassword().isEmpty());
            m.put("gameType", room.getGameType());
            m.put("readyCount", room.getReadyPlayers().size());
            data.add(m);
        }
        r.put("code", 200); r.put("data", data);
        return r;
    }

    @PostMapping("/rooms/{roomId}/join")
    public Map<String, Object> joinRoom(@PathVariable String roomId,
                                         @RequestBody(required = false) Map<String, String> body,
                                         @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        if (isBanned(uid)) { r.put("code", 403); r.put("msg", "账号已封禁"); return r; }
        String password = body != null ? body.get("password") : null;
        GameRoom room = roomService.joinRoom(roomId, uid, password);
        if (room == null) { r.put("code", 400); r.put("msg", "加入失败（房间不存在/已满/密码错/已在其它房）"); return r; }
        r.put("code", 200); r.put("msg", "加入成功");
        r.put("data", roomService.roomPublicView(room));
        return r;
    }

    @PostMapping("/match")
    public Map<String, Object> quickMatch(@RequestBody(required = false) Map<String, String> body,
                                          @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        if (isBanned(uid)) { r.put("code", 403); r.put("msg", "账号已封禁"); return r; }
        String gameType = body != null ? body.get("gameType") : null;
        if (gameType == null || gameType.isEmpty()) gameType = "TIC_TAC_TOE";
        GameRoom room = roomService.quickMatch(uid, gameType);
        if (room == null) {
            r.put("code", 202); r.put("msg", "已进入匹配队列，等待对手");
            return r;
        }
        r.put("code", 200); r.put("msg", "匹配成功");
        r.put("data", roomService.roomPublicView(room));
        // 推送匹配成功通知给双方
        for (Long pid : room.getPlayers()) {
            Map<String, Object> notice = new HashMap<>();
            notice.put("roomId", room.getRoomId());
            messagingTemplate.convertAndSendToUser(String.valueOf(pid), "/queue/game-matched", notice);
        }
        return r;
    }

    @PostMapping("/rooms/{roomId}/ready")
    public Map<String, Object> setReady(@PathVariable String roomId,
                                         @RequestBody(required = false) Map<String, Object> body,
                                         @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        boolean ready = body == null || !"false".equals(String.valueOf(body.get("ready")));
        GameRoom room = roomService.setReady(roomId, uid, ready);
        if (room != null) {
            r.put("code", 200); r.put("msg", ready ? "已准备" : "已取消准备");
        } else {
            r.put("code", 400); r.put("msg", "操作失败（房间不存在/已锁定/非玩家）");
        }
        return r;
    }

    @PostMapping("/rooms/{roomId}/spectate")
    public Map<String, Object> spectate(@PathVariable String roomId,
                                         @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        if (roomService.spectate(roomId, uid)) {
            r.put("code", 200); r.put("msg", "观战成功");
            r.put("data", roomService.roomPublicView(roomService.getRoom(roomId)));
        } else {
            r.put("code", 400); r.put("msg", "房间不存在");
        }
        return r;
    }

    @PostMapping("/rooms/{roomId}/leave")
    public Map<String, Object> leaveRoom(@PathVariable String roomId,
                                          @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        roomService.leaveRoom(uid);
        r.put("code", 200); r.put("msg", "已离开");
        return r;
    }

    @GetMapping("/rooms/{roomId}")
    public Map<String, Object> getRoom(@PathVariable String roomId,
                                        @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) { r.put("code", 404); r.put("msg", "房间不存在"); return r; }
        r.put("code", 200); r.put("data", roomService.roomPublicView(room));
        return r;
    }

    /** Standalone Gomoku page fallback; it must not depend on the opener window's WebSocket. */
    @PostMapping("/rooms/{roomId}/gomoku-move")
    public Map<String, Object> gomokuMove(@PathVariable String roomId,
                                          @RequestBody Map<String, Object> body,
                                          @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        GameRoom room = roomService.getRoom(roomId);
        if (room == null) { r.put("code", 404); r.put("msg", "房间不存在"); return r; }
        if (!"GOMOKU".equals(room.getGameType()) || !room.hasPlayer(uid)) {
            r.put("code", 403); r.put("msg", "无权操作该五子棋房间"); return r;
        }
        try {
            int row = Integer.parseInt(body.get("row").toString());
            int col = Integer.parseInt(body.get("col").toString());
            com.webchat.service.engine.ActionResult result = roomService.applyAction(roomId, uid, new int[]{row, col});
            if (!result.success) { r.put("code", 400); r.put("msg", result.message); return r; }
            r.put("code", 200);
            GameRoom updated = roomService.getRoom(roomId);
            if (updated != null) r.put("data", roomService.roomPublicView(updated));
            return r;
        } catch (Exception e) {
            r.put("code", 400); r.put("msg", "落子坐标无效"); return r;
        }
    }

    @PostMapping("/rooms/{roomId}/draw")
    public Map<String, Object> requestDraw(@PathVariable String roomId,
                                            @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        int result = roomService.requestDraw(roomId, uid);
        if (result == 1) { r.put("code", 200); r.put("msg", "已请求平局，等待对手同意"); }
        else if (result == 2) { r.put("code", 200); r.put("msg", "双方已同意，平局结束"); }
        else if (result == -2) { r.put("code", 409); r.put("msg", "你已经请求平局，请等待对手同意"); }
        else { r.put("code", 400); r.put("msg", "当前无法请求平局"); }
        GameRoom room = roomService.getRoom(roomId);
        if (room != null) r.put("data", roomService.roomPublicView(room));
        return r;
    }

    // ============ 战绩与档案 ============

    @GetMapping("/history")
    public Map<String, Object> myHistory(@RequestParam(defaultValue = "50") int limit,
                                          @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        List<Map<String, Object>> data = new ArrayList<>();
        for (MatchRecord rec : matchService.getRecent(uid, limit)) {
            data.add(toHistoryView(rec, uid));
        }
        r.put("code", 200); r.put("data", data);
        return r;
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(@RequestParam(required = false) Long userId,
                                         @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        Long target = userId != null ? userId : uid;
        User u = userService.findById(target);
        if (u == null) { r.put("code", 404); r.put("msg", "用户不存在"); return r; }
        Map<String, Object> data = new HashMap<>();
        data.put("id", u.getId());
        data.put("username", u.getUsername());
        data.put("nickname", u.getNickname());
        data.put("rankPoints", u.getRankPoints());
        data.put("wins", u.getWins());
        data.put("losses", u.getLosses());
        data.put("draws", u.getDraws());
        data.put("winRate", Math.round(u.getWinRate() * 10) / 10.0);
        data.put("currentStreak", u.getCurrentStreak());
        data.put("status", u.getStatus());
        data.put("inGameState", u.getInGameState());
        data.put("registeredAt", u.getRegisteredAt());
        data.put("lastLoginAt", u.getLastLoginAt());
        r.put("code", 200); r.put("data", data);
        return r;
    }

    private Map<String, Object> toHistoryView(MatchRecord rec, Long viewerId) {
        Map<String, Object> m = new HashMap<>();
        Long opponentId = viewerId.equals(rec.getPlayerAId()) ? rec.getPlayerBId() : rec.getPlayerAId();
        User opp = userService.findById(opponentId);
        int myDelta = viewerId.equals(rec.getPlayerAId()) ? rec.getRankDeltaA() : rec.getRankDeltaB();
        String result;
        if (rec.getWinnerId() == null) result = "DRAW";
        else result = viewerId.equals(rec.getWinnerId()) ? "WIN" : "LOSS";
        m.put("id", rec.getId());
        m.put("gameType", rec.getGameType());
        m.put("opponentId", opponentId);
        m.put("opponentName", opp != null ? opp.getUsername() : "");
        m.put("result", result);
        m.put("rankDelta", myDelta);
        m.put("boardLog", rec.getBoardLog());
        m.put("finishedAt", rec.getFinishedAt());
        return m;
    }

    // ============ 积分规则（管理员热改） ============

    @GetMapping("/rules")
    public Map<String, Object> getRules(@RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        r.put("code", 200); r.put("data", rankRuleService.getRules());
        return r;
    }

    @PostMapping("/rules")
    public Map<String, Object> updateRules(@RequestBody Map<String, Object> body,
                                            @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        String inputKey = (String) body.get("adminKey");
        if (inputKey == null || !inputKey.equals(adminPassword)) {
            r.put("code", 403); r.put("msg", "管理员密码错误"); return r;
        }
        RankRule rule = new RankRule();
        rule.setWinPoints(intVal(body.get("winPoints"), 15));
        rule.setLossPoints(intVal(body.get("lossPoints"), -10));
        rule.setDrawPoints(intVal(body.get("drawPoints"), 2));
        rule.setDiffThreshold(intVal(body.get("diffThreshold"), 200));
        rule.setUpsetBonus(intVal(body.get("upsetBonus"), 5));
        rule.setMinPoints(intVal(body.get("minPoints"), 0));
        rule.setInitialPoints(intVal(body.get("initialPoints"), 0));
        rankRuleService.updateRules(rule);
        r.put("code", 200); r.put("msg", "规则已更新，立即生效（无需重启）");
        r.put("data", rule);
        return r;
    }

    // ============ 禁言/封号（管理员） ============

    @PostMapping("/admin/status")
    public Map<String, Object> setStatus(@RequestBody Map<String, Object> body,
                                          @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        String inputKey = (String) body.get("adminKey");
        if (inputKey == null || !inputKey.equals(adminPassword)) {
            r.put("code", 403); r.put("msg", "管理员密码错误"); return r;
        }
        String username = (String) body.get("username");
        String status = (String) body.get("status"); // ACTIVE / MUTED / BANNED
        if (status == null || (!status.equals("ACTIVE") && !status.equals("MUTED") && !status.equals("BANNED"))) {
            r.put("code", 400); r.put("msg", "状态非法"); return r;
        }
        boolean ok = userService.setStatusByUsername(username, status);
        if (ok) {
            r.put("code", 200); r.put("msg", "状态已更新: " + status);
        } else {
            r.put("code", 404); r.put("msg", "用户不存在");
        }
        return r;
    }

    private boolean isBanned(Long uid) {
        User u = userService.findById(uid);
        return u != null && "BANNED".equals(u.getStatus());
    }

    private int intVal(Object o, int def) {
        if (o == null) return def;
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return def; }
    }
}
