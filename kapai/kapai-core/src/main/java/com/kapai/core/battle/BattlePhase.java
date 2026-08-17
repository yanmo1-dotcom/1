package com.kapai.core.battle;

/**
 * 战斗回合阶段。BattleManager 按此顺序驱动状态机：
 * PLAYER_TURN → END_PLAYER_TURN → ENEMY_INTENT → ENEMY_ACTION → END_ENEMY_TURN → PLAYER_TURN ...
 */
public enum BattlePhase {
    PLAYER_TURN,
    END_PLAYER_TURN,
    ENEMY_INTENT,
    ENEMY_ACTION,
    END_ENEMY_TURN,
    BATTLE_END
}
