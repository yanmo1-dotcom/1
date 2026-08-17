package com.kapai.ui;

import com.kapai.core.enums.CardRarity;
import com.kapai.core.enums.CardTarget;
import com.kapai.core.enums.CardType;
import com.kapai.core.enums.CreatureType;

import java.util.HashMap;
import java.util.Map;

/**
 * 枚举中文翻译表。集中把 core 层枚举映射为界面显示文案，
 * 避免在渲染代码里散落 switch。新增枚举值只需补一条映射。
 */
public final class I18n {

    private static final Map<CardType, String> CARD_TYPE = new HashMap<>();
    private static final Map<CardRarity, String> CARD_RARITY = new HashMap<>();
    private static final Map<CardTarget, String> CARD_TARGET = new HashMap<>();
    private static final Map<CreatureType, String> CREATURE_TYPE = new HashMap<>();

    static {
        CARD_TYPE.put(CardType.ATTACK, "攻击");
        CARD_TYPE.put(CardType.SKILL, "技能");
        CARD_TYPE.put(CardType.POWER, "能力");
        CARD_TYPE.put(CardType.CURSE, "诅咒");
        CARD_TYPE.put(CardType.STATUS, "状态");

        CARD_RARITY.put(CardRarity.BASIC, "基础");
        CARD_RARITY.put(CardRarity.COMMON, "普通");
        CARD_RARITY.put(CardRarity.UNCOMMON, "罕见");
        CARD_RARITY.put(CardRarity.RARE, "稀有");
        CARD_RARITY.put(CardRarity.SPECIAL, "特殊");

        CARD_TARGET.put(CardTarget.SELF, "自身");
        CARD_TARGET.put(CardTarget.SINGLE, "单体");
        CARD_TARGET.put(CardTarget.ALL_ENEMIES, "全体敌人");
        CARD_TARGET.put(CardTarget.NONE, "无目标");

        CREATURE_TYPE.put(CreatureType.PLAYER, "玩家");
        CREATURE_TYPE.put(CreatureType.ENEMY, "敌人");
        CREATURE_TYPE.put(CreatureType.NEUTRAL, "中立");
    }

    public static String type(CardType t) { return CARD_TYPE.getOrDefault(t, t.name()); }
    public static String rarity(CardRarity r) { return CARD_RARITY.getOrDefault(r, r.name()); }
    public static String target(CardTarget t) { return CARD_TARGET.getOrDefault(t, t.name()); }
    public static String creature(CreatureType c) { return CREATURE_TYPE.getOrDefault(c, c.name()); }

    /** 战斗阶段中文。 */
    public static String phase(com.kapai.core.battle.BattlePhase p) {
        return switch (p) {
            case PLAYER_TURN -> "玩家回合";
            case END_PLAYER_TURN -> "玩家回合结束";
            case ENEMY_INTENT -> "敌人意图";
            case ENEMY_ACTION -> "敌人行动";
            case END_ENEMY_TURN -> "敌人回合结束";
            case BATTLE_END -> "战斗结束";
        };
    }

    /** 怪物意图中文。 */
    public static String intentKind(com.kapai.core.creature.EnemyIntent.Kind k) {
        return switch (k) {
            case ATTACK -> "攻击";
            case DEFEND -> "防御";
            case ATTACK_DEFEND -> "攻击+防御";
            case BUFF -> "增益";
            case DEBUFF -> "减益";
            case UNKNOWN -> "未知";
        };
    }

    /** 卡牌效果类型中文简述（用于手牌卡面）。 */
    public static String effectType(String typeId) {
        return switch (typeId) {
            case "DAMAGE" -> "伤害";
            case "BLOCK" -> "格挡";
            case "DRAW" -> "抽牌";
            case "APPLY_STATUS" -> "状态";
            default -> typeId;
        };
    }

    /** 状态 id 中文。 */
    public static String status(String statusId) {
        try {
            return com.kapai.core.status.StatusId.valueOf(statusId).getDisplayName();
        } catch (IllegalArgumentException e) {
            return statusId;
        }
    }

    private I18n() {
    }
}
