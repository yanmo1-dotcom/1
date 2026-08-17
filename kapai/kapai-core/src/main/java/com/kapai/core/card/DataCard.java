package com.kapai.core.card;

import com.kapai.core.creature.AbstractCreature;
import com.kapai.core.effect.BattleContext;
import com.kapai.core.effect.CardEffect;
import com.kapai.core.enums.CardRarity;
import com.kapai.core.enums.CardTarget;
import com.kapai.core.enums.CardType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 数据驱动卡牌：完全由 JSON 装配的卡牌实例。
 *
 * 与硬编码子类并列存在——传统特殊卡牌仍可继承 {@link AbstractCard} 重写 use()，
 * 而大多数数值型卡牌由本类承载，配合数据层实现"改 JSON 即改卡"。
 * use() 实现：构造 BattleContext 并依次执行 effects，baseValue 取第一个伤害/格挡
 * 效果的 amount 作为代表值（精确的多效果数值由各自 amount 承担）。
 */
@Slf4j
public class DataCard extends AbstractCard {

    public DataCard(String id, String name, int cost, CardRarity rarity, CardType type, CardTarget target,
                    List<CardEffect> effects) {
        super(id, name, cost, rarity, type, target);
        if (effects != null) {
            this.effects.addAll(effects);
        }
    }

    @Override
    public void use(AbstractCreature user, AbstractCreature target) {
        for (CardEffect effect : effects) {
            BattleContext ctx = BattleContext.builder()
                    .self(user)
                    .target(target)
                    .baseValue(baseValueOf(effect))
                    .drawer(null) // 由 BattleManager 在打牌时注入；见 BattleManager.playCard
                    .build();
            effect.apply(ctx);
        }
    }

    /** 取效果自身 amount 作为 baseValue，供效果内部统一引用。 */
    private int baseValueOf(CardEffect effect) {
        if (effect instanceof com.kapai.core.effect.DamageEffect d) {
            return d.getAmount();
        }
        if (effect instanceof com.kapai.core.effect.BlockEffect b) {
            return b.getAmount();
        }
        return 0;
    }
}
