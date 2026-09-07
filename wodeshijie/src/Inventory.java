import java.awt.Color;
import java.awt.Graphics;
import java.awt.Font;

/**
 * 物品栏类
 * 管理快捷栏（9格）和物品数量
 */
public class Inventory {
    public static final int SLOT_COUNT = 9;
    private BlockType[] slots;   // 每格的方块类型
    private int[] counts;        // 每格的数量
    private int selectedSlot;    // 当前选中的格子

    public Inventory() {
        slots = new BlockType[SLOT_COUNT];
        counts = new int[SLOT_COUNT];
        selectedSlot = 0;

        // 初始给一些基础方块方便测试
        slots[0] = BlockType.GRASS;
        counts[0] = 64;
        slots[1] = BlockType.DIRT;
        counts[1] = 64;
        slots[2] = BlockType.STONE;
        counts[2] = 64;
        slots[3] = BlockType.WOOD;
        counts[3] = 32;
        slots[4] = BlockType.PLANKS;
        counts[4] = 32;
        slots[5] = BlockType.GLASS;
        counts[5] = 16;
        slots[6] = BlockType.BRICK;
        counts[6] = 16;
        slots[7] = BlockType.SAND;
        counts[7] = 32;
        slots[8] = BlockType.TORCH;
        counts[8] = 16;
    }

    /** 获取当前选中的方块类型 */
    public BlockType getSelectedBlock() {
        return slots[selectedSlot];
    }

    /** 选中格子切换（0-8） */
    public void selectSlot(int slot) {
        if (slot >= 0 && slot < SLOT_COUNT) {
            selectedSlot = slot;
        }
    }

    /** 滚轮切换 */
    public void scrollSelect(int direction) {
        selectedSlot = (selectedSlot + direction + SLOT_COUNT) % SLOT_COUNT;
    }

    /** 添加物品到物品栏 */
    public void addItem(BlockType type) {
        // 先找已有同类的格子
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots[i] == type && counts[i] < 64) {
                counts[i]++;
                return;
            }
        }
        // 再找空格子
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (slots[i] == null || counts[i] == 0) {
                slots[i] = type;
                counts[i] = 1;
                return;
            }
        }
    }

    /** 消耗一个当前选中的方块 */
    public boolean consumeSelected() {
        if (counts[selectedSlot] > 0) {
            counts[selectedSlot]--;
            if (counts[selectedSlot] == 0) {
                slots[selectedSlot] = null;
            }
            return true;
        }
        return false;
    }

    /** 当前选中格是否有物品 */
    public boolean hasSelectedItem() {
        return counts[selectedSlot] > 0 && slots[selectedSlot] != null;
    }

    public int getSelectedSlot() { return selectedSlot; }

    /**
     * 绘制快捷栏（屏幕底部中央）
     */
    public void draw(Graphics g, int screenWidth, int screenHeight) {
        int slotSize = 48;
        int gap = 4;
        int totalWidth = SLOT_COUNT * slotSize + (SLOT_COUNT - 1) * gap;
        int startX = (screenWidth - totalWidth) / 2;
        int startY = screenHeight - slotSize - 16;

        g.setFont(new Font("SansSerif", Font.BOLD, 14));

        for (int i = 0; i < SLOT_COUNT; i++) {
            int sx = startX + i * (slotSize + gap);
            int sy = startY;

            // 槽位背景
            if (i == selectedSlot) {
                g.setColor(new Color(255, 255, 255, 220));
            } else {
                g.setColor(new Color(0, 0, 0, 160));
            }
            g.fillRect(sx, sy, slotSize, slotSize);

            // 边框
            g.setColor(i == selectedSlot ? Color.WHITE : new Color(100, 100, 100));
            g.drawRect(sx, sy, slotSize, slotSize);

            // 绘制方块图标
            if (slots[i] != null && counts[i] > 0) {
                g.setColor(slots[i].getColor());
                g.fillRect(sx + 8, sy + 8, slotSize - 16, slotSize - 16);
                g.setColor(Color.BLACK);
                g.drawRect(sx + 8, sy + 8, slotSize - 16, slotSize - 16);

                // 数量
                g.setColor(Color.WHITE);
                String countStr = String.valueOf(counts[i]);
                g.drawString(countStr, sx + slotSize - 8 - g.getFontMetrics().stringWidth(countStr),
                        sy + slotSize - 8);
            }

            // 序号标签
            g.setColor(new Color(200, 200, 200));
            g.drawString(String.valueOf(i + 1), sx + 4, sy + 14);
        }
    }
}
