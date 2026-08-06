package com.yourname.voxelgame.world;

/**
 * 正交投影下的鼠标射线检测。
 *
 * 正交投影下，屏幕上每个像素对应一条方向固定为 (0,0,-1) 的射线，
 * 起点为该像素映射到的世界 (wx, wy)。沿 -z 用 DDA 遍历体素，
 * 找到第一个非空方块，记录进入该体素时穿过的面（命中面法线）。
 */
public final class Raycaster {

    public static final int[] HIT_FRONT_NORMAL = {0, 0, 1};

    private Raycaster() {}

    public static class Hit {
        public boolean hit;
        public int bx, by, bz;       // 命中方块世界坐标
        public int nx, ny, nz;       // 命中面法线
        public int face;

        public void set(int x, int y, int z, int nxf, int nyf, int nzf, int f) {
            hit = true; bx = x; by = y; bz = z; nx = nxf; ny = nyf; nz = nzf; face = f;
        }
        public void clear() { hit = false; }
    }

    /**
     * 从屏幕世界坐标 (wx, wy) 沿 -z 射线在世界中找第一个非空方块。
     * @param world 跨 Chunk 方块查询
     * @param startZ 射线起点 z（应大于 chunk 最大 z）
     */
    public static Hit castOrtho(float wx, float wy, float startZ, BlockAccess world, Hit out) {
        out.clear();
        int cx = (int) Math.floor(wx);
        int cy = (int) Math.floor(wy);
        if (cy < 0 || cy >= Chunk.SIZE) return out;

        int zStart = (int) Math.floor(startZ);
        if (zStart >= Chunk.SIZE) zStart = Chunk.SIZE - 1;
        if (zStart < 0) return out;

        for (int z = zStart; z >= 0; z--) {
            byte b = world.getBlock(cx, cy, z);
            if (b != 0) {
                out.set(cx, cy, z, HIT_FRONT_NORMAL[0], HIT_FRONT_NORMAL[1], HIT_FRONT_NORMAL[2], Chunk.FACE_FRONT);
                return out;
            }
        }
        return out;
    }
}
