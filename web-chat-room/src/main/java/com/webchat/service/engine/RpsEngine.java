package com.webchat.service.engine;

import com.webchat.model.GameRoom;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * 石头剪刀布引擎，支持两种模式：
 *  - RPS_1V1：2人同时出拳，裁决；平局则重出（最多3轮后强制判平）。
 *  - RPS_FFA：3-6人擂台淘汰赛。每轮取「擂主 vs 挑战者」两人出拳，胜者成新擂主，
 *    挑战者轮换为下一存活玩家，直到剩1人夺冠。最后统一结算（冠军胜，其余败）。
 * 服务端权威：出拳在裁决前对对手不可见，裁决时才公开。
 */
@Component
public class RpsEngine implements GameEngine {

    public static final long ACTION_TIMEOUT_MS = 15000;

    @Override public String gameType() { return "RPS_1V1"; }

    @Override
    public boolean supports(String type) {
        return "RPS_1V1".equals(type) || "RPS_FFA".equals(type);
    }

    @Override public int minPlayers(String type) { return "RPS_FFA".equals(type) ? 3 : 2; }
    @Override public int maxPlayers(String type) { return "RPS_FFA".equals(type) ? 6 : 2; }
    @Override public boolean hasActionTimeout(String type) { return true; }

    @Override
    public Map<String, Object> onMatchStart(GameRoom room) {
        Map<String, Object> gd = room.getGameData();
        Map<String, Object> payload = new HashMap<>();
        if ("RPS_FFA".equals(room.getGameType())) {
            // 擂台初始化：存活队列=全部玩家，擂主=队首，挑战者=队次
            LinkedList<Long> alive = new LinkedList<>(room.getPlayers());
            gd.put("alive", alive);
            gd.put("champion", alive.poll());
            gd.put("challenger", alive.poll());
            gd.put("moves", new HashMap<Long, String>());
            gd.put("round", 1);
            payload.put("event", "phase");
            payload.put("message", "擂台赛开始！擂主 " + gd.get("champion") + " VS 挑战者 " + gd.get("challenger") + "，请出拳");
            payload.put("champion", gd.get("champion"));
            payload.put("challenger", gd.get("challenger"));
        } else {
            gd.put("moves", new HashMap<Long, String>());
            gd.put("round", 1);
            payload.put("event", "phase");
            payload.put("message", "出拳阶段，请出拳（石头/剪刀/布）");
        }
        payload.put("deadline", ACTION_TIMEOUT_MS);
        return payload;
    }

    @Override
    public ActionResult onAction(GameRoom room, Long uid, Object action) {
        ActionResult res = new ActionResult();
        res.event = "action";
        if (!room.hasPlayer(uid)) return res.fail("你不是该局玩家");
        String move = action == null ? null : action.toString().toUpperCase();
        if (!isValidMove(move)) return res.fail("出拳非法（ROCK/PAPER/SCISSORS）");
        @SuppressWarnings("unchecked")
        Map<Long, String> moves = (Map<Long, String>) room.getGameData().get("moves");
        if (moves == null) { moves = new HashMap<>(); room.getGameData().put("moves", moves); }
        if (moves.containsKey(uid)) return res.fail("你已出拳，等待对手");
        moves.put(uid, move);
        res.success = true;
        res.message = "已出拳，等待裁决";

        if ("RPS_FFA".equals(room.getGameType())) {
            // 擂台：两人都出拳才裁决
            Long champ = (Long) room.getGameData().get("champion");
            Long chall = (Long) room.getGameData().get("challenger");
            if (champ != null && chall != null && moves.containsKey(champ) && moves.containsKey(chall)) {
                return resolveArenaRound(room, champ, chall, moves, res);
            }
        } else {
            // 1v1：双方都出拳才裁决
            if (moves.size() >= room.getPlayers().size()) {
                return resolve1v1(room, moves, res);
            }
        }
        return res;
    }

