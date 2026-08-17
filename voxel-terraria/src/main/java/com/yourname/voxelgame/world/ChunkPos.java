package com.yourname.voxelgame.world;

/**
 * 不可变 Chunk 坐标。重写 equals/hashCode 以用作 Map key。
 * 1 Chunk = 16×16×16 方块，水平面 (x,z) 上每个 Chunk 覆盖 16×16。
 */
public final class ChunkPos {

    public final int cx;
    public final int cz;

    public ChunkPos(int cx, int cz) {
        this.cx = cx;
        this.cz = cz;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkPos)) return false;
        ChunkPos p = (ChunkPos) o;
        return p.cx == cx && p.cz == cz;
    }

    @Override
    public int hashCode() {
        return cx * 73856093 ^ cz * 19349663;
    }

    @Override
    public String toString() { return "ChunkPos(" + cx + "," + cz + ")"; }
}
