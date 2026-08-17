package com.yourname.voxelgame.world;

/**
 * 世界方块查询接口。支持跨 Chunk 的世界坐标查询，
 * Chunk 与 ChunkManager 都实现此接口，物理/碰撞通过它解耦。
 */
public interface BlockAccess {
    /** 世界坐标 → 方块 ID（越界/未加载返回 0）。 */
    byte getBlock(int wx, int wy, int wz);
}
