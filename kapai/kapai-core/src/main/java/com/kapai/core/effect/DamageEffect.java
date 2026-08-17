package com.kapai.core.effect;

import com.kapai.core.creature.AbstractCreature;
import com.kapai.core.status.Status;
import com.kapai.core.status.StatusId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 伤害效果。
 *
 * 结算链：基础伤害 → +力量 → ×易伤(1.5) → ×虚弱(0.75)。
 * 力量作用于"攻击牌"，虚弱同样作用于攻击牌，故本效果按攻击伤害处理。
 * 易伤作用于受击方：若 target 处于易伤，最终值 ×1.5。
 *
 * 设计思路：所有 Buff 修正集中在 apply 内完成，使伤害来源透明可测试；
 * baseValue 来自卡牌配置，运行时由 BattleContext 传入。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class DamageEffect implements CardEffect {

    public static final String TYPE_ID = "DAMAGE";

    /** 基础伤害值（未修饰）。 */
    private int amount;

    @Override
    public void apply(BattleContext ctx) {
        AbstractCreature source = ctx.getSelf();
        AbstractCreature target = ctx.getTarget();
        if (source == null || target == null) {
            log.warn("DamageEffect 缺少 source 或 target，跳过");
            return;
        }

        int damage = ctx.getBaseValue() > 0 ? ctx.getBaseValue() : amount;

        // 1. 力量加成（攻击伤害 +N）
        int strength = source.statusAmount(StatusId.STRENGTH);
        damage += strength;

        // 2. 虚弱：来源造成的攻击伤害 ×0.75
        if (source.statusAmount(StatusId.WEAK) > 0) {
            damage = (int) (damage * 0.75);
        }

        // 3. 易伤：受击方受到的攻击伤害 ×1.5
        if (target.statusAmount(StatusId.VULNERABLE) > 0) {
            damage = (int) (damage * 1.5);
        }

        damage = Math.max(0, damage);
        log.debug("{} 对 {} 造成 {} 点伤害（基础={}）", source.getName(), target.getName(), damage, amount);
        target.takeDamage(damage);
    }

    @Override
    public String typeId() {
        return TYPE_ID;
    }
}
