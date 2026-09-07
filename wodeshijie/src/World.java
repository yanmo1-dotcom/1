import java.util.Random;

/**
 * 世界类
 * 负责世界数据存储、地形生成、方块操作
 * 坐标系：x向右递增，y向下递增（y=0为世界顶部天空）
 */
public class World {
    public static final int BLOCK_SIZE = 32;       // 每个方块的像素大小
    public static final int WORLD_WIDTH = 512;     // 世界宽度（格数）
    public static final int WORLD_HEIGHT = 128;    // 世界高度（格数）
    public static final int SEA_LEVEL = 64;        // 海平面y坐标（从顶部往下）

    private BlockType[][] blocks;  // [x][y]，y=0为世界顶部
    private Random random;
    private long seed;

    public World(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
        this.blocks = new BlockType[WORLD_WIDTH][WORLD_HEIGHT];
        generateTerrain();
    }

    public World() {
        this(System.currentTimeMillis());
    }

    /** 生成地形 */
    private void generateTerrain() {
        // 初始化全部为空气
        for (int x = 0; x < WORLD_WIDTH; x++) {
            for (int y = 0; y < WORLD_HEIGHT; y++) {
                blocks[x][y] = BlockType.AIR;
            }
        }

        // 生成地形高度图（地表y坐标，从顶部往下）
        int[] heightMap = new int[WORLD_WIDTH];
        for (int x = 0; x < WORLD_WIDTH; x++) {
            double h = SEA_LEVEL - 6; // 基准地表略高于海平面
            h += Math.sin(x * 0.02) * 8;
            h += Math.sin(x * 0.05 + 1.3) * 5;
            h += Math.sin(x * 0.01 + 2.7) * 12;
            h += Math.sin(x * 0.08 + 0.5) * 3;
            h += (random.nextDouble() - 0.5) * 2;
            heightMap[x] = (int) Math.max(20, Math.min(WORLD_HEIGHT - 15, h));
        }

        // 填充地形
        for (int x = 0; x < WORLD_WIDTH; x++) {
            int surfaceY = heightMap[x]; // 地表方块的y坐标

            for (int y = 0; y < WORLD_HEIGHT; y++) {
                if (y == WORLD_HEIGHT - 1) {
                    // 最底层基岩
                    blocks[x][y] = BlockType.BEDROCK;
                } else if (y > surfaceY + 4) {
                    // 深层石头（地表以下4格开始）
                    blocks[x][y] = BlockType.STONE;
                } else if (y > surfaceY) {
                    // 泥土层（地表以下1-4格）
                    blocks[x][y] = BlockType.DIRT;
                } else if (y == surfaceY) {
                    // 地表
                    if (surfaceY >= SEA_LEVEL - 1) {
                        blocks[x][y] = BlockType.SAND;  // 海边沙滩
                    } else if (surfaceY < SEA_LEVEL - 12) {
                        blocks[x][y] = BlockType.SNOW;  // 高山积雪
                    } else {
                        blocks[x][y] = BlockType.GRASS; // 普通草地
                    }
                } else if (y > surfaceY && y <= SEA_LEVEL) {
                    // 这个分支不会进入，因为y>surfaceY已经被泥土/石头处理
                    // 水填充在下面单独处理
                }
            }

            // 填充水：地表低于海平面时，地表以上到海平面之间填充水
            if (surfaceY > SEA_LEVEL) {
                for (int y = SEA_LEVEL; y < surfaceY; y++) {
                    if (blocks[x][y] == BlockType.AIR) {
                        blocks[x][y] = BlockType.WATER;
                    }
                }
            }
        }

        // 生成矿石
        generateOres();

        // 生成树木
        generateTrees(heightMap);
    }

