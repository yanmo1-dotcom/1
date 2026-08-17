package com.kapai.data;

import com.kapai.core.battle.BattleManager;
import com.kapai.core.battle.BattlePhase;
import com.kapai.core.card.AbstractCard;
import com.kapai.core.creature.Enemy;
import com.kapai.core.creature.Player;
import com.kapai.core.relic.BurningBloodRelic;
import com.kapai.core.relic.PenNibRelic;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据驱动 + 战斗流程集成测试：
 * 从 cards.json 加载卡牌 → 组建牌组 → 启动战斗 → 打牌 → 推进回合 → 验证遗物触发。
 */
class BattleIntegrationTest {

    static CardDatabase db;

    @BeforeAll
    static void loadDb() throws CardLoadException {
        db = new CardDatabase();
        db.load();
    }

    @Test
    void loadsCardsFromJson() {
        assertTrue(db.all().size() >= 8, "应加载至少 8 张卡牌");
        assertNotNull(db.get("STRIKE").orElseThrow(), "STRIKE 应存在");
    }

    @Test
    void strikeDealsDamage() {
        Player player = new Player("P", "战士", 50);
        Enemy enemy = new Enemy("E1", "史莱姆", 20);

        AbstractCard strike = db.createCopy("STRIKE").orElseThrow();
        List<AbstractCard> deck = new ArrayList<>();
        deck.add(strike);
        // 多塞几张避免抽空
        for (int i = 0; i < 6; i++) {
            deck.add(db.createCopy("DEFEND").orElseThrow());
        }

        BattleManager bm = new BattleManager(player, List.of(enemy), deck);
        bm.startBattle();

        int hpBefore = enemy.getCurrentHp();
        // 手牌中找到 STRIKE 并打出
        AbstractCard inHand = bm.getPiles().getHand().stream()
                .filter(c -> c.getId().equals("STRIKE"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("手牌中无 STRIKE"));
        bm.playCard(inHand, enemy);

        assertTrue(enemy.getCurrentHp() < hpBefore, "打击应造成伤害");
        assertEquals(BattlePhase.PLAYER_TURN, bm.getPhase());
    }

    @Test
    void relicHealsOnTurnStart() {
        Player player = new Player("P", "战士", 30);
        player.setCurrentHp(10);
        Enemy enemy = new Enemy("E1", "史莱姆", 20);

        List<AbstractCard> deck = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            deck.add(db.createCopy("DEFEND").orElseThrow());
        }

        BattleManager bm = new BattleManager(player, List.of(enemy), deck);
        bm.addRelicListener(new BurningBloodRelic());
        bm.startBattle();

        // 第一回合开始时遗物应回血 2
        assertEquals(12, player.getCurrentHp(), "燃烧之血应在回合开始回 2 HP");
    }

    @Test
    void penNibGainsEnergyAfterThreeAttacks() {
        Player player = new Player("P", "战士", 50);
        Enemy enemy = new Enemy("E1", "史莱姆", 999);

        // 牌组：6 张打击
        List<AbstractCard> deck = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            deck.add(db.createCopy("STRIKE").orElseThrow());
        }

        BattleManager bm = new BattleManager(player, List.of(enemy), deck);
        bm.addRelicListener(new PenNibRelic());
        bm.startBattle();

        int energyAfterStart = player.getEnergy();
        // 打出 3 张攻击牌
        int played = 0;
        for (AbstractCard c : new ArrayList<>(bm.getPiles().getHand())) {
            if (played >= 3) {
                break;
            }
            if (c.getId().equals("STRIKE")) {
                bm.playCard(c, enemy);
                played++;
            }
        }
        // 第 3 张后钢笔尖回复 1 能量；能量 = 初始3 - 3费用 + 1 = 1
        assertEquals(1, player.getEnergy(), "打出 3 张攻击牌后钢笔尖应回复 1 能量");
    }
}
