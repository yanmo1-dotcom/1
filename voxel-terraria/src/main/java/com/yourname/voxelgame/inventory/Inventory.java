package com.yourname.voxelgame.inventory;

/**
 * 背包：0-8 快捷栏，9-28 主背包，共 29 格。
 */
public class Inventory {

    public static final int HOTBAR_SIZE = 9;
    public static final int MAIN_SIZE = 20;
    public static final int TOTAL = 29;

    private final ItemStack[] slots = new ItemStack[TOTAL];
    private int selectedHotbar = 0;

    public Inventory() {
        for (int i = 0; i < TOTAL; i++) slots[i] = new ItemStack();
    }

    public ItemStack get(int i) { return slots[i]; }
    public int selected() { return selectedHotbar; }
    public ItemStack heldItem() { return slots[selectedHotbar]; }
    public void select(int i) { if (i >= 0 && i < HOTBAR_SIZE) selectedHotbar = i; }

    /**
     * 尝试向背包加入一组物品。优先堆叠到已有同类，再放空槽。
     * 返回未能放入的数量（0 表示全部放入）。
     */
    public int add(int id, int count) {
        if (id == 0 || count <= 0) return 0;
        int max = ItemRegistry.maxStack(id);
        // 先堆叠到已有同类（含快捷栏与主背包）
        for (int i = 0; i < TOTAL && count > 0; i++) {
            ItemStack s = slots[i];
            if (!s.isEmpty() && s.id == id && !ItemRegistry.isTool(id)) {
                int take = Math.min(count, max - s.count);
                s.count += take;
                count -= take;
            }
        }
        // 再放空槽
        for (int i = 0; i < TOTAL && count > 0; i++) {
            ItemStack s = slots[i];
            if (s.isEmpty()) {
                int take = Math.min(count, max);
                s.id = id; s.count = take;
                s.durability = ItemRegistry.isTool(id) ? ItemRegistry.get(id).maxDurability : 0;
                count -= take;
            }
        }
        return count;
    }

    /** 消耗手持工具 1 点耐久；耐久归零则销毁。 */
    public void damageHeldTool() {
        ItemStack s = heldItem();
        if (s.isEmpty() || !ItemRegistry.isTool(s.id)) return;
        s.durability--;
        if (s.durability <= 0) s.clear();
    }

    /** 手持是否为指定工具类型。 */
    public ItemRegistry.ToolType heldToolType() {
        ItemStack s = heldItem();
        if (s.isEmpty() || !ItemRegistry.isTool(s.id)) return ItemRegistry.ToolType.NONE;
        return ItemRegistry.get(s.id).toolType;
    }

    public ItemRegistry.ItemDef heldDef() {
        ItemStack s = heldItem();
        return s.isEmpty() ? null : ItemRegistry.get(s.id);
    }
}
