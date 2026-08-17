package com.webchat.service.engine;

import com.webchat.model.GameRoom;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 五子棋引擎。15x15 棋盘，2人回合制，无出招超时。
 * 1=黑(seat0)，2=白(seat1)。胜负判定以落子点为中心检查 4 个方向连续 5 子。
 */
@Component
public class GomokuEngine implements GameEngine {

    private static final int SIZE = 15;
    private static final int WIN_LEN = 5;

    @Override public String gameType() { return "GOMOKU"; }
    @Override public int minPlayers(String gameType) { return 2; }
    @Override public int maxPlayers(String gameType) { return 2; }
    @Override public boolean hasActionTimeout(String gameType) { return false; }

    @Override
    public Map<String, Object> onMatchStart(GameRoom room) {
        int[][] board = new int[SIZE][SIZE];
        room.getGameData().put("board", board);
        // Seat 1 is white (mark=2), and white always moves first.
        int firstSeat = 1;
        Long firstId = room.getPlayers().get(firstSeat);
        room.setCurrentTurnPlayerId(firstId);
        room.getGameData().put("turn", 2);
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "match-start");
        payload.put("message", "五子棋对局开始，先手为玩家" + firstId);
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
        if (row < 0 || row >= SIZE || col < 0 || col >= SIZE) return res.fail("坐标非法");
        int[][] board = (int[][]) room.getGameData().get("board");
        if (board == null) {
            board = new int[SIZE][SIZE];
            room.getGameData().put("board", board);
        }
        if (board[row][col] != 0) return res.fail("该格已落子");

        int mark = room.seatOf(uid) == 0 ? 1 : 2;
        board[row][col] = mark;
        room.getGameData().put("lastMove", new int[]{row, col});
        res.success = true;
        res.extra.put("lastMove", new int[]{row, col});

        if (checkWin(board, row, col, mark)) {
            Long winnerId = room.getPlayers().get(mark == 1 ? 0 : 1);
            res.finished = true;
            res.winnerId = winnerId;
            res.message = "玩家" + winnerId + " 五子连珠获胜";
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
        res.success = true;
        res.finished = true;
        res.event = "forfeit";
        Long winner = room.getPlayers().stream()
                .filter(p -> !p.equals(uid) && p != null)
                .findFirst().orElse(null);
        res.winnerId = winner;
        res.message = "玩家" + uid + " 弃赛，" + (winner != null ? "玩家" + winner + " 获胜" : "无人获胜");
        return res;
    }

    /** 以落子点为中心，沿 4 个方向统计连续同色棋子数，任一方向 >=5 即胜 */
    private boolean checkWin(int[][] b, int row, int col, int mark) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int count = 1;
            count += countDir(b, row, col, d[0], d[1], mark);
            count += countDir(b, row, col, -d[0], -d[1], mark);
            if (count >= WIN_LEN) return true;
        }
        return false;
    }

    private int countDir(int[][] b, int row, int col, int dr, int dc, int mark) {
        int count = 0;
        int r = row + dr, c = col + dc;
        while (r >= 0 && r < SIZE && c >= 0 && c < SIZE && b[r][c] == mark) {
            count++;
            r += dr;
            c += dc;
        }
        return count;
    }

    private boolean isBoardFull(int[][] b) {
        for (int[] r : b) for (int v : r) if (v == 0) return false;
        return true;
    }
}
