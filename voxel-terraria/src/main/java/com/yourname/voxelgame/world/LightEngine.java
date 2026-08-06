package com.yourname.voxelgame.world;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * BFS 光照传播引擎。
 *
 * - 太阳光：从每个 (x,z) 列的顶部 y=15 向下传播，遇不透明方块停止，
 *   空气格子赋 15，侧向/向下逐格衰减 1 级。
 * - 点光源：从发光方块 BFS 向外传播，初始为 emission，每格衰减 1。
 * - 增量更新：方块变化时，先移除旧光照影响，再从光源/邻居重填。
 *
 * 光照等级 0-15。所有操作通过 LightProvider 跨 Chunk 进行。
 * 队列用 int 编码坐标：低 18 位 x，中间 4 位 y(0-15)，高 18 位 z。
 */
public final class LightEngine {

    public static final int SUN_LIGHT = 15;
    private static final int MAX_LIGHT = 15;

    private LightEngine() {}

    // 坐标编码
    private static int key(int x, int y, int z) {
        return (x & 0x3FFFF) | ((y & 0xF) << 18) | ((z & 0x3FFFF) << 22);
    }
    private static int kx(int k) { int x = k & 0x3FFFF; return x >= (1<<17) ? x - (1<<18) : x; }
    private static int ky(int k) { return (k >>> 18) & 0xF; }
    private static int kz(int k) { int z = (k >>> 22) & 0x3FFFF; return z >= (1<<17) ? z - (1<<18) : z; }

    /**
     * 对整个加载世界计算太阳光（从顶部向下）。
     * 用于 chunk 首次生成后的大范围初始化。
     */
    public static void computeSunlight(LightProvider world, int minCX, int maxCX, int minCZ, int maxCZ) {
        Deque<Integer> queue = new ArrayDeque<>();
        // 第一遍：从每列顶部向下找第一个非不透明格子，赋 SUN_LIGHT 入队
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                for (int x = cx * Chunk.SIZE; x < cx * Chunk.SIZE + Chunk.SIZE; x++) {
                    for (int z = cz * Chunk.SIZE; z < cz * Chunk.SIZE + Chunk.SIZE; z++) {
                        for (int y = Chunk.SIZE - 1; y >= 0; y--) {
                            byte b = world.getBlock(x, y, z);
                            if (b == 0 || !BlockType.byId(b).opaque) {
                                world.setLight(x, y, z, (byte) SUN_LIGHT);
                                queue.add(key(x, y, z));
                            } else {
                                break; // 遇不透明方块，下方无直射阳光
                            }
                        }
                    }
                }
            }
        }
        // BFS 侧向/向下衰减
        bfsPropagate(world, queue);
    }

    /**
     * 在某点放置/移除光源或方块后，做局部增量更新。
     * 简化实现：重新计算受影响 chunk 周围的光照。具体策略：
     *   1) 清空受影响 chunk 的光照
     *   2) 重算太阳光（全列）
     *   3) 对所有发光方块重新做点光源 BFS
     * 这比纯增量简单且对 5×5 世界足够快。
     */
    public static void recomputeArea(LightProvider world, int minCX, int maxCX, int minCZ, int maxCZ) {
        // 清空光照
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    for (int z = 0; z < Chunk.SIZE; z++) {
                        for (int y = 0; y < Chunk.SIZE; y++) {
                            world.setLight(cx * Chunk.SIZE + x, y, cz * Chunk.SIZE + z, (byte) 0);
                        }
                    }
                }
            }
        }
        // 太阳光
        computeSunlight(world, minCX, maxCX, minCZ, maxCZ);
        // 点光源（扫所有方块找发光的）
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                for (int x = 0; x < Chunk.SIZE; x++) {
                    for (int z = 0; z < Chunk.SIZE; z++) {
                        for (int y = 0; y < Chunk.SIZE; y++) {
                            int wx = cx * Chunk.SIZE + x, wz = cz * Chunk.SIZE + z;
                            byte b = world.getBlock(wx, y, wz);
                            BlockType t = BlockType.byId(b);
                            if (t.lightEmission > 0) {
                                propagatePointLight(world, wx, y, wz, t.lightEmission);
                            }
                        }
                    }
                }
            }
        }
    }

    /** 从一个点光源 BFS 向外传播。 */
    public static void propagatePointLight(LightProvider world, int x, int y, int z, int level) {
        world.setLight(x, y, z, (byte) level);
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(key(x, y, z));
        bfsPropagate(world, queue);
    }

    /** 通用 BFS 衰减传播：队列中每个格子的光照已知，向邻居传播 level-1。 */
    private static void bfsPropagate(LightProvider world, Deque<Integer> queue) {
        while (!queue.isEmpty()) {
            int k = queue.poll();
            int x = kx(k), y = ky(k), z = kz(k);
            int level = world.getLight(x, y, z);
            if (level <= 1) continue; // 衰减到 1 以下不再传播
            tryNeighbor(world, queue, x + 1, y, z, level);
            tryNeighbor(world, queue, x - 1, y, z, level);
            tryNeighbor(world, queue, x, y + 1, z, level);
            tryNeighbor(world, queue, x, y - 1, z, level);
            tryNeighbor(world, queue, x, y, z + 1, level);
            tryNeighbor(world, queue, x, y, z - 1, level);
        }
    }

    private static void tryNeighbor(LightProvider world, Deque<Integer> queue,
                                     int x, int y, int z, int srcLevel) {
        if (y < 0 || y >= Chunk.SIZE) return;
        byte b = world.getBlock(x, y, z);
        if (b != 0 && BlockType.byId(b).opaque) return; // 不透明方块不传光
        int cur = world.getLight(x, y, z);
        int next = srcLevel - 1;
        if (next > cur) {
            world.setLight(x, y, z, (byte) next);
            queue.add(key(x, y, z));
        }
    }
}