    /** 生成矿石 */
    private void generateOres() {
        // 煤矿：较多，分布较广
        for (int i = 0; i < 400; i++) {
            int x = random.nextInt(WORLD_WIDTH);
            int y = SEA_LEVEL + 5 + random.nextInt(WORLD_HEIGHT - SEA_LEVEL - 10);
            if (y < WORLD_HEIGHT - 1 && blocks[x][y] == BlockType.STONE) {
                placeOreVein(x, y, BlockType.COAL_ORE, 3 + random.nextInt(4));
            }
        }
        // 铁矿：中等数量，中深层
        for (int i = 0; i < 250; i++) {
            int x = random.nextInt(WORLD_WIDTH);
            int y = SEA_LEVEL + 15 + random.nextInt(WORLD_HEIGHT - SEA_LEVEL - 20);
            if (y < WORLD_HEIGHT - 1 && blocks[x][y] == BlockType.STONE) {
                placeOreVein(x, y, BlockType.IRON_ORE, 2 + random.nextInt(3));
            }
        }
        // 钻石矿：稀少，深层
        for (int i = 0; i < 80; i++) {
            int x = random.nextInt(WORLD_WIDTH);
            int y = WORLD_HEIGHT - 25 + random.nextInt(20);
            if (y > 1 && y < WORLD_HEIGHT - 1 && blocks[x][y] == BlockType.STONE) {
                placeOreVein(x, y, BlockType.DIAMOND_ORE, 1 + random.nextInt(2));
            }
        }
    }

    /** 放置矿脉（小范围扩散） */
    private void placeOreVein(int cx, int cy, BlockType ore, int size) {
        for (int i = 0; i < size; i++) {
            int x = cx + random.nextInt(3) - 1;
            int y = cy + random.nextInt(3) - 1;
            if (x >= 0 && x < WORLD_WIDTH && y >= 0 && y < WORLD_HEIGHT) {
                if (blocks[x][y] == BlockType.STONE) {
                    blocks[x][y] = ore;
                }
            }
        }
    }

    /** 生成树木 */
    private void generateTrees(int[] heightMap) {
        for (int x = 5; x < WORLD_WIDTH - 5; x++) {
            if (random.nextDouble() < 0.05) {
                int surfaceY = heightMap[x];
                if (blocks[x][surfaceY] == BlockType.GRASS && surfaceY < SEA_LEVEL - 2) {
                    generateTree(x, surfaceY - 1); // 树从地表上方开始长
                }
            }
        }
    }

    /** 生成单棵树（y向上为负方向，即y减小） */
    private void generateTree(int x, int groundY) {
        int trunkHeight = 4 + random.nextInt(3);

        // 检查空间（向上生长，y减小）
        if (groundY - trunkHeight - 2 < 0) return;

        // 放置树干（向上）
        for (int y = groundY; y > groundY - trunkHeight; y--) {
            if (blocks[x][y] == BlockType.AIR) {
                blocks[x][y] = BlockType.WOOD;
            }
        }

        // 放置树叶（树冠）
        int leafCenterY = groundY - trunkHeight;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                int lx = x + dx;
                int ly = leafCenterY + dy;
                if (lx >= 0 && lx < WORLD_WIDTH && ly >= 0 && ly < WORLD_HEIGHT) {
                    int dist = Math.abs(dx) + Math.abs(dy - 1);
                    if (dist <= 2 && blocks[lx][ly] == BlockType.AIR) {
                        blocks[lx][ly] = BlockType.LEAVES;
                    }
                }
            }
        }
        // 顶部树叶
        if (leafCenterY - 1 >= 0 && blocks[x][leafCenterY - 1] == BlockType.AIR) {
            blocks[x][leafCenterY - 1] = BlockType.LEAVES;
        }
    }

    /** 获取指定位置的方块 */
    public BlockType getBlock(int x, int y) {
        if (x < 0 || x >= WORLD_WIDTH || y < 0 || y >= WORLD_HEIGHT) {
            return BlockType.AIR;
        }
        return blocks[x][y];
    }

    /** 设置方块 */
    public void setBlock(int x, int y, BlockType type) {
        if (x >= 0 && x < WORLD_WIDTH && y >= 0 && y < WORLD_HEIGHT) {
            blocks[x][y] = type;
        }
    }

    /** 检查指定位置是否为固体方块（用于碰撞） */
    public boolean isSolid(int x, int y) {
        return getBlock(x, y).isSolid();
    }

    public long getSeed() { return seed; }

    /** 找到指定x列的地表高度（从顶部往下第一个非空气/树叶/水方块） */
    public int getSurfaceHeight(int x) {
        for (int y = 0; y < WORLD_HEIGHT; y++) {
            BlockType b = blocks[x][y];
            if (b != BlockType.AIR && b != BlockType.LEAVES && b != BlockType.WATER) {
                return y;
            }
        }
        return WORLD_HEIGHT - 1;
    }
}
