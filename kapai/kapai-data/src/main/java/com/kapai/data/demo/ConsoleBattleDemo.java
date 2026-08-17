package com.kapai.data.demo;

import com.kapai.core.battle.BattleManager;
import com.kapai.core.battle.BattlePhase;
import com.kapai.core.card.AbstractCard;
import com.kapai.core.creature.Enemy;
import com.kapai.core.creature.Player;
import com.kapai.core.enums.CardType;
import com.kapai.core.relic.BurningBloodRelic;
import com.kapai.data.CardDatabase;
import com.kapai.data.CardLoadException;
import lombok.extern.slf4j.Slf4j;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 控制台自动战斗演示。
 *
 * 用途：在不启动 LibGDX 的情况下，本地跑一场完整战斗，验证 core + data 链路。
 * 运行：mvn -pl kapai-data exec:java -Dexec.mainClass=com.kapai.data.demo.ConsoleBattleDemo
 *
 * AI 策略（演示用，极简）：
 * - 有攻击牌且能量够 → 打血量最低的敌人
 * - 否则若有技能牌 → 打防御
 * - 结束回合
 */
@Slf4j
public class ConsoleBattleDemo {

    public static void main(String[] args) throws CardLoadException {
        // 强制 stdout 为 UTF-8，避免 Windows 终端中文乱码
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));

        println("========== 卡牌肉鸽框架 · 控制台战斗演示 ==========\n");

        // 1. 加载卡牌库
        CardDatabase db = new CardDatabase();
        db.load();
        println("已加载卡牌 " + db.all().size() + " 张：");
        db.all().forEach(c -> println("  - [" + c.getId() + "] " + c.getName()
                + "  费用" + c.getCost() + "  " + c.getType()));
        println("");

        // 2. 构建玩家、敌人、牌组
        Player player = new Player("P", "战士", 50);
        Enemy e1 = new Enemy("E1", "尖刺史莱姆", 24);
        Enemy e2 = new Enemy("E2", "酸液史莱姆", 20);
        List<Enemy> enemies = new ArrayList<>(List.of(e1, e2));

        List<AbstractCard> deck = new ArrayList<>();
        for (int i = 0; i < 5; i++) deck.add(db.createCopy("STRIKE").orElseThrow());
        for (int i = 0; i < 4; i++) deck.add(db.createCopy("DEFEND").orElseThrow());
        for (int i = 0; i < 2; i++) deck.add(db.createCopy("BASH").orElseThrow());
        for (int i = 0; i < 2; i++) deck.add(db.createCopy("IRON_WAVE").orElseThrow());

        // 3. 创建战斗管理器，装备遗物
        BattleManager bm = new BattleManager(player, enemies, deck);
        bm.addRelicListener(new BurningBloodRelic());

        // 订阅阶段变化，打印阶段流转
        bm.addPhaseListener(ConsoleBattleDemo::onPhase);

        // 4. 开打
        bm.startBattle();

        int maxRounds = 30;
        int round = 0;
        while (bm.getPhase() != BattlePhase.BATTLE_END && round < maxRounds) {
            round++;
            printState(bm, player, enemies);
            // 自动出牌直到能量不足或无牌可出
            boolean played = true;
            while (played && bm.getPhase() == BattlePhase.PLAYER_TURN) {
                played = autoPlay(bm, player, enemies);
            }
            if (bm.getPhase() == BattlePhase.BATTLE_END) break;
            println(">> 玩家结束回合");
            bm.endPlayerTurn();
            println("");
        }

        println("\n========== 战斗结束 ==========");
        boolean victory = enemies.stream().allMatch(e -> e.isDead());
        println(victory ? "玩家胜利！" : "玩家失败！");
        println("回合数：" + bm.getTurnCount()
                + "  玩家剩余 HP：" + player.getCurrentHp() + "/" + player.getMaxHp());
    }

    /** 自动出一张牌；返回是否成功打出。 */
    private static boolean autoPlay(BattleManager bm, Player player, List<Enemy> enemies) {
        List<AbstractCard> hand = new ArrayList<>(bm.getPiles().getHand());
        Enemy target = lowestHpEnemy(enemies);
        if (target == null) return false;

        // 优先打出费用最高的攻击牌
        AbstractCard attack = hand.stream()
                .filter(c -> c.getType() == CardType.ATTACK && c.canPlay(player))
                .max(java.util.Comparator.comparingInt(AbstractCard::getCost))
                .orElse(null);
        if (attack != null) {
            println("  打出 [" + attack.getName() + "] → " + target.getName());
            bm.playCard(attack, target);
            return true;
        }
        // 其次打出技能牌（防御）
        AbstractCard skill = hand.stream()
                .filter(c -> c.getType() == CardType.SKILL && c.canPlay(player))
                .findFirst()
                .orElse(null);
        if (skill != null) {
            println("  打出 [" + skill.getName() + "] → 自身");
            bm.playCard(skill, null);
            return true;
        }
        return false;
    }

    private static Enemy lowestHpEnemy(List<Enemy> enemies) {
        return enemies.stream()
                .filter(e -> !e.isDead())
                .min(java.util.Comparator.comparingInt(Enemy::getCurrentHp))
                .orElse(null);
    }

    private static void printState(BattleManager bm, Player player, List<Enemy> enemies) {
        println("---- 第 " + bm.getTurnCount() + " 回合 ----");
        println("玩家 HP=" + player.getCurrentHp() + "/" + player.getMaxHp()
                + "  能量=" + player.getEnergy()
                + "  格挡=" + player.statusAmount(com.kapai.core.status.StatusId.BLOCK));
        for (Enemy e : enemies) {
            String intent = e.getIntent() == null ? "无" : e.getIntent().getKind() + "(" + e.getIntent().getDamage() + ")";
            println("  敌人 " + e.getName() + " HP=" + e.getCurrentHp() + "/" + e.getMaxHp()
                    + "  意图=" + intent
                    + (e.isDead() ? "  [已死亡]" : ""));
        }
        StringBuilder hand = new StringBuilder("  手牌：");
        bm.getPiles().getHand().forEach(c -> hand.append(c.getName()).append("(").append(c.getCost()).append(") "));
        println(hand.toString());
    }

    private static void onPhase(BattlePhase phase) {
        // 阶段流转静默记录，避免刷屏；如需调试可取消注释
        // println("  [阶段] " + phase);
    }

    private static void println(String s) {
        System.out.println(s);
    }
}
