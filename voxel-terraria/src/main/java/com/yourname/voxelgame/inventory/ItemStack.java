package com.yourname.voxelgame.inventory;

/**
 * 物品堆。可变：count 与 durability。
 * id=0 表示空槽。
 */
public class ItemStack {

    public int id;        // 0 = 空
    public int count;     // 1..maxStack
    public int durability;// 工具耐久；非工具为 0

    public ItemStack() { this.id = 0; this.count = 0; this.durability = 0; }

    public ItemStack(int id, int count) {
        this.id = id;
        this.count = count;
        this.durability = ItemRegistry.isTool(id) ? ItemRegistry.get(id).maxDurability : 0;
    }

    public boolean isEmpty() { return id == 0 || count <= 0; }

    public void clear() { id = 0; count = 0; durability = 0; }

    /** 剩余可堆叠数量。 */
    public int room() {
        if (isEmpty()) return ItemRegistry.maxStack(id > 0 ? id : 1);
        return Math.max(0, ItemRegistry.maxStack(id) - count);
    }

    /** 尝试合并 other 到 this。返回 other 中剩余未合并数量。 */
    public int merge(ItemStack other) {
        if (other.isEmpty()) return 0;
        if (isEmpty()) {
            // 空槽直接接收
            int take = Math.min(other.count, ItemRegistry.maxStack(other.id));
            this.id = other.id; this.durability = other.durability;
            this.count = take;
            other.count -= take;
            if (other.count <= 0) other.clear();
            return other.count;
        }
        if (this.id != other.id || ItemRegistry.isTool(id)) return other.count; // 不同物或工具不合并
        int take = Math.min(other.count, room());
        this.count += take;
        other.count -= take;
        if (other.count <= 0) other.clear();
        return other.count;
    }

    /** 分裂出 n 个（若够），返回新堆；不足返回空。 */
    public ItemStack split(int n) {
        if (n <= 0 || isEmpty()) return new ItemStack();
        int take = Math.min(n, count);
        ItemStack out = new ItemStack(id, take);
        out.durability = durability;
        this.count -= take;
        if (count <= 0) clear();
        return out;
    }

    public ItemStack copy() {
        ItemStack s = new ItemStack(id, count);
        s.durability = durability;
        return s;
    }

    public ItemRegistry.ItemDef def() { return ItemRegistry.get(id); }
}