    private ActionResult resolve1v1(GameRoom room, Map<Long, String> moves, ActionResult res) {
        Long a = room.getPlayers().get(0);
        Long b = room.getPlayers().get(1);
        String ma = moves.get(a), mb = moves.get(b);
        int cmp = compare(ma, mb);
        res.extra.put("moves", new HashMap<>(moves));
        if (cmp == 0) {
            // 平局重出
            int round = (int) room.getGameData().getOrDefault("round", 1);
            if (round >= 3) {
                res.finished = true;
                res.draw = true;
                res.winnerId = null;
                res.message = "连续3轮平局，判定平局";
                res.event = "finished";
                return res;
            }
            room.getGameData().put("round", round + 1);
            room.getGameData().put("moves", new HashMap<Long, String>());
            res.event = "phase";
            res.message = "平局！重出（第" + (round + 1) + "轮）";
            res.scheduleTimeout = true;
            res.nextActionDeadline = ACTION_TIMEOUT_MS;
            return res;
        }
        res.finished = true;
        res.winnerId = cmp > 0 ? a : b;
        res.message = (cmp > 0 ? a : b) + " 获胜";
        res.event = "finished";
        return res;
    }

    private ActionResult resolveArenaRound(GameRoom room, Long champ, Long chall, Map<Long, String> moves, ActionResult res) {
        String mc = moves.get(champ), mh = moves.get(chall);
        int cmp = compare(mc, mh);
        res.extra.put("moves", new HashMap<>(moves));
        @SuppressWarnings("unchecked")
        LinkedList<Long> alive = (LinkedList<Long>) room.getGameData().get("alive");

        if (cmp == 0) {
            // 平局，本轮重出
            room.getGameData().put("moves", new HashMap<Long, String>());
            int round = (int) room.getGameData().getOrDefault("round", 1) + 1;
            room.getGameData().put("round", round);
            res.event = "phase";
            res.message = "平局！擂主 " + champ + " VS 挑战者 " + chall + " 重出";
            res.scheduleTimeout = true;
            res.nextActionDeadline = ACTION_TIMEOUT_MS;
            return res;
        }
        Long winner = cmp > 0 ? champ : chall;
        Long loser = cmp > 0 ? chall : champ;
        // 淘汰败者
        if (alive == null) alive = new LinkedList<>();
        alive.remove(loser);
        // 胜者为新擂主，挑战者=下一存活
        room.getGameData().put("champion", winner);
        if (alive.isEmpty()) {
            // 只剩擂主，夺冠
            res.finished = true;
            res.winnerId = winner;
            res.message = "玩家" + winner + " 擂台夺冠！淘汰 " + loser;
            res.event = "finished";
            res.extra.put("eliminatedId", loser);
            res.extra.put("finalChampion", winner);
            return res;
        }
        Long nextChall = alive.poll();
        room.getGameData().put("challenger", nextChall);
        room.getGameData().put("moves", new HashMap<Long, String>());
        int round = (int) room.getGameData().getOrDefault("round", 1) + 1;
        room.getGameData().put("round", round);
        res.event = "phase";
        res.message = "玩家" + loser + " 被淘汰！擂主 " + winner + " VS 新挑战者 " + nextChall;
        res.extra.put("eliminatedId", loser);
        res.scheduleTimeout = true;
        res.nextActionDeadline = ACTION_TIMEOUT_MS;
        return res;
    }

    @Override
    public ActionResult onTimeout(GameRoom room) {
        ActionResult res = new ActionResult();
        res.event = "timeout";
        @SuppressWarnings("unchecked")
        Map<Long, String> moves = (Map<Long, String>) room.getGameData().get("moves");
        if (moves == null) moves = new HashMap<>();

        if ("RPS_FFA".equals(room.getGameType())) {
            Long champ = (Long) room.getGameData().get("champion");
            Long chall = (Long) room.getGameData().get("challenger");
            // 未出拳者判负；双方都没出则擂主守擂
            if (moves.containsKey(champ) && !moves.containsKey(chall)) {
                return arenaLose(room, chall, champ, "超时未出拳", res);
            } else if (moves.containsKey(chall) && !moves.containsKey(champ)) {
                return arenaLose(room, champ, chall, "超时未出拳", res);
            } else if (!moves.containsKey(champ) && !moves.containsKey(chall)) {
                // 双方都超时，挑战者判负
                return arenaLose(room, chall, champ, "双方超时，擂主守擂", res);
            }
        } else {
            Long a = room.getPlayers().get(0);
            Long b = room.getPlayers().get(1);
            if (moves.containsKey(a) && !moves.containsKey(b)) {
                res.success = true; res.finished = true; res.winnerId = a; res.event = "finished";
                res.message = "玩家" + b + " 超时判负";
                return res;
            } else if (moves.containsKey(b) && !moves.containsKey(a)) {
                res.success = true; res.finished = true; res.winnerId = b; res.event = "finished";
                res.message = "玩家" + a + " 超时判负";
                return res;
            } else {
                res.success = true; res.finished = true; res.draw = true; res.event = "finished";
                res.message = "双方均超时，判定平局";
                return res;
            }
        }
        // 兜底：异常状态判平局，避免房间卡死
        res.success = true; res.finished = true; res.draw = true; res.event = "finished";
        res.message = "超时异常，判定平局";
        return res;
    }

