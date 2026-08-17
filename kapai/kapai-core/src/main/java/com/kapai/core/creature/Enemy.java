package com.kapai.core.creature;

import com.kapai.core.enums.CreatureType;
import lombok.Getter;
import lombok.Setter;

/**
 * 怪物生物。携带本回合意图（intent），由 BattleManager 在玩家回合结束后生成。
 */
@Getter
@Setter
public class Enemy extends AbstractCreature {

    private EnemyIntent intent;

    public Enemy(String id, String name, int maxHp) {
        super(id, name, CreatureType.ENEMY, maxHp, 0);
    }
}
