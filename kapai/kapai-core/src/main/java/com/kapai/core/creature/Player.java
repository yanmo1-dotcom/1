package com.kapai.core.creature;

import com.kapai.core.enums.CreatureType;

/**
 * 玩家生物。能量在战斗中由 BattleManager 重置。
 */
public class Player extends AbstractCreature {

    public Player(String id, String name, int maxHp) {
        super(id, name, CreatureType.PLAYER, maxHp, 3);
    }
}
