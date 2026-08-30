package tailai;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 成就系统：记录玩家在游戏中的里程碑。
 * 解锁时屏幕右上角弹出通知，按H键查看成就面板。
 */
public class Achievement {
    public final String id;
    public final String name;
    public final String description;
    public boolean unlocked;
    public float unlockTime; // 解锁时间（用于通知动画）

    public Achievement(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.unlocked = false;
        this.unlockTime = 0;
    }

    /** 所有成就列表。 */
    public static List<Achievement> createAll() {
        List<Achievement> list = new ArrayList<>();
        list.add(new Achievement("first_mine", "初出茅庐", "第一次挖掘方块"));
        list.add(new Achievement("first_place", "木匠", "放置第一个方块"));
        list.add(new Achievement("first_kill", "战士", "击败第一个敌人"));
        list.add(new Achievement("copper_mine", "矿工", "挖到铜矿石"));
        list.add(new Achievement("iron_sword", "铁匠", "合成第一把铁剑"));
        list.add(new Achievement("first_fish", "渔夫", "钓到第一条鱼"));
        list.add(new Achievement("eye_boss", "驯兽师", "击败克苏鲁之眼"));
        list.add(new Achievement("build_house", "建筑师", "建造一个合格房屋"));
        list.add(new Achievement("first_npc", "社交达人", "第一个NPC入住"));
        list.add(new Achievement("hell_explorer", "地狱探索者", "到达地狱"));
        list.add(new Achievement("hard_mode", "困难征服者", "击败血肉墙，开启困难模式"));
        list.add(new Achievement("goblin_slayer", "哥布林克星", "击退哥布林入侵"));
        list.add(new Achievement("destroyer", "机械杀手", "击败机械毁灭者"));
        list.add(new Achievement("collector", "收藏家", "获得30种不同物品"));
        list.add(new Achievement("max_hp", "生命巅峰", "生命上限达到300"));
        return list;
    }

    /** 绘制成就通知（右上角弹出）。 */
    public static void drawNotification(Graphics2D g, Achievement a, float yOffset) {
        int w = 260;
        int h = 60;
        int x = GamePanel.VIEW_W - w - 20;
        int y = (int)(80 + yOffset);
        // 背景
        g.setColor(new Color(0, 0, 0, 220));
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(new Color(255, 215, 80));
        g.drawRoundRect(x, y, w, h, 10, 10);
        // 奖杯图标
        g.setColor(new Color(255, 215, 80));
        g.fillOval(x + 10, y + 15, 30, 30);
        g.setColor(new Color(200, 160, 40));
        g.fillOval(x + 15, y + 20, 20, 20);
        g.setColor(new Color(255, 240, 150));
        g.fillOval(x + 18, y + 23, 8, 8);
        // 文字
        g.setColor(new Color(255, 215, 80));
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 14));
        g.drawString("成就解锁！", x + 50, y + 22);
        g.setColor(Color.WHITE);
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.BOLD, 13));
        g.drawString(a.name, x + 50, y + 40);
        g.setColor(new Color(200, 200, 200));
        g.setFont(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 11));
        g.drawString(a.description, x + 50, y + 54);
    }
}
