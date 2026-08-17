package com.yourname.voxelgame.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.lwjgl.opengl.GL11.glPushMatrix;
import static org.lwjgl.opengl.GL11.glPopMatrix;
import static org.lwjgl.opengl.GL11.glTranslatef;

/**
 * Chunk 管理器：以玩家所在 Chunk 为中心，维护 5×5 范围的 Chunk。
 * - 异步用线程池生成地形 + mesh
 * - 主线程每帧上传最多 2 个待上传 mesh
 * - 实现 BlockAccess 提供跨 Chunk 方块查询
 * - 流式加载/卸载（dispose 释放 GL buffer）
 */
public class ChunkManager implements BlockAccess, LightProvider {

    public static final int LOAD_RADIUS = 2; // 5×5
    private static final int MAX_UPLOADS_PER_FRAME = 2;
    private static final int THREAD_COUNT = 3;

    private final Map<ChunkPos, Chunk> chunks = new HashMap<>();
    private final Map<ChunkPos, Future<?>> pending = new HashMap<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
    private final TerrainGenerator generator;

    private int centerCX = Integer.MIN_VALUE;
    private int centerCZ = Integer.MIN_VALUE;

    // 存档 diff：所有玩家修改过的方块（世界坐标 → id）
    private final java.util.Map<Long, Byte> edits = new java.util.HashMap<>();
    // 待应用 diff（按 chunk 分组）：chunk pos long key → list of edits
    private final java.util.Map<Long, java.util.List<int[]>> pendingEdits = new java.util.HashMap<>();

    private static long cpKey(int cx, int cz) { return ((long) cx << 32) | (cz & 0xFFFFFFFFL); }
    private static long wKey(int x, int y, int z) { return ((long)(x & 0x3FFFF)) | (((long)y & 0xF) << 18) | (((long)z & 0x3FFFF) << 22); }

    public ChunkManager(long seed) {
        this.generator = new TerrainGenerator(seed);
    }

    /** 每帧调用：根据玩家世界坐标更新加载中心，启动/卸载 chunk。 */
    public void update(float playerX, float playerZ) {
        int ncx = (int) Math.floor(playerX / Chunk.SIZE);
        int ncz = (int) Math.floor(playerZ / Chunk.SIZE);
        if (ncx == centerCX && ncz == centerCZ) {
            // 中心未变，仍处理上传
            processUploads();
            return;
        }
        centerCX = ncx;
        centerCZ = ncz;

        // 卸载超出半径的 chunk
        int minCX = centerCX - LOAD_RADIUS, maxCX = centerCX + LOAD_RADIUS;
        int minCZ = centerCZ - LOAD_RADIUS, maxCZ = centerCZ + LOAD_RADIUS;
        synchronized (chunks) {
            Iterator<Map.Entry<ChunkPos, Chunk>> it = chunks.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ChunkPos, Chunk> e = it.next();
                ChunkPos p = e.getKey();
                if (p.cx < minCX || p.cx > maxCX || p.cz < minCZ || p.cz > maxCZ) {
                    e.getValue().dispose();
                    it.remove();
                }
            }
        }

        // 加载半径内缺失的 chunk
        for (int dx = -LOAD_RADIUS; dx <= LOAD_RADIUS; dx++) {
            for (int dz = -LOAD_RADIUS; dz <= LOAD_RADIUS; dz++) {
                ChunkPos cp = new ChunkPos(centerCX + dx, centerCZ + dz);
                if (!chunks.containsKey(cp) && !pending.containsKey(cp)) {
                    submitGenerate(cp);
                }
            }
        }

