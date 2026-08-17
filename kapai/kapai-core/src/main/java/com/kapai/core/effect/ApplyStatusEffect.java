package com.kapai.core.effect;

import com.kapai.core.creature.AbstractCreature;
import com.kapai.core.status.Status;
import com.kapai.core.status.StatusId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 施加状态效果。作用于 target（默认为目标生物）。
 * 配合 JSON 的 statusId + amount 描述，如"给予敌人 2 层易伤"。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class ApplyStatusEffect implements CardEffect {

    public static final String TYPE_ID = "APPLY_STATUS";

    private String statusId;
    private int amount;
    /** true=作用于自身，false=作用于目标。 */
    private boolean toSelf;

    @Override
    public void apply(BattleContext ctx) {
        StatusId id;
        try {
            id = StatusId.valueOf(statusId);
        } catch (IllegalArgumentException e) {
            log.warn("未知状态 id={}，跳过 ApplyStatusEffect", statusId);
            return;
        }
        AbstractCreature dest = toSelf ? ctx.getSelf() : ctx.getTarget();
        if (dest == null) {
            return;
        }
        dest.applyStatus(new Status(id, amount));
    }

    @Override
    public String typeId() {
        return TYPE_ID;
    }
}
