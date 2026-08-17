package com.webchat.service.engine;

import com.webchat.model.GameRoom;

import java.util.Map;

/**
 * 可插拔游戏引擎接口。每种对战游戏实现一个。
 * 服务端权威：所有随机数/胜负判定在此完成，客户端只提交动作。
 */
public interface GameEngine {

    String gameType();

    /** 该引擎支持哪些游戏类型（默认仅 gameType()）。RPS 引擎覆盖以支持 1v1 与 FFA。 */
    default boolean supports(String type) { return gameType().equals(type); }

    int minPlayers(String gameType);
    int maxPlayers(String gameType);

    /** 是否需要出招倒计时（如 RPS/猜数）。井字棋回合制可设 false。 */
    boolean hasActionTimeout(String gameType);

    /** 锁定后初始化游戏专属状态，返回首阶段广播 payload（含 event/message）。 */
    Map<String, Object> onMatchStart(GameRoom room);

    /**
     * 处理玩家动作。action 由调用方解析后的负载（如 RPS 的 move 字符串、猜数的 int、井字棋的 row/col）。
     * 返回 ActionResult，RoomService 据此广播/结算。
     */
    ActionResult onAction(GameRoom room, Long uid, Object action);

    /** 超时裁决（actionDeadline 到期） */
    ActionResult onTimeout(GameRoom room);

    /** 玩家中途离开对局的弃赛裁决 */
    ActionResult onLeaveMidGame(GameRoom room, Long uid);
}
