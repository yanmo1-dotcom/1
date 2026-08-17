package com.webchat.service.engine;

import com.webchat.model.GameRoom;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 猜数字引擎（轮流猜）。2人。
 * 服务端随机生成 1-100 的目标数，双方轮流猜，引擎提示"大了/小了/命中"，
 * 命中者胜，未命中换人。限时提交，超时判负。
 */
@Component
public class GuessNumberEngine implements GameEngine {

    public static final long ACTION_TIMEOUT_MS = 20000;
    public static final int MIN = 1, MAX = 100;

    @Override public String gameType() { return "GUESS_NUMBER"; }
    @Override public int minPlayers(String gameType) { return 2; }
    @Override public int maxPlayers(String gameType) { return 2; }
    @Override public boolean hasActionTimeout(String gameType) { return true; }

    @Override
    public Map<String, Object> onMatchStart(GameRoom room) {
        // 服务端权威随机数 1-100
        long nano = System.nanoTime();
        int hash = Math.abs((int)(nano ^ (nano >>> 32) ^ System.identityHashCode(room)));
        int target = MIN + (hash % (MAX - MIN + 1));
        room.getGameData().put("target", target);
        room.getGameData().put("guessHistory", new ArrayList<Map<String, Object>>());
        // 随机选先猜者
        int firstSeat = (System.identityHashCode(room) & 1) == 0 ? 0 : 1;
        Long first = room.getPlayers().get(firstSeat);
        room.setCurrentTurnPlayerId(first);
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "phase");
        payload.put("message", "猜数字开始！目标在 " + MIN + "-" + MAX + "，玩家" + first + " 先猜");
        payload.put("currentTurnPlayerId", first);
        payload.put("rangeMin", MIN);
        payload.put("rangeMax", MAX);
        payload.put("deadline", ACTION_TIMEOUT_MS);
        return payload;
    }

    @Override
    public ActionResult onAction(GameRoom room, Long uid, Object action) {
        ActionResult res = new ActionResult();
        res.event = "action";
        if (!room.hasPlayer(uid)) return res.fail("你不是该局玩家");
        if (!room.isPlayerTurn(uid)) return res.fail("还没轮到你猜");
        int guess;
        try { guess = Integer.parseInt(action.toString()); }
        catch (Exception e) { return res.fail("请输入数字"); }
        int target = (int) room.getGameData().get("target");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) room.getGameData().get("guessHistory");
        Map<String, Object> entry = new HashMap<>();
        entry.put("playerId", uid);
        entry.put("guess", guess);
        String hint;
        if (guess == target) {
            entry.put("hint", "命中");
            history.add(entry);
            res.success = true;
            res.finished = true;
            res.winnerId = uid;
            res.event = "finished";
            res.message = "玩家" + uid + " 猜中目标 " + target + "，获胜！";
            res.extra.put("target", target);
            res.extra.put("history", history);
            return res;
        } else if (guess < target) {
            hint = "小了";
        } else {
            hint = "大了";
        }
        entry.put("hint", hint);
        history.add(entry);
        res.success = true;
        res.extra.put("history", history);
        res.extra.put("lastGuess", guess);
        res.extra.put("hint", hint);
        // 换人
        Long next = room.getPlayers().stream().filter(p -> !p.equals(uid)).findFirst().orElse(uid);
        room.setCurrentTurnPlayerId(next);
        res.message = "玩家" + uid + " 猜 " + guess + "，" + hint + "！轮到玩家" + next;
        res.scheduleTimeout = true;
        res.nextActionDeadline = ACTION_TIMEOUT_MS;
        return res;
    }

    @Override
    public ActionResult onTimeout(GameRoom room) {
        ActionResult res = new ActionResult();
        res.success = true; // 超时裁决必须推进结算
        Long current = room.getCurrentTurnPlayerId();
        Long other = room.getPlayers().stream().filter(p -> !p.equals(current) && p != null).findFirst().orElse(null);
        int target = (int) room.getGameData().getOrDefault("target", 0);
        res.finished = true;
        res.winnerId = other;
        res.message = "玩家" + current + " 超时未猜，玩家" + other + " 获胜（答案是" + target + "）";
        res.extra.put("target", target);
        res.event = "finished";
        return res;
    }

    @Override
    public ActionResult onLeaveMidGame(GameRoom room, Long uid) {
        ActionResult res = new ActionResult();
        res.success = true; // 弃赛必须推进结算
        res.finished = true;
        res.event = "forfeit";
        Long winner = room.getPlayers().stream().filter(p -> !p.equals(uid) && p != null).findFirst().orElse(null);
        res.winnerId = winner;
        int target = (int) room.getGameData().getOrDefault("target", 0);
        res.message = "玩家" + uid + " 弃赛，玩家" + winner + " 获胜（答案是" + target + "）";
        res.extra.put("target", target);
        return res;
    }
}
