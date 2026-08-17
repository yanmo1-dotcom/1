package com.kapai.core.creature;

import com.kapai.core.enums.CreatureType;
import com.kapai.core.status.Status;
import com.kapai.core.status.StatusId;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 玩家与怪物的通用基类。
 *
 * 设计思路：
 * - 生命值、能量、状态列表为可变战斗状态；最大生命值为上限约束。
 * - {@link #takeDamage(int)} 实现标准伤害结算链：先扣格挡，再扣生命，
 *   并在受击时触发"易伤"加成（由调用方或效果层计算最终值，此处负责承受）。
 * - {@link #applyStatus(Status)} 负责状态叠加，同 id 的状态层数累加而非替换。
 * - 不依赖任何 UI 类型，可被 core 独立测试与移植到 Web 端。
 */
@Data
@Slf4j
public abstract class AbstractCreature {

    /** 唯一标识，用于日志与目标定位。 */
    private final String id;

    /** 显示名。 */
    private final String name;

    /** 种类，影响规则判定。 */
    private final CreatureType type;

    /** 当前生命值。 */
    private int currentHp;

    /** 最大生命值。currentHp 不会超过此值。 */
    private int maxHp;

    /** 当前可用能量（仅玩家战斗中实际使用）。 */
    private int energy;

    /** 当前生效的状态列表。 */
    private final List<Status> statuses = new ArrayList<>();

    /** 是否已死亡。结算后由战斗管理器轮询清理。 */
    private boolean dead;

    protected AbstractCreature(String id, String name, CreatureType type, int maxHp, int energy) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.energy = energy;
    }

    // ===== 生命与伤害 =====

    /**
     * 承受伤害：先抵扣格挡，剩余部分扣除生命值。
     *
     * @param amount 已经过 Buff 修饰后的最终伤害值（非负）
     */
    public void takeDamage(int amount) {
        if (dead || amount <= 0) {
            return;
        }
        int remaining = consumeBlock(amount);
        if (remaining > 0) {
            currentHp = Math.max(0, currentHp - remaining);
            log.debug("{} 受到 {} 点伤害，剩余 HP={}", name, remaining, currentHp);
        }
        if (currentHp <= 0) {
            dead = true;
            log.info("{} 已死亡", name);
        }
    }

    /**
     * 消耗格挡抵伤，返回未被格挡的伤害。
     */
    private int consumeBlock(int amount) {
        Optional<Status> block = findStatus(StatusId.BLOCK);
        if (block.isEmpty() || block.get().getAmount() <= 0) {
            return amount;
        }
        int blockAmount = block.get().getAmount();
        int absorbed = Math.min(blockAmount, amount);
        block.get().setAmount(blockAmount - absorbed);
        log.debug("{} 格挡抵消 {} 点伤害", name, absorbed);
        return amount - absorbed;
    }

    /** 直接治疗（不超过最大生命）。 */
    public void heal(int amount) {
        if (dead || amount <= 0) {
            return;
        }
        currentHp = Math.min(maxHp, currentHp + amount);
        log.debug("{} 恢复 {} 点生命，当前 HP={}", name, amount, currentHp);
    }

    // ===== 状态管理 =====

    /**
     * 施加状态：同 id 叠加层数，新状态标记 justApplied。
     * 若目标拥有"人工制品"且本次为负面状态，则消耗一层抵消。
     */
    public void applyStatus(Status status) {
        if (status == null || status.getAmount() == 0) {
            return;
        }
        // 人工制品抵消负面状态
        if (status.getId().isDebuff() && consumeArtifact()) {
            log.debug("{} 的【人工制品】抵消了 {}", name, status.getId().getDisplayName());
            return;
        }
        Optional<Status> existing = findStatus(status.getId());
        if (existing.isPresent()) {
            existing.get().stack(status.getAmount());
        } else {
            statuses.add(new Status(status.getId(), status.getAmount(), true));
        }
        log.debug("{} 获得 {} {} 层", name, status.getId().getDisplayName(), status.getAmount());
    }

    /** 消耗一层人工制品，成功返回 true。 */
    private boolean consumeArtifact() {
        Optional<Status> artifact = findStatus(StatusId.ARTIFACT);
        if (artifact.isEmpty() || artifact.get().getAmount() <= 0) {
            return false;
        }
        artifact.get().setAmount(artifact.get().getAmount() - 1);
        return true;
    }

    /** 查询某状态的当前层数（无则 0）。 */
    public int statusAmount(StatusId id) {
        return findStatus(id).map(Status::getAmount).orElse(0);
    }

    private Optional<Status> findStatus(StatusId id) {
        return statuses.stream().filter(s -> s.getId() == id).findFirst();
    }

    /** 移除层数耗尽的状态。回合结算时调用。 */
    public void purgeExpiredStatuses() {
        statuses.removeIf(s -> s.getAmount() <= 0);
    }

    /** 回合结束时对衰减类状态减层（易伤/虚弱/脆弱等）。 */
    public void decayDecrementalStatuses() {
        for (Status s : statuses) {
            if (s.isJustApplied()) {
                s.setJustApplied(false); // 本回合施加的不衰减
            } else if (isDecremental(s.getId())) {
                s.setAmount(Math.max(0, s.getAmount() - 1));
            }
        }
        purgeExpiredStatuses();
    }

    private boolean isDecremental(StatusId id) {
        return id == StatusId.VULNERABLE || id == StatusId.WEAK || id == StatusId.FRAIL;
    }

    /** 获得格挡（受"脆弱"影响 ×0.75）。 */
    public void gainBlock(int amount) {
        if (amount <= 0) {
            return;
        }
        int actual = amount;
        if (statusAmount(StatusId.FRAIL) > 0) {
            actual = (int) (amount * 0.75);
        }
        Optional<Status> block = findStatus(StatusId.BLOCK);
        if (block.isPresent()) {
            block.get().setAmount(block.get().getAmount() + actual);
        } else {
            statuses.add(new Status(StatusId.BLOCK, actual, false));
        }
        log.debug("{} 获得 {} 点格挡", name, actual);
    }
}
