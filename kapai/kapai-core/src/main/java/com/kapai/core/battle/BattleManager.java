package com.kapai.core.battle;

import com.kapai.core.card.AbstractCard;
import com.kapai.core.creature.AbstractCreature;
import com.kapai.core.creature.Enemy;
import com.kapai.core.creature.EnemyIntent;
import com.kapai.core.creature.Player;
import com.kapai.core.effect.BattleContext;
import com.kapai.core.effect.CardEffect;
import com.kapai.core.effect.DamageEffect;
import com.kapai.core.relic.RelicListener;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 战斗管理器：核心回合制流程编排。
 *
 * 回合流程（状态机）：
 *   PLAYER_TURN（玩家出牌）
 *   → endPlayerTurn()：弃手牌、衰减状态、广播 onTurnEnd
 *   → ENEMY_INTENT：生成怪物意图
 *   → ENEMY_ACTION：执行怪物意图
 *   → endEnemyTurn()：玩家能量重置、抽牌、广播 onTurnStart
 *
 * 设计思路：
 * - 不依赖 UI，纯逻辑驱动；UI 层订阅阶段变化渲染。
 * - 遗物监听器以列表持有，事件广播采用"快照遍历"防止回调中增删导致 ConcurrentModification。
 * - playCard 把 drawer 注入 BattleContext，使抽牌类效果能回调本类的抽牌能力。
 */
@Data
@Slf4j
public class BattleManager {

    private final Player player;
    private final List<Enemy> enemies;
    private final CardPiles piles;

    private BattlePhase phase = BattlePhase.PLAYER_TURN;
    private int turnCount = 0;
    private int playerEnergyPerTurn = 3;

    /** 遗物/其他观察者。 */
    private final List<RelicListener> relicListeners = new ArrayList<>();

    /** 阶段变更钩子，供 UI 层订阅刷新。 */
    private final List<Consumer<BattlePhase>> phaseListeners = new ArrayList<>();

    public BattleManager(Player player, List<Enemy> enemies, List<AbstractCard> deck) {
        this.player = player;
        this.enemies = new ArrayList<>(enemies);
        this.piles = new CardPiles();
        this.piles.initDrawPile(deck);
    }

    // ===== 战斗生命周期 =====

    public void startBattle() {
        turnCount = 0;
        notify(RelicListener::onBattleStart);
        startPlayerTurn();
    }

    /** 玩家回合开始：重置能量、抽 5 张、广播 onTurnStart。 */
    public void startPlayerTurn() {
        turnCount++;
        phase = BattlePhase.PLAYER_TURN;
        player.setEnergy(playerEnergyPerTurn);
        piles.draw(5);
        log.info("—— 第 {} 回合（玩家）开始，能量={} ——", turnCount, player.getEnergy());
        notify(l -> l.onTurnStart(player));
        firePhaseChange();
    }

    /**
     * 玩家打出一张牌。
     *
     * @param card   手牌中的卡牌
     * @param target 目标生物，AOE/自身可为 null
     */
    public void playCard(AbstractCard card, AbstractCreature target) {
        if (phase != BattlePhase.PLAYER_TURN) {
            log.warn("非玩家回合，无法打牌");
            return;
        }
        if (!card.canPlay(player)) {
            log.warn("能量不足或不可打出：{}（cost={}, 能量={}）", card.getName(), card.getCost(), player.getEnergy());
            return;
        }
        card.payCost(player);
        piles.removeFromHand(card);

        // 为该卡的每个效果注入 drawer，使 DrawCardEffect 能回调抽牌
        for (CardEffect effect : card.getEffects()) {
            BattleContext ctx = BattleContext.builder()
                    .self(player)
                    .target(target)
                    .baseValue(baseValueOf(effect))
                    .drawer(piles::draw)
                    .build();
            effect.apply(ctx);
            if (effect instanceof DamageEffect && target != null) {
                notify(l -> l.onDamageDealt(player, target, ((DamageEffect) effect).getAmount()));
            }
        }

        notify(l -> l.onCardPlayed(card, player, target));
        piles.discard(card);

        checkBattleEnd();
    }

