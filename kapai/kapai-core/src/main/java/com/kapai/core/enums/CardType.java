package com.kapai.core.enums;

/**
 * 卡牌类型。区分卡牌在战斗中的作用类别，影响 UI 表现与规则判定。
 */
public enum CardType {
    ATTACK,   // 攻击牌：通常造成伤害，受"力量"加成
    SKILL,    // 技能牌：格挡、抽牌、增益等
    POWER,    // 能力牌：打出后永久生效的被动
    CURSE,    // 诅咒牌：负面效果
    STATUS    // 状态牌：异常状态生成的牌
}
