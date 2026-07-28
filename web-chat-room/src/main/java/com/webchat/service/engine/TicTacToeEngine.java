package com.webchat.service.engine;

import com.webchat.model.GameRoom;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 井字棋引擎（保留原有逻辑）。2人回合制，无出招超时。
 */
@Component
public class TicTacToeEngine implements GameEngine {

    @Override public String gameType() { return "TIC_TAC_TOE"; }
    @Override public int minPlayers(String gameType) { return 2; }
    @Override public int maxPlayers(String gameType) { return 2; }
    @Override public boolean hasActionTimeout(String gameType) { return false; }

    @Override
    public Map<String, Object> onMatchStart(GameRoom room) {
        // 初始化棋盘 3x3 全 0
        int[][] board = new int[][]{{0,0,0},{0,0,0},{0,0,0}};
        room.getGameData().put("board", board);
        // 随机选先手
        int firstSeat = (System.identityHashCode(room) & 1) == 0 ? 0 : 1;
        Long firstId = room.getPlayers().get(firstSeat);
        room.setCurrentTurnPlayerId(firstId);
        room.getGameData().put("turn", 1);
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "match-start");
        payload.put("message", "对局开始，先手为玩家" + firstId);
        payload.put("firstPlayerId", firstId);
        return payload;
    }

    @Override
    public ActionResult onAction(GameRoom room, Long uid, Object action) {
        ActionResult res = new ActionResult();
        res.event = "action";
        if (!room.hasPlayer(uid)) return res.fail("你不是该局玩家");
        if (!room.isPlayerTurn(uid)) return res.fail("还没轮到你");
        if (!(action instanceof int[])) return res.fail("动作格式错误");
        int[] rc = (int[]) action;
        int row = rc[0], col = rc[1];
        if (row < 0 || row > 2 || col < 0 || col > 2) return res.fail("坐标非法");
        int[][] board = (int[][]) room.getGameData().get("board");
        if (board == null) {
            board = new int[][]{{0,0,0},{0,0,0},{0,0,0}};
            room.getGameData().put("board", board);
        }
        if (board[row][col] != 0) return res.fail("该格已落子");

        int mark = room.seatOf(uid) == 0 ? 1 : 2;
        board[row][col] = mark;
        res.success = true;

        int winnerMark = checkWinner(board);
        if (winnerMark != 0) {
            Long winnerId = room.getPlayers().get(winnerMark == 1 ? 0 : 1);
            res.finished = true;
            res.winnerId = winnerId;
            res.message = "玩家" + winnerId + " 获胜";
            res.extra.put("board", board);
            return res;
        }
        if (isBoardFull(board)) {
            res.finished = true;
            res.draw = true;
            res.message = "平局";
            res.extra.put("board", board);
            return res;
        }
        // 切换轮次
        int turn = (int) room.getGameData().getOrDefault("turn", 1);
        turn = (turn == 1) ? 2 : 1;
        room.getGameData().put("turn", turn);
        Long next = room.getPlayers().get(turn == 1 ? 0 : 1);
        room.setCurrentTurnPlayerId(next);
        res.message = "落子成功";
        res.extra.put("board", board);
        return res;
    }

    @Override
    public ActionResult onTimeout(GameRoom room) {
        // 井字棋回合制超时：当前轮到的玩家判负
        ActionResult res = new ActionResult();
        res.success = true;
        res.finished = true;
        res.event = "finished";
        Long current = room.getCurrentTurnPlayerId();
        Long winner = room.getPlayers().stream()
                .filter(p -> !p.equals(current) && p != null).findFirst().orElse(null);
        res.winnerId = winner;
        res.message = "玩家" + current + " 超时未落子，" + (winner != null ? "玩家" + winner + " 获胜" : "无人获胜");
        return res;
    }

    @Override
    public ActionResult onLeaveMidGame(GameRoom room, Long uid) {
        ActionResult res = new ActionResult();
        res.success = true; // 弃赛必须推进结算
        res.finished = true;
        res.event = "forfeit";
        Long winner = room.getPlayers().stream()
                .filter(p -> !p.equals(uid) && p != null)
                .findFirst().orElse(null);
        res.winnerId = winner;
        res.message = "玩家" + uid + " 弃赛，" + (winner != null ? "玩家" + winner + " 获胜" : "无人获胜");
        return res;
    }

    private int checkWinner(int[][] b) {
        int[][] lines = {
                {b[0][0], b[0][1], b[0][2]}, {b[1][0], b[1][1], b[1][2]}, {b[2][0], b[2][1], b[2][2]},
                {b[0][0], b[1][0], b[2][0]}, {b[0][1], b[1][1], b[2][1]}, {b[0][2], b[1][2], b[2][2]},
                {b[0][0], b[1][1], b[2][2]}, {b[0][2], b[1][1], b[2][0]}
        };
        for (int[] l : lines) {
            if (l[0] != 0 && l[0] == l[1] && l[1] == l[2]) return l[0];
        }
        return 0;
    }

    private boolean isBoardFull(int[][] b) {
        for (int[] r : b) for (int v : r) if (v == 0) return false;
        return true;
    }
}
