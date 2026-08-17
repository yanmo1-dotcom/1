package com.kapai.core.card;

import com.kapai.core.creature.AbstractCreature;
import com.kapai.core.effect.CardEffect;
import com.kapai.core.enums.CardRarity;
import com.kapai.core.enums.CardTarget;
import com.kapai.core.enums.CardType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡牌抽象基类。
 *
 * 设计思路：
 * - 卡牌的"静态描述"（id/name/cost/rarity/type/target/effects）与"战斗实例状态"
 *   分离：静态部分由数据层从 JSON 装配，effects 用策略模式组合。
 * - {@link #use(AbstractCreature, AbstractCreature)} 作为模板方法，子类可覆盖以实现
 *   特殊打出逻辑，默认实现遍历 effects 依次执行。
 * - 不持有任何 UI 资源（贴图/字体），表现层另行封装 CardView。
 *
 * 注意：本类非 final，供传统硬编码卡牌子类继承；数据驱动卡牌可直接用 {@link DataCard}。
 */
@Data
@Slf4j
public abstract class AbstractCard {

    protected final String id;
    protected final String name;
    protected int cost;
    protected final CardRarity rarity;
    protected final CardType type;
    protected CardTarget target;

    /** 该牌的效果列表；硬编码子类可在构造时 add，数据驱动卡牌由 JSON 装配。 */
    protected final List<CardEffect> effects = new ArrayList<>();

    protected AbstractCard(String id, String name, int cost, CardRarity rarity, CardType type, CardTarget target) {
        this.id = id;
        this.name = name;
        this.cost = cost;
        this.rarity = rarity;
        this.type = type;
        this.target = target;
    }

    /**
     * 打出卡牌。子类一般无需覆盖；如需特殊流程（如多次复制、消耗）可重写。
     *
     * @param user   打出者
     * @param target 目标生物，AOE/自身牌可为 null
     */
    public abstract void use(AbstractCreature user, AbstractCreature target);

    /**
     * 默认执行链：依次 apply 所有效果。
     * 子类的 use() 可调用此方法完成标准结算。
     */
    protected void executeEffects(AbstractCreature user, AbstractCreature target) {
        for (CardEffect effect : effects) {
            var ctx = com.kapai.core.effect.BattleContext.builder()
                    .self(user)
                    .target(target)
                    .baseValue(0)
                    .build();
            effect.apply(ctx);
            log.debug("卡牌 {} 执行效果 {}", name, effect.typeId());
        }
    }

    /** 是否可被打出（默认按费用判断，子类可扩展消耗/手牌上限等规则）。 */
    public boolean canPlay(AbstractCreature user) {
        return user.getEnergy() >= cost;
    }

    /** 扣除费用。 */
    public void payCost(AbstractCreature user) {
        user.setEnergy(user.getEnergy() - cost);
    }
}