        processUploads();
    }

    /** 提交异步生成 + mesh 任务。 */
    private void submitGenerate(ChunkPos cp) {
        Future<?> f = pool.submit(() -> {
            byte[][][] data = generator.generate(cp);
            Chunk chunk = new Chunk(cp, data);
            // 先放入表，让光照计算的跨 chunk 查询能看到自己
            synchronized (chunks) {
                chunks.put(cp, chunk);
            }
            // 应用存档 diff（若有）
            java.util.List<int[]> pend = pendingEdits.get(cpKey(cp.cx, cp.cz));
            if (pend != null) {
                for (int[] e : pend) {
                    int lx = e[0] - cp.cx * Chunk.SIZE;
                    int lz = e[2] - cp.cz * Chunk.SIZE;
                    if (e[1] >= 0 && e[1] < Chunk.SIZE) chunk.setBlock(lx, e[1], lz, (byte) e[3]);
                }
            }
            // 计算该 chunk 的太阳光 + 点光源（跨 chunk 查邻居）
            LightEngine.computeSunlight(this, cp.cx, cp.cx, cp.cz, cp.cz);
            // 火把自然不生成，无需扫描点光源；玩家放置时单独传播
            // 生成 mesh（依赖光照）
            chunk.buildMesh(this);
            pending.remove(cp);
        });
        pending.put(cp, f);
    }

    /** 主线程：上传就绪的 mesh，每帧最多 N 个。 */
    private void processUploads() {
        int uploaded = 0;
        // 复制一份避免并发修改
        List<Chunk> snapshot;
        synchronized (chunks) {
            snapshot = new ArrayList<>(chunks.values());
        }
        for (Chunk c : snapshot) {
            if (c.state() == Chunk.State.MESH_READY) {
                c.uploadMesh();
                uploaded++;
                if (uploaded >= MAX_UPLOADS_PER_FRAME) break;
            }
        }
    }

    // —— BlockAccess ——
    @Override
    public byte getBlock(int wx, int wy, int wz) {
        if (wy < 0 || wy >= Chunk.SIZE) return 0;
        int cx = (int) Math.floor((float) wx / Chunk.SIZE);
        int cz = (int) Math.floor((float) wz / Chunk.SIZE);
        int lx = wx - cx * Chunk.SIZE;
        int lz = wz - cz * Chunk.SIZE;
        Chunk c;
        synchronized (chunks) {
            c = chunks.get(new ChunkPos(cx, cz));
        }
        if (c == null) return 0;
        return c.getBlock(lx, wy, lz);
    }

    // —— LightProvider ——
    @Override
    public byte getLight(int wx, int wy, int wz) {
        if (wy < 0) return 0;
        if (wy >= Chunk.SIZE) return (byte) LightEngine.SUN_LIGHT;
        int cx = (int) Math.floor((float) wx / Chunk.SIZE);
        int cz = (int) Math.floor((float) wz / Chunk.SIZE);
        int lx = wx - cx * Chunk.SIZE;
        int lz = wz - cz * Chunk.SIZE;
        Chunk c;
        synchronized (chunks) {
            c = chunks.get(new ChunkPos(cx, cz));
        }
        if (c == null) return 0;
        return c.getLightLocal(lx, wy, lz);
    }

    @Override
    public void setLight(int wx, int wy, int wz, byte level) {
        if (wy < 0 || wy >= Chunk.SIZE) return;
        int cx = (int) Math.floor((float) wx / Chunk.SIZE);
        int cz = (int) Math.floor((float) wz / Chunk.SIZE);
        int lx = wx - cx * Chunk.SIZE;
        int lz = wz - cz * Chunk.SIZE;
        Chunk c;
        synchronized (chunks) {
            c = chunks.get(new ChunkPos(cx, cz));
        }
        if (c == null) return;
        c.setLightLocal(lx, wy, lz, level);
    }

    /** 世界坐标设方块（破坏/放置）。返回是否成功。 */
    public boolean setBlock(int wx, int wy, int wz, byte id) {
        if (wy < 0 || wy >= Chunk.SIZE) return false;
        int cx = (int) Math.floor((float) wx / Chunk.SIZE);
        int cz = (int) Math.floor((float) wz / Chunk.SIZE);
        int lx = wx - cx * Chunk.SIZE;
        int lz = wz - cz * Chunk.SIZE;
        Chunk c;
        synchronized (chunks) {
            c = chunks.get(new ChunkPos(cx, cz));
        }
        if (c == null) return false;
        boolean changed = c.setBlock(lx, wy, lz, id);
        if (changed) {
            // 记录存档 diff
            edits.put(wKey(wx, wy, wz), id);
            // 光照增量更新：重算该 chunk 及邻居的太阳光 + 点光源
            recomputeLightAround(cx, cz);
            // 标记重建 mesh（异步，依赖新光照）
            rebuildMesh(c);
            markNeighborDirty(c, lx, lz);
        }
        return changed;
    }

    /** 从存档加载方块修改 diff（chunk 生成后自动应用）。 */
    public void loadEdits(java.util.List<com.yourname.voxelgame.save.SaveManager.BlockEdit> blockEdits) {
        for (var e : blockEdits) {
            edits.put(wKey(e.x, e.y, e.z), e.id);
            int cx = (int) Math.floor((float) e.x / Chunk.SIZE);
            int cz = (int) Math.floor((float) e.z / Chunk.SIZE);
            pendingEdits.computeIfAbsent(cpKey(cx, cz), k -> new java.util.ArrayList<>())
                .add(new int[]{e.x, e.y, e.z, e.id});
        }
    }

    /** 导出所有方块修改（用于存档）。 */
    public java.util.List<com.yourname.voxelgame.save.SaveManager.BlockEdit> exportEdits() {
        java.util.List<com.yourname.voxelgame.save.SaveManager.BlockEdit> out = new java.util.ArrayList<>();
        for (var entry : edits.entrySet()) {
            long k = entry.getKey();
            int x = (int)(k & 0x3FFFF); if (x >= (1<<17)) x -= (1<<18);
            int y = (int)((k >>> 18) & 0xF);
            int z = (int)((k >>> 22) & 0x3FFFF); if (z >= (1<<17)) z -= (1<<18);
            out.add(new com.yourname.voxelgame.save.SaveManager.BlockEdit(x, y, z, entry.getValue()));
        }
        return out;
    }

    /** 重算 (cx,cz) 周围 3×3 chunk 的光照。 */
    private void recomputeLightAround(int cx, int cz) {
        LightEngine.recomputeArea(this, cx - 1, cx + 1, cz - 1, cz + 1);
    }

    private void rebuildMesh(Chunk c) {
        submitRebuild(c);
    }

    private void submitRebuild(Chunk c) {
        if (c.state() == Chunk.State.DISPOSED) return;
        // 已有 pending 的 rebuild 不重复提交（用 chunk 自身状态防重）
        pool.submit(() -> {
            c.buildMesh(this);
        });
    }

    private void markNeighborDirty(Chunk c, int lx, int lz) {
        if (lx == 0) markChunkDirty(c.pos().cx - 1, c.pos().cz);
        if (lx == Chunk.SIZE - 1) markChunkDirty(c.pos().cx + 1, c.pos().cz);
        if (lz == 0) markChunkDirty(c.pos().cx, c.pos().cz - 1);
        if (lz == Chunk.SIZE - 1) markChunkDirty(c.pos().cx, c.pos().cz + 1);
    }

    private void markChunkDirty(int cx, int cz) {
        Chunk c;
        synchronized (chunks) {
            c = chunks.get(new ChunkPos(cx, cz));
        }
        if (c != null) submitRebuild(c);
    }

    /** 渲染所有已上传的 chunk。每个 chunk 顶点是局部坐标，需平移到世界位置。 */
    public void render() {
        List<Chunk> snapshot;
        synchronized (chunks) {
            snapshot = new ArrayList<>(chunks.values());
        }
        for (Chunk c : snapshot) {
            if (c.state() != Chunk.State.UPLOADED) continue;
            glPushMatrix();
            glTranslatef(c.pos().cx * Chunk.SIZE, 0f, c.pos().cz * Chunk.SIZE);
            c.render();
            glPopMatrix();
        }
    }

    /** 关闭线程池并释放所有 chunk。 */
    public void shutdown() {
        pool.shutdownNow();
        for (Chunk c : chunks.values()) c.dispose();
        chunks.clear();
    }

    public int getLoadedCount() { return chunks.size(); }
    public int getPendingCount() { return pending.size(); }
}
