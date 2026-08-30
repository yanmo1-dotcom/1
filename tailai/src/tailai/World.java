package tailai;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Random;

/**
 * 方块世界：由字节网格组成的横版沙盒。
 * 负责程序化地形生成、挖掘、放置、方块<->物品映射、存档读写。
 */
public class World {

    public static final int TILE = 16;

    public final int width;
    public final int height;
    private final byte[] tiles; // 一维存储，idx = y*width + x
    /** 地图中部的地表高度（格），用于出生点与"是否在地下"判定。 */
    public int surfaceY = 40;
    /** 当前世界生成种子（联机时同步给客户端生成相同世界）。 */
    public long seed = 0;
    /** 方块改动计数（渲染缓存失效用）。 */
    public int modCount = 0;
    /** 每列的生物群系：0 森林 1 沙漠 2 雪原 3 丛林 4 腐化。 */
    public int[] biome;

    public World() {
        this(800, 240);
    }

    public World(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new byte[width * height];
    }

    private int idx(int gx, int gy) {
        return gy * width + gx;
    }

    public TileType get(int gx, int gy) {
        if (gx < 0 || gx >= width || gy < 0 || gy >= height) {
            return TileType.STONE; // 世界外视为石头，防止实体掉出
        }
        return TileType.byId(tiles[idx(gx, gy)]);
    }

    public void set(int gx, int gy, TileType t) {
        if (gx < 0 || gx >= width || gy < 0 || gy >= height) {
            return;
        }
        tiles[idx(gx, gy)] = (byte) t.id;
        modCount++;
    }

    public boolean isSolid(int gx, int gy) {
        return get(gx, gy).solid;
    }

    // ------------------------------------------------------------------
    // 世界生成
    // ------------------------------------------------------------------