    private int baseValueOf(CardEffect effect) {
        if (effect instanceof DamageEffect d) {
            return d.getAmount();
        }
        if (effect instanceof com.kapai.core.effect.BlockEffect b) {
            return b.getAmount();
        }
        return 0;
    }

    /** 玩家主动结束回合。 */
    public void endPlayerTurn() {
        phase = BattlePhase.END_PLAYER_TURN;
        firePhaseChange();
        piles.discardHand();
        player.decayDecrementalStatuses();
        player.purgeExpiredStatuses();
        notify(l -> l.onTurnEnd(player));
        enemyIntentPhase();
    }

    // ===== 怪物回合 =====

    private void enemyIntentPhase() {
        phase = BattlePhase.ENEMY_INTENT;
        for (Enemy e : enemies) {
            if (!e.isDead()) {
                e.setIntent(generateIntent(e));
            }
        }
        log.info("怪物意图已生成");
        firePhaseChange();
        enemyActionPhase();
    }

    private EnemyIntent generateIntent(Enemy e) {
        // 简化 AI：默认攻击，伤害=力量+基础值。真实游戏按怪物脚本配置。
        int base = 6 + e.statusAmount(com.kapai.core.status.StatusId.STRENGTH);
        return new EnemyIntent(EnemyIntent.Kind.ATTACK, base, base);
    }

    private void enemyActionPhase() {
        phase = BattlePhase.ENEMY_ACTION;
        firePhaseChange();
        for (Enemy e : enemies) {
            if (e.isDead()) {
                continue;
            }
            executeEnemyIntent(e);
        }
        checkBattleEnd();
        endEnemyTurn();
    }

    private void executeEnemyIntent(Enemy e) {
        EnemyIntent intent = e.getIntent();
        if (intent == null) {
            return;
        }
        switch (intent.getKind()) {
            case ATTACK, ATTACK_DEFEND -> {
                int dmg = intent.getDamage();
                player.takeDamage(dmg);
                notify(l -> l.onDamageTaken(player, dmg));
                log.info("{} 攻击玩家，造成 {} 伤害", e.getName(), dmg);
            }
            case DEFEND -> e.gainBlock(intent.getAmount());
            default -> log.debug("{} 执行意图 {}", e.getName(), intent.getKind());
        }
    }

    private void endEnemyTurn() {
        phase = BattlePhase.END_ENEMY_TURN;
        for (Enemy e : enemies) {
            if (!e.isDead()) {
                e.decayDecrementalStatuses();
                e.purgeExpiredStatuses();
            }
        }
        firePhaseChange();
        if (!checkBattleEnd()) {
            startPlayerTurn();
        }
    }

    // ===== 结算 =====

    /** 返回 true 表示战斗已结束。 */
    private boolean checkBattleEnd() {
        if (player.isDead()) {
            phase = BattlePhase.BATTLE_END;
            log.info("玩家阵亡，战斗失败");
            notify(l -> l.onBattleEnd(false));
            firePhaseChange();
            return true;
        }
        if (enemies.stream().allMatch(AbstractCreature::isDead)) {
            phase = BattlePhase.BATTLE_END;
            log.info("所有敌人死亡，战斗胜利");
            notify(l -> l.onBattleEnd(true));
            firePhaseChange();
            return true;
        }
        return false;
    }

    // ===== 观察者广播 =====

    /** 注册遗物/监听器。 */
    public void addRelicListener(RelicListener listener) {
        relicListeners.add(listener);
    }

    private void notify(java.util.function.Consumer<RelicListener> action) {
        // 快照遍历，避免回调中增删列表抛 ConcurrentModificationException
        for (RelicListener l : new ArrayList<>(relicListeners)) {
            action.accept(l);
        }
    }

    public void addPhaseListener(Consumer<BattlePhase> listener) {
        phaseListeners.add(listener);
    }

    private void firePhaseChange() {
        for (Consumer<BattlePhase> l : phaseListeners) {
            l.accept(phase);
        }
    }

    /** 仅存活敌人。 */
    public List<Enemy> aliveEnemies() {
        List<Enemy> alive = new ArrayList<>();
        for (Enemy e : enemies) {
            if (!e.isDead()) {
                alive.add(e);
            }
        }
        return alive;
    }
}
