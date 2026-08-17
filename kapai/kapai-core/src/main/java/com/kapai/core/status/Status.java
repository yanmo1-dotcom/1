package com.kapai.core.status;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 状态/Buff 实体。
 *
 * 设计思路：用一个 {@link StatusId} 枚举标识种类，配合 amount 描述层数/数值，
 * 避免为每种 Buff 都建一个子类（数据驱动）。{@code justApplied} 标记本回合新施加，
 * 用于"回合结束时减少一层"前的回合内生效判定。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Status {

    private StatusId id;
    private int amount;
    /** 是否为本回合刚施加（回合结束结算时用于避免立即衰减） */
    private boolean justApplied;

    public Status(StatusId id, int amount) {
        this(id, amount, true);
    }

    /** 叠加：同 id 状态层数相加（部分状态可自定义叠加规则，此处保留默认实现）。 */
    public void stack(int extra) {
        this.amount += extra;
        this.justApplied = true;
    }
}