    private ActionResult arenaLose(GameRoom room, Long loser, Long winner, String reason, ActionResult res) {
        @SuppressWarnings("unchecked")
        LinkedList<Long> alive = (LinkedList<Long>) room.getGameData().get("alive");
        if (alive != null) alive.remove(loser);
        room.getGameData().put("champion", winner);
        res.success = true; // 弃赛/超时裁决必须推进结算
        if (alive == null || alive.isEmpty()) {
            res.finished = true; res.winnerId = winner; res.event = "finished";
            res.message = "玩家" + loser + " " + reason + "，玩家" + winner + " 擂台夺冠！";
            res.extra.put("eliminatedId", loser);
            res.extra.put("finalChampion", winner);
            return res;
        }
        Long nextChall = alive.poll();
        room.getGameData().put("challenger", nextChall);
        room.getGameData().put("moves", new HashMap<Long, String>());
        res.event = "phase";
        res.message = "玩家" + loser + " " + reason + "被淘汰！擂主 " + winner + " VS 新挑战者 " + nextChall;
        res.extra.put("eliminatedId", loser);
        res.scheduleTimeout = true;
        res.nextActionDeadline = ACTION_TIMEOUT_MS;
        return res;
    }

    @Override
    public ActionResult onLeaveMidGame(GameRoom room, Long uid) {
        ActionResult res = new ActionResult();
        res.event = "forfeit";
        res.success = true; // 弃赛必须推进结算
        if ("RPS_FFA".equals(room.getGameType())) {
            @SuppressWarnings("unchecked")
            LinkedList<Long> alive = (LinkedList<Long>) room.getGameData().get("alive");
            Long champ = (Long) room.getGameData().get("champion");
            Long chall = (Long) room.getGameData().get("challenger");
            if (alive != null) alive.remove(uid);
            // 若离开者是擂主，挑战者升擂主；否则只移出挑战者
            Long winner;
            if (uid.equals(champ)) {
                winner = chall;
            } else if (uid.equals(chall)) {
                winner = champ;
            } else {
                winner = champ;
            }
            room.getGameData().put("champion", winner);
            if (alive == null || alive.isEmpty()) {
                res.finished = true; res.winnerId = winner; res.event = "finished";
                res.message = "玩家" + uid + " 弃赛，玩家" + winner + " 擂台夺冠！";
                res.extra.put("eliminatedId", uid);
                res.extra.put("finalChampion", winner);
                return res;
            }
            Long nextChall = alive.poll();
            room.getGameData().put("challenger", nextChall);
            room.getGameData().put("moves", new HashMap<Long, String>());
            res.event = "phase";
            res.message = "玩家" + uid + " 弃赛被淘汰！擂主 " + winner + " VS 新挑战者 " + nextChall;
            res.extra.put("eliminatedId", uid);
            res.scheduleTimeout = true;
            res.nextActionDeadline = ACTION_TIMEOUT_MS;
            return res;
        }
        // 1v1 弃赛
        Long winner = room.getPlayers().stream().filter(p -> !p.equals(uid) && p != null).findFirst().orElse(null);
        res.finished = true;
        res.winnerId = winner;
        res.message = "玩家" + uid + " 弃赛，" + (winner != null ? "玩家" + winner + " 获胜" : "");
        return res;
    }

    private boolean isValidMove(String m) {
        return "ROCK".equals(m) || "PAPER".equals(m) || "SCISSORS".equals(m);
    }

    /** a vs b：>0 a胜，<0 b胜，0 平 */
    private int compare(String a, String b) {
        if (a.equals(b)) return 0;
        if ((a.equals("ROCK") && b.equals("SCISSORS"))
                || (a.equals("SCISSORS") && b.equals("PAPER"))
                || (a.equals("PAPER") && b.equals("ROCK"))) return 1;
        return -1;
    }
}
