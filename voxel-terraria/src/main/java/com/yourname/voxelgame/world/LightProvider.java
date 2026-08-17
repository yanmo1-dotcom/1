package com.yourname.voxelgame.world;

/**
 * 光照引擎与世界交互的接口：读写方块与光照（世界坐标）。
 * ChunkManager 实现此接口以支持跨 Chunk 的 BFS 光照传播。
 */
public interface LightProvider {
    /** 世界坐标方块 ID（未加载/越界返回 0）。 */
    byte getBlock(int wx, int wy, int wz);
    /** 世界坐标光照等级（未加载/越界返回 0）。 */
    byte getLight(int wx, int wy, int wz);
    /** 设置世界坐标光照等级（必须落在已加载 chunk 内）。 */
    void setLight(int wx, int wy, int wz, byte level);
}