    public void generate(long seed) {
        this.seed = seed;
        Random r = new Random(seed);

        // 1) 分段地形高度场 + 生物群系：平原/丘陵/山地区域平滑衔接；
        //    生物群系（沙漠/雪原/丛林/森林）按段分布，地表方块与植被随之变化
        int[] ground = new int[width];
        biome = new int[width]; // 0 森林 1 沙漠 2 雪原 3 丛林 4 腐化
        {
            Random gr = new Random(seed ^ 0x5EED);
            // 6 个正弦波相位种子，用于空间相关的平滑地形（无锯齿）
            float[] ns = new float[6];
            for (int i = 0; i < 6; i++) {
                ns[i] = gr.nextFloat() * 6.28318f;
            }
            int pos = 0;
            int prevH = 60;
            while (pos < width) {
                int segLen = Math.min(120 + gr.nextInt(100), width - pos);
                // 段类型：50% 平原、32% 丘陵、18% 山地（减少山地，避免全是尖峰）
                int type;
                double roll = gr.nextDouble();
                if (roll < 0.50) {
                    type = 0;
                } else if (roll < 0.82) {
                    type = 1;
                } else {
                    type = 2;
                }
                // 段基准高度：相对上一段末端平缓变化
                int base = prevH + gr.nextInt(11) - 5;
                base = Math.max(48, Math.min(82, base));
                // 振幅：平原 ±2、丘陵 ±5、山地 ±10（降低振幅，避免剧烈锯齿）
                int amp = (type == 0) ? 2 : (type == 1) ? 5 : 10;
                for (int i = 0; i < segLen; i++) {
                    float t = i / (float) segLen;
                    float s = t * t * (3 - 2 * t); // 平滑步进
                    int h = (int) (prevH + (base - prevH) * s);
                    // 平滑噪声：3 层正弦波叠加（空间相关，相邻格高度连续变化，无锯齿尖峰）
                    float nx = pos * 0.012f;
                    h += (int) (Math.sin(nx + ns[0]) * amp * 0.55);
                    h += (int) (Math.sin(nx * 2.7f + ns[1]) * amp * 0.30);
                    h += (int) (Math.sin(nx * 6.1f + ns[2]) * amp * 0.15);
                    h += (int) (Math.sin(pos * 0.004 + ns[3]) * 4); // 超大尺度走势
                    ground[pos] = Math.max(15, Math.min(height - 30, h));
                    pos++;
                }
                prevH = base;
            }
            // 生物群系大区：森林占比最大（开局安全），腐化/雪原等稀有群系各 1 区
            // 大世界 5 区：森林×2、沙漠、雪原、丛林、腐化中选 4 个（森林至少 2）
            int regions = Math.min(5, Math.max(1, width / 180));
            int[] pool;
            if (regions <= 2) {
                pool = new int[]{0, 0}; // 小世界全森林
            } else if (regions == 3) {
                pool = new int[]{0, 0, gr.nextInt(2) + 1}; // 森林×2 + 沙漠/雪原
            } else if (regions == 4) {
                pool = new int[]{0, 0, 1 + gr.nextInt(3), 1 + gr.nextInt(3)}; // 森林×2 + 2 个其他
            } else {
                // 5 区：森林×2 + 沙漠/雪原/丛林/腐化 各 1（洗牌）
                pool = new int[]{0, 0, 1, 2, 3};
                if (gr.nextDouble() < 0.5) {
                    pool[4] = 4; // 50% 概率包含腐化之地
                }
                for (int i = pool.length - 1; i > 0; i--) {
                    int j = gr.nextInt(i + 1);
                    int tmp = pool[i];
                    pool[i] = pool[j];
                    pool[j] = tmp;
                }
            }
            // 出生点所在区强制森林
            int regionW = width / regions;
            int cRegion = Math.min(regions - 1, (width / 2) / regionW);
            if (pool[cRegion] != 0) {
                // 找一个森林区交换
                for (int i = 0; i < regions; i++) {
                    if (pool[i] == 0) {
                        pool[i] = pool[cRegion];
                        pool[cRegion] = 0;
                        break;
                    }
                }
            }
            for (int rr = 0; rr < regions; rr++) {
                int x0 = rr * regionW;
                int x1 = (rr == regions - 1) ? width : (rr + 1) * regionW;
                for (int x = x0; x < x1; x++) {
                    biome[x] = pool[rr];
                }
            }
            // 出生点（地图中心附近）压平：中心 ±50 格完全同一高度，两侧渐变过渡，开局就是大平原
            int cx = width / 2;
            int baseH = ground[cx];
            int flatR = 50;   // 完全压平半径（扩大，避免边缘断崖）
            int blendR = 20;  // 过渡带宽度
            for (int x = cx - flatR - blendR; x <= cx + flatR + blendR; x++) {
                if (x < 2 || x >= width - 2) {
                    continue;
                }
                int d = Math.abs(x - cx);
                if (d <= flatR) {
                    ground[x] = baseH;
                } else {
                    float t = Math.min(1f, (d - flatR) / (float) blendR);
                    ground[x] = (int) (baseH + (ground[x] - baseH) * (0.5f + 0.5f * t));
                }
            }
            // 出生点区域强制为森林（开局安全草原）
            for (int x = cx - flatR - blendR - 8; x <= cx + flatR + blendR + 8; x++) {
                if (x >= 0 && x < width) {
                    biome[x] = 0;
                }
            }
        }

        // 2) 分层填充：按生物群系决定表层方块（草/沙/雪/丛林草）与次表层
        // 直接操作 tiles 数组（避免 19 万次 set() 边界检查+modCount++，大幅提升生成速度）
        int hellStart = height - 45; // 地狱层起始高度
        for (int x = 0; x < width; x++) {
            int g = ground[x];
            int b = biome[x];
            for (int y = 0; y < height; y++) {
                TileType t;
                if (y >= hellStart) {
                    // 地狱层：灰烬+地狱石+熔岩池
                    if (y == height - 1) {
                        t = TileType.ASH; // 最底层实心
                    } else if (y >= hellStart + 5 && r.nextDouble() < 0.12 && y < height - 3) {
                        t = TileType.LAVA; // 熔岩池
                    } else if (r.nextDouble() < 0.06) {
                        t = TileType.HELLSTONE; // 地狱石矿脉
                    } else {
                        t = TileType.ASH;
                    }
                } else if (y < g) {
                    t = TileType.AIR;
                } else if (y == g) {
                    t = surfaceTile(b);
                } else if (y <= g + 2) {
                    if (b == 1) {
                        t = TileType.SAND; // 沙漠表层下是沙
                    } else if (b == 4) {
                        t = TileType.EBONSTONE; // 腐化表层下是黑檀石
                    } else {
                        t = TileType.DIRT;
                    }
                } else if (b == 2 && y == g + 3) {
                    t = TileType.ICE; // 雪原表层下有一层冰
                } else if (b == 4 && y <= g + 6) {
                    t = TileType.EBONSTONE; // 腐化地下浅层是黑檀石
                } else {
                    t = TileType.STONE;
                }
                tiles[y * width + x] = (byte) t.id;
            }
        }
        modCount++;

        // 3) 矿脉：在石头层随机生成聚集矿脉（深度基于地表，保证落在石头层；铜铁交替保证两种矿都有）
        int veins = width / 60 + 5;
        for (int i = 0; i < veins; i++) {
            int cx = r.nextInt(width);
            int cy = Math.max(ground[cx] + 6, 30)
                    + r.nextInt(Math.max(6, height - ground[cx] - 24));
            TileType ore = (i % 2 == 0) ? TileType.COPPER : TileType.IRON;
            int nr = 1 + r.nextInt(3);
            for (int k = 0; k < 50; k++) {
                int x = cx + r.nextInt(nr * 2 + 1) - nr;
                int y = cy + r.nextInt(nr * 2 + 1) - nr;
                if (x >= 0 && x < width && y >= 0 && y < height && get(x, y) == TileType.STONE) {
                    set(x, y, ore);
                }
            }
        }

        // 3.5) 金矿脉：比铁更深、更稀有（地下 20 格以下，数量少）
        int goldVeins = width / 120 + 2;
        for (int i = 0; i < goldVeins; i++) {
            int cx = r.nextInt(width);
            int cy = Math.max(ground[cx] + 20, 50)
                    + r.nextInt(Math.max(6, height - ground[cx] - 30));
            int nr = 1 + r.nextInt(2);
            for (int k = 0; k < 30; k++) {
                int x = cx + r.nextInt(nr * 2 + 1) - nr;
                int y = cy + r.nextInt(nr * 2 + 1) - nr;
                if (x >= 0 && x < width && y >= 0 && y < height && get(x, y) == TileType.STONE) {
                    set(x, y, TileType.GOLD);
                }
            }
        }

        // 4) 洞穴：随机挖空若干圆形区域（数量增加，让地下更有探索性）
        int caves = width / 25 + 8;
        for (int i = 0; i < caves; i++) {
            int cx = r.nextInt(width);
            int cy = Math.max(ground[cx] + 12, 24) + r.nextInt(Math.max(8, height - 40 - ground[cx] - 12));
            int rad = 3 + r.nextInt(5);
            for (int y = cy - rad; y <= cy + rad; y++) {
                for (int x = cx - rad; x <= cx + rad; x++) {
                    if (x < 0 || x >= width || y < 1 || y >= height - 1) {
                        continue;
                    }
                    double d = Math.hypot(x - cx, y - cy);
                    if (d < rad - r.nextDouble() * 1.5) {
                        if (get(x, y) != TileType.GRASS && get(x, y) != TileType.WOOD) {
                            set(x, y, TileType.AIR);
                        }
                    }
                }
            }
            // 洞穴里放 1~2 根火把点缀
            for (int k = 0; k < 2; k++) {
                int tx = cx + r.nextInt(rad * 2 + 1) - rad;
                int ty = cy + r.nextInt(rad * 2 + 1) - rad;
                if (tx > 0 && tx < width && ty > 0 && ty < height - 1
                        && get(tx, ty) == TileType.AIR && adjacentSolid(tx, ty)) {
                    set(tx, ty, TileType.TORCH);
                }
            }
        }

        // 5) 植被：按生物群系生成（森林绿树 / 沙漠仙人掌 / 雪原针叶树 / 丛林大树）
        for (int x = 12; x < width - 12; x++) {
            int b = biome[x];
            int g = ground[x];
            if (get(x, g) != surfaceTile(b)) {
                continue;
            }
            if (b == 1) { // 沙漠：仙人掌
                if (r.nextDouble() < 0.05) {
                    int th = 2 + r.nextInt(3);
                    for (int i = 1; i <= th; i++) {
                        set(x, g - i, TileType.CACTUS);
                    }
                }
            } else if (b == 2) { // 雪原：针叶树
                if (r.nextDouble() < 0.05) {
                    int th = 4 + r.nextInt(3);
                    for (int i = 1; i <= th; i++) {
                        set(x, g - i, TileType.WOOD);
                    }
                    int top = g - th;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -2; dy <= 1; dy++) {
                            int tx = x + dx;
                            int ty = top + dy;
                            if (tx > 0 && tx < width && ty > 0 && ty < height) {
                                if (get(tx, ty) == TileType.AIR && r.nextDouble() < 0.97) {
                                    set(tx, ty, TileType.LEAF);
                                }
                            }
                        }
                    }
                }
            } else if (b == 3) { // 丛林：大树
                if (r.nextDouble() < 0.09) {
                    int th = 5 + r.nextInt(3);
                    for (int i = 1; i <= th; i++) {
                        set(x, g - i, TileType.WOOD);
                    }
                    int top = g - th;
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dy = -2; dy <= 1; dy++) {
                            int tx = x + dx;
                            int ty = top + dy;
                            if (tx > 0 && tx < width && ty > 0 && ty < height) {
                                if (get(tx, ty) == TileType.AIR && r.nextDouble() < 0.97) {
                                    set(tx, ty, TileType.LEAF);
                                }
                            }
                        }
                    }
                }
            } else if (b == 4) { // 腐化：暗影树 + 毒蘑菇
                if (r.nextDouble() < 0.07) {
                    int th = 4 + r.nextInt(3);
                    for (int i = 1; i <= th; i++) {
                        set(x, g - i, TileType.SHADOW_WOOD);
                    }
                    // 树冠用暗影木横向延伸（模拟紫色树冠）
                    int top = g - th;
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            int tx = x + dx;
                            int ty = top + dy;
                            if (tx > 0 && tx < width && ty > 0 && ty < height
                                    && get(tx, ty) == TileType.AIR && r.nextDouble() < 0.7) {
                                set(tx, ty, TileType.SHADOW_WOOD);
                            }
                        }
                    }
                }
                // 地表随机长毒蘑菇
                if (r.nextDouble() < 0.04 && get(x, g - 1) == TileType.AIR) {
                    set(x, g - 1, TileType.VILE_MUSHROOM);
                }
            } else { // 森林：普通树
                if (r.nextDouble() < 0.06) {
                    int th = 4 + r.nextInt(3);
                    for (int i = 1; i <= th; i++) {
                        set(x, g - i, TileType.WOOD);
                    }
                    int top = g - th;
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dy = -2; dy <= 1; dy++) {
                            int tx = x + dx;
                            int ty = top + dy;
                            if (tx > 0 && tx < width && ty > 0 && ty < height) {
                                if (get(tx, ty) == TileType.AIR && r.nextDouble() < 0.97) {
                                    set(tx, ty, TileType.LEAF);
                                }
                            }
                        }
                    }
                }
            }
        }

        surfaceY = ground[width / 2];

        // 6) 生命水晶：地下深处随机散布（多重试，确保落在洞穴空气格）
        int crystals = 6 + r.nextInt(4);
        int placed = 0;
        for (int i = 0; i < crystals && placed < crystals; i++) {
            for (int tries = 0; tries < 40; tries++) {
                int cx = 10 + r.nextInt(width - 20);
                int cy = Math.max(ground[cx] + 8, 26) + r.nextInt(Math.max(8, height - 40 - ground[cx]));
                if (get(cx, cy) == TileType.AIR && adjacentSolid(cx, cy)) {
                    set(cx, cy, TileType.HEART_CRYSTAL);
                    placed++;
                    break;
                }
            }
        }

        // 7) 地下水池：深处洞穴底部填充水（钓鱼用）
        for (int x = 5; x < width - 5; x++) {
            for (int y = ground[x] + 18; y < height - 3; y++) {
                if (get(x, y) == TileType.AIR && isSolid(x, y + 1)) {
                    // 左右至少一侧有固体（形成盆地）
                    if (isSolid(x - 1, y) || isSolid(x + 1, y)) {
                        if (r.nextDouble() < 0.35) {
                            set(x, y, TileType.WATER);
                            // 向上填充 1-2 格水
                            if (get(x, y - 1) == TileType.AIR && r.nextDouble() < 0.5) {
                                set(x, y - 1, TileType.WATER);
                            }
                        }
                    }
                }
            }
        }

        // 8) 地下水池已在第 7 步生成；地表不生成孤立湖泊（避免水竖条）
    }

    /** 目标格是否与任一固体格四邻相接。 */
    public boolean adjacentSolid(int gx, int gy) {
        return isSolid(gx - 1, gy) || isSolid(gx + 1, gy)
                || isSolid(gx, gy - 1) || isSolid(gx, gy + 1);
    }

    /** 生物群系对应的表层方块。0=森林草皮 1=沙漠沙 2=雪原雪 3=丛林草皮。 */
    static TileType surfaceTile(int b) {
        switch (b) {
            case 1: return TileType.SAND;
            case 2: return TileType.SNOW;
            case 3: return TileType.JUNGLE_GRASS;
            case 4: return TileType.CORRUPT_GRASS;
            default: return TileType.GRASS;
        }
    }

    // ------------------------------------------------------------------
    // 挖掘 / 放置
    // ------------------------------------------------------------------

    /** 挖掉一格方块并返回对应掉落物品；空气返回 null。 */
    public Item mine(int gx, int gy) {
        TileType t = get(gx, gy);
        if (t == TileType.AIR || t.mineSeconds <= 0) {
            return null;
        }
        set(gx, gy, TileType.AIR);
        return tileToItem(t);
    }

    public static Item tileToItem(TileType t) {
        switch (t) {
            case GRASS: return Item.GRASS;
            case DIRT: return Item.DIRT;
            case STONE: return Item.STONE;
            case COPPER: return Item.COPPER;
            case IRON: return Item.IRON;
            case WOOD: return Item.WOOD;
            case LEAF: return Item.LEAF;
            case GEL_BLOCK: return Item.GEL_BLOCK;
            case TORCH: return Item.TORCH;
            case WORKBENCH: return Item.WORKBENCH;
            case HEART_CRYSTAL: return Item.LIFE_CRYSTAL;
            case SAND: return Item.SAND;
            case SNOW: return Item.SNOW;
            case ICE: return Item.ICE;
            case CACTUS: return Item.CACTUS;
            case JUNGLE_GRASS: return Item.JUNGLE_GRASS;
            case CORRUPT_GRASS: return Item.DIRT;
            case EBONSTONE: return Item.STONE;
            case SHADOW_WOOD: return Item.WOOD;
            case FURNACE: return Item.FURNACE;
            case ANVIL: return Item.ANVIL;
            case GOLD: return Item.GOLD;
            case ASH: return Item.ASH;
            case HELLSTONE: return Item.HELLSTONE;
            case OBSIDIAN: return Item.OBSIDIAN;
            case COBALT: return Item.COBALT;
            case MYTHRIL: return Item.MYTHRIL;
            default: return null;
        }
    }

    public static TileType itemToTile(Item it) {
        switch (it) {
            case GRASS: return TileType.GRASS;
            case DIRT: return TileType.DIRT;
            case STONE: return TileType.STONE;
            case COPPER: return TileType.COPPER;
            case IRON: return TileType.IRON;
            case WOOD: return TileType.WOOD;
            case LEAF: return TileType.LEAF;
            case GEL_BLOCK: return TileType.GEL_BLOCK;
            case TORCH: return TileType.TORCH;
            case WORKBENCH: return TileType.WORKBENCH;
            case SAND: return TileType.SAND;
            case SNOW: return TileType.SNOW;
            case ICE: return TileType.ICE;
            case CACTUS: return TileType.CACTUS;
            case JUNGLE_GRASS: return TileType.JUNGLE_GRASS;
            case FURNACE: return TileType.FURNACE;
            case ANVIL: return TileType.ANVIL;
            case GOLD: return TileType.GOLD;
            case ASH: return TileType.ASH;
            case HELLSTONE: return TileType.HELLSTONE;
            case OBSIDIAN: return TileType.OBSIDIAN;
            case COBALT: return TileType.COBALT;
            case MYTHRIL: return TileType.MYTHRIL;
            default: return null;
        }
    }
}
