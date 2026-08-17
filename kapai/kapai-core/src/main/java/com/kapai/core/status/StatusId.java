package com.kapai.core.status;

import lombok.Getter;

/**
 * 状态标识枚举。集中定义所有 Buff/Debuff，便于在效果系统与遗物系统中按 id 查询。
 *
 * 使用枚举而非魔法字符串，保证类型安全且可全局检索。
 */
@Getter
public enum StatusId {
    // Debuff
    VULNERABLE("易伤", "受到的攻击伤害 ×1.5", true),
    WEAK("虚弱", "造成的攻击伤害 ×0.75", true),
    FRAIL("脆弱", "获得的格挡 ×0.75", true),

    // Buff
    STRENGTH("力量", "攻击伤害 +N", false),
    DEXTERITY("敏捷", "格挡 +N", false),
    REGEN("再生", "回合开始恢复 N 点生命", false),

    // 特殊
    BLOCK("格挡", "抵消等量受到的伤害", false),
    ARTIFACT("人工制品", "下一次受到负面状态时抵消并消耗一层", false);

    /** 是否为负面状态（用于人工制品判定、UI 颜色区分） */
    private final boolean debuff;

    @Getter private final String displayName;
    @Getter private final String description;

    StatusId(String displayName, String description, boolean debuff) {
        this.displayName = displayName;
        this.description = description;
        this.debuff = debuff;
    }
}
