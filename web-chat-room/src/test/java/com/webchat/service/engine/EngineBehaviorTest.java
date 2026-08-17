package com.webchat.service.engine;

import com.webchat.model.GameRoom;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EngineBehaviorTest {

    @Test
    void actionResultFluentMethodsShouldSetSuccessAndMessage() {
        ActionResult result = new ActionResult().ok("ok").fail("nope");

        assertFalse(result.success);
        assertEquals("nope", result.message);
    }

    @Test
    void gameEngineRegistryShouldResolveRegisteredEngine() {
        GameEngine engine = new TicTacToeEngine();
        GameEngineRegistry registry = new GameEngineRegistry(List.of(engine));

        assertSame(engine, registry.get("TIC_TAC_TOE"));
        assertTrue(registry.exists("TIC_TAC_TOE"));
        assertNull(registry.get("UNKNOWN"));
    }

    @Test
    void ticTacToeEngineShouldFinishWhenPlayerCreatesWinningLine() {
        TicTacToeEngine engine = new TicTacToeEngine();
        GameRoom room = new GameRoom();
        room.setPlayers(new ArrayList<>(List.of(1L, 2L)));
        room.setCurrentTurnPlayerId(1L);
        room.setGameData(new HashMap<>());

        int[][] board = new int[][]{{1, 1, 0}, {0, 2, 0}, {0, 0, 0}};
        room.getGameData().put("board", board);
        room.getGameData().put("turn", 1);

        ActionResult result = engine.onAction(room, 1L, new int[]{0, 2});

        assertTrue(result.success);
        assertTrue(result.finished);
        assertEquals(1L, result.winnerId);
    }

    @Test
    void ticTacToeEngineShouldRejectOccupiedCell() {
        TicTacToeEngine engine = new TicTacToeEngine();
        GameRoom room = new GameRoom();
        room.setPlayers(new ArrayList<>(List.of(1L, 2L)));
        room.setCurrentTurnPlayerId(1L);
        room.setGameData(new HashMap<>());
        int[][] board = new int[][]{{1, 0, 0}, {0, 0, 0}, {0, 0, 0}};
        room.getGameData().put("board", board);

        ActionResult result = engine.onAction(room, 1L, new int[]{0, 0});

        assertFalse(result.success);
        assertEquals("该格已落子", result.message);
    }

    @Test
    void guessNumberEngineShouldRecordGuessAndSwitchTurn() {
        GuessNumberEngine engine = new GuessNumberEngine();
        GameRoom room = new GameRoom();
        room.setPlayers(new ArrayList<>(List.of(1L, 2L)));
        room.setCurrentTurnPlayerId(1L);
        room.setGameData(new HashMap<>());
        room.getGameData().put("target", 50);
        room.getGameData().put("guessHistory", new ArrayList<Map<String, Object>>());

        ActionResult result = engine.onAction(room, 1L, "40");

        assertTrue(result.success);
        assertEquals("小了", result.extra.get("hint"));
        assertEquals(2L, room.getCurrentTurnPlayerId());
        assertTrue(result.scheduleTimeout);
    }

    @Test
    void guessNumberEngineShouldFinishOnCorrectGuess() {
        GuessNumberEngine engine = new GuessNumberEngine();
        GameRoom room = new GameRoom();
        room.setPlayers(new ArrayList<>(List.of(1L, 2L)));
        room.setCurrentTurnPlayerId(1L);
        room.setGameData(new HashMap<>());
        room.getGameData().put("target", 50);
        room.getGameData().put("guessHistory", new ArrayList<Map<String, Object>>());

        ActionResult result = engine.onAction(room, 1L, "50");

        assertTrue(result.success);
        assertTrue(result.finished);
        assertEquals(1L, result.winnerId);
        assertEquals("命中", ((List<Map<String, Object>>) room.getGameData().get("guessHistory")).get(0).get("hint"));
    }

    @Test
    void rpsEngineShouldResolveOneOnOneWinner() {
        RpsEngine engine = new RpsEngine();
        GameRoom room = new GameRoom();
        room.setPlayers(new ArrayList<>(List.of(1L, 2L)));
        room.setGameType("RPS_1V1");
        room.setGameData(new HashMap<>());
        room.getGameData().put("moves", new HashMap<Long, String>());

        ActionResult first = engine.onAction(room, 1L, "ROCK");
        ActionResult second = engine.onAction(room, 2L, "SCISSORS");

        assertTrue(first.success);
        assertTrue(second.finished);
        assertEquals(1L, second.winnerId);
    }

    @Test
    void rpsEngineShouldRejectInvalidMove() {
        RpsEngine engine = new RpsEngine();
        GameRoom room = new GameRoom();
        room.setPlayers(new ArrayList<>(List.of(1L, 2L)));
        room.setGameType("RPS_1V1");
        room.setGameData(new HashMap<>());

        ActionResult result = engine.onAction(room, 1L, "invalid");

        assertFalse(result.success);
        assertEquals("出拳非法（ROCK/PAPER/SCISSORS）", result.message);
    }

    @Test
    void gomokuEngineShouldFinishWhenFiveInRowArePlaced() {
        GomokuEngine engine = new GomokuEngine();
        GameRoom room = new GameRoom();
        room.setPlayers(new ArrayList<>(List.of(1L, 2L)));
        room.setCurrentTurnPlayerId(1L);
        room.setGameData(new HashMap<>());
        int[][] board = new int[15][15];
        board[0][0] = 1;
        board[1][0] = 1;
        board[2][0] = 1;
        board[3][0] = 1;
        room.getGameData().put("board", board);
        room.getGameData().put("turn", 1);

        ActionResult win = engine.onAction(room, 1L, new int[]{4, 0});

        assertTrue(win.finished);
        assertEquals(1L, win.winnerId);
        assertTrue(win.success);
    }

    @Test
    void gomokuWhiteShouldMoveFirstAndPlayersMustAlternate() {
        GomokuEngine engine = new GomokuEngine();
        GameRoom room = new GameRoom();
        room.setPlayers(new ArrayList<>(List.of(1L, 2L))); // seat 0 black, seat 1 white
        room.setGameData(new HashMap<>());

        engine.onMatchStart(room);
        assertEquals(2L, room.getCurrentTurnPlayerId());

        ActionResult white = engine.onAction(room, 2L, new int[]{7, 7});
        assertTrue(white.success);
        assertEquals(1L, room.getCurrentTurnPlayerId());

        ActionResult secondWhiteMove = engine.onAction(room, 2L, new int[]{7, 8});
        assertFalse(secondWhiteMove.success);

        ActionResult black = engine.onAction(room, 1L, new int[]{8, 7});
        assertTrue(black.success);
        assertEquals(2L, room.getCurrentTurnPlayerId());
    }
}
