package com.yourname.voxelgame.world;

/**
 * 程序化地形生成。
 * - 2D Simplex 高度图决定地表（海平面 y=64，起伏 ±8）
 * - 3D Simplex 侵蚀洞穴（阈值以下挖空）
 * - 分层：基岩(y<2) → 石 → 土(y>surface-4) → 草(y==surface)
 * - 矿脉：煤(y<40)、铁(y<30)、金(y<20)，3D 噪声阈值聚类
 *
 * 输出 byte[16][16][16]，索引 [x][y][z]（局部坐标）。
 * Chunk 在世界 y 上仍为单层 16 高，但地形参考世界绝对 y（相对海平面）。
 * 本项目世界高度有限（0..15），所以把海平面映射到 y≈12，起伏压在 chunk 内。
 */
public final class TerrainGenerator {

    private final SimplexNoise heightNoise;
    private final SimplexNoise caveNoise;
    private final SimplexNoise oreNoise;

    // 世界纵向参数：chunk 高 16，所以海平面 ≈ 12，地形在 0..15 内
    public static final int SEA_LEVEL = 12;

    public TerrainGenerator(long seed) {
        this.heightNoise = new SimplexNoise(seed);
        this.caveNoise = new SimplexNoise(seed ^ 0x9E3779B97F4A7C15L);
        this.oreNoise = new SimplexNoise(seed ^ 0xC2B2AE3D27D4EB4FL);
    }

    /**
     * 生成某 Chunk 的方块数据。
     * @param pos Chunk 坐标
     * @return byte[x][y][z]，局部坐标 0..15
     */
    public byte[][][] generate(ChunkPos pos) {
        byte[][][] data = new byte[Chunk.SIZE][Chunk.SIZE][Chunk.SIZE];
        int baseX = pos.cx * Chunk.SIZE;
        int baseZ = pos.cz * Chunk.SIZE;

        for (int x = 0; x < Chunk.SIZE; x++) {
            for (int z = 0; z < Chunk.SIZE; z++) {
                int wx = baseX + x;
                int wz = baseZ + z;
                // 高度图：fBm，[-1,1] → 高度偏移 ±4，海平面 12
                float h = heightNoise.fbm2(wx, wz, 4, 0.5f, 0.015f);
                int surfaceY = SEA_LEVEL + (int) (h * 4f);

                for (int y = 0; y < Chunk.SIZE; y++) {
                    int wy = y; // 世界绝对 y（本项目 chunk 占 y 0..15
                    byte block = 0;
                    if (wy == 0) {
                        block = (byte) BlockType.BEDROCK.id;
                    } else if (wy <= surfaceY) {
                        // 洞穴：3D 噪声阈值以下挖空
                        float cave = caveNoise.fbm3(wx, wy, wz, 3, 0.5f, 0.05f);
                        if (cave > 0.55f && wy > 1 && wy < surfaceY) {
                            block = 0; // 空气（洞穴）
                        } else {
                            // 分层 + 矿脉
                            block = layerBlock(wy, surfaceY, wx, wz);
                        }
                    }
                    data[x][y][z] = block;
                }
            }
        }
        return data;
    }

    /** 分层 + 矿脉判定。 */
    private byte layerBlock(int wy, int surfaceY, int wx, int wz) {
        // 矿脉：3D 噪声阈值，按深度限制
        if (wy < 12) {
            float ore = oreNoise.fbm3(wx, wy, wz, 2, 0.5f, 0.08f);
            if (wy < 8 && ore > 0.7f) return (byte) BlockType.GOLD.id;
            if (wy < 10 && ore > 0.65f) return (byte) BlockType.IRON.id;
            if (wy < 12 && ore > 0.6f) return (byte) BlockType.COAL.id;
        }
        // 分层
        if (wy >= surfaceY) return (byte) BlockType.GRASS.id;
        if (wy >= surfaceY - 3) return (byte) BlockType.DIRT.id;
        return (byte) BlockType.STONE.id;
    }
}
