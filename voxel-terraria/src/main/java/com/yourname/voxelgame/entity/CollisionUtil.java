package com.yourname.voxelgame.entity;

import com.yourname.voxelgame.world.BlockAccess;

/**
 * AABB vs 体素碰撞工具。通过 BlockAccess 查询世界方块（跨 Chunk）。
 * 世界方块占 [x, x+1]³。
 */
public final class CollisionUtil {

    private CollisionUtil() {}

    /**
     * 判定 AABB 是否与任意非空方块相交（严格相交，重叠面不算）。
     */
    public static boolean intersectsVoxels(BlockAccess world, float minX, float minY, float minZ,
                                            float maxX, float maxY, float maxZ) {
        int x0 = (int) Math.floor(minX);
        int x1 = (int) Math.floor(maxX - 1e-4f);
        int y0 = (int) Math.floor(minY);
        int y1 = (int) Math.floor(maxY - 1e-4f);
        int z0 = (int) Math.floor(minZ);
        int z1 = (int) Math.floor(maxZ - 1e-4f);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (world.getBlock(x, y, z) != 0) return true;
                }
            }
        }
        return false;
    }

    /** 玩家脚下 0.05 单位下方是否有非空方块（地面检测）。 */
    public static boolean isOnGround(BlockAccess world, float minX, float minZ, float minY,
                                      float width, float depth) {
        float halfW = width * 0.5f;
        float halfD = depth * 0.5f;
        return intersectsVoxels(world,
                minX - halfW, minY - 0.05f, minZ - halfD,
                minX + halfW, minY - 1e-4f, minZ + halfD);
    }

    /** AABB 是否与任意非空方块相交（中心点 + 宽深高 形式）。 */
    public static boolean intersectsAt(BlockAccess world, float cx, float cy, float cz,
                                        float width, float height, float depth) {
        float halfW = width * 0.5f;
        float halfH = height * 0.5f;
        float halfD = depth * 0.5f;
        return intersectsVoxels(world,
                cx - halfW, cy - halfH, cz - halfD,
                cx + halfW, cy + halfH, cz + halfD);
    }
}

