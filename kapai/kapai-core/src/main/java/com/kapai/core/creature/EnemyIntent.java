package com.kapai.core.creature;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 怪物意图：本回合怪物将执行的动作预览。
 *
 * 设计思路：玩家回合结束时由怪物 AI 生成，玩家可据此决策出牌。
 * 类型用枚举，数值统一放 damage（格挡意图则 amount 视为格挡量）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnemyIntent {

    public enum Kind {
        ATTACK,        // 攻击
        DEFEND,        // 格挡
        ATTACK_DEFEND, // 攻击+格挡
        BUFF,          // 自我增益
        DEBUFF,        // 对玩家减益
        UNKNOWN        // 未知（部分怪物隐藏意图）
    }

    private Kind kind;
    private int damage;
    private int amount;
}
