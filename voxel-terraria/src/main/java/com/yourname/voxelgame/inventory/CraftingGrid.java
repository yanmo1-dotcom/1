package com.yourname.voxelgame.inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * 3×3 合成网格 + 输出格。每次变动重新检查所有配方。
 */
public class CraftingGrid {

    private final ItemStack[] slots = new ItemStack[9];
    private ItemStack output = new ItemStack();
    private final List<Recipe> recipes = new ArrayList<>();

    public CraftingGrid() {
        for (int i = 0; i < 9; i++) slots[i] = new ItemStack();
        registerRecipes();
    }

    private void registerRecipes() {
        // 镐：3材料(头) + 2煤(柄)，形状 [SSS][.C.][.C.]
        recipes.add(new Recipe(new int[][] {
            {3,3,3}, {0,6,0}, {0,6,0}
        }, ItemRegistry.STONE_PICKAXE, 1));
        recipes.add(new Recipe(new int[][] {
            {7,7,7}, {0,6,0}, {0,6,0}
        }, ItemRegistry.IRON_PICKAXE, 1));
        recipes.add(new Recipe(new int[][] {
            {4,4,4}, {0,6,0}, {0,6,0}
        }, ItemRegistry.WOOD_PICKAXE, 1));
        // 剑：2材料(刃) + 1煤(柄)，形状 [.S.][.S.][.C.]
        recipes.add(new Recipe(new int[][] {
            {0,3,0}, {0,3,0}, {0,6,0}
        }, ItemRegistry.STONE_SWORD, 1));
        recipes.add(new Recipe(new int[][] {
            {0,7,0}, {0,7,0}, {0,6,0}
        }, ItemRegistry.IRON_SWORD, 1));
        recipes.add(new Recipe(new int[][] {
            {0,4,0}, {0,4,0}, {0,6,0}
        }, ItemRegistry.WOOD_SWORD, 1));
        // 火把: 1煤 + 1凝胶 → 4火把
        recipes.add(new Recipe(new int[][] {
            {0,6,0}, {0,200,0}, {0,0,0}
        }, 9, 4));
    }

    public ItemStack get(int i) { return slots[i]; }
    public ItemStack output() { return output; }

    public void set(int i, ItemStack s) { slots[i] = s; recheck(); }

    /** 重新检查配方，更新 output。 */
    public void recheck() {
        int[][] grid = new int[3][3];
        for (int i = 0; i < 9; i++) {
            ItemStack s = slots[i];
            grid[i / 3][i % 3] = s.isEmpty() ? 0 : s.id;
        }
        for (Recipe r : recipes) {
            if (r.matches(grid)) {
                output = new ItemStack(r.resultId, r.resultCount);
                return;
            }
        }
        output = new ItemStack();
    }

    /** 取走输出格（玩家点击）。扣除合成材料各 1。 */
    public ItemStack takeOutput() {
        if (output.isEmpty()) return new ItemStack();
        ItemStack out = output.copy();
        output = new ItemStack();
        for (int i = 0; i < 9; i++) {
            if (!slots[i].isEmpty()) {
                slots[i].count--;
                if (slots[i].count <= 0) slots[i].clear();
            }
        }
        recheck();
        return out;
    }
}
