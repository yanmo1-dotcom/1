package com.yourname.voxelgame.inventory;

/**
 * 不可变合成配方。pattern 是 3×3 的物品 id（0=空），result 是产出（id+count）。
 * 配方匹配允许 pattern 在 3×3 中任意平移（对 1×N / N×1 配方），
 * 简化为：把 pattern 与网格都先去掉外围空行/列后逐格比较。
 */
public final class Recipe {

    public final int[][] pattern; // [row][col]，3×3，0 表示空
    public final int resultId;
    public final int resultCount;

    public Recipe(int[][] pattern, int resultId, int resultCount) {
        this.pattern = pattern;
        this.resultId = resultId;
        this.resultCount = resultCount;
    }

    /** 判断 3×3 网格是否匹配本配方（允许外围留空）。 */
    public boolean matches(int[][] grid) {
        // 找 pattern 与 grid 的有效边界后逐格比较
        int[] pb = bounds(pattern);
        int[] gb = bounds(grid);
        if (pb[2] - pb[0] != gb[2] - gb[0]) return false; // 高不同
        if (pb[3] - pb[1] != gb[3] - gb[1]) return false; // 宽不同
        for (int r = 0; r <= pb[2] - pb[0]; r++) {
            for (int c = 0; c <= pb[3] - pb[1]; c++) {
                int p = pattern[pb[0] + r][pb[1] + c];
                int g = grid[gb[0] + r][gb[1] + c];
                if (p != g) return false;
            }
        }
        return true;
    }

    /** 返回 [top, left, bottom, right] 非空边界（含）。 */
    private static int[] bounds(int[][] m) {
        int top = 3, left = 3, bottom = -1, right = -1;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                if (m[r][c] != 0) {
                    if (r < top) top = r;
                    if (r > bottom) bottom = r;
                    if (c < left) left = c;
                    if (c > right) right = c;
                }
            }
        }
        return new int[] { top, left, bottom, right };
    }
}
