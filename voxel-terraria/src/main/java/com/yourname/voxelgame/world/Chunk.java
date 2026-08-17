package com.yourname.voxelgame.world;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * 16³ 体素 Chunk，带状态机、异步 mesh 生成与光照缓存。
 * - 数据由 TerrainGenerator 生成，构造时传入
 * - lightMap 缓存每方块光照等级 0-15
 * - mesh 生成需要邻居方块与光照查询（通过 LightProvider 跨 Chunk）
 * - 顶点颜色 = baseColor × (avgLight / 15)，最低 0.1；昼夜调制由渲染时 glColor 全局乘
 */
public class Chunk {

    public static final int SIZE = 16;

    public enum State { EMPTY, GENERATED, MESH_READY, UPLOADED, DISPOSED }

    private final ChunkPos pos;
    private final byte[][][] blocks;      // [x][y][z]
    private final byte[][][] lightMap;    // [x][y][z] 光照等级 0-15

    private State state = State.EMPTY;
    private int vboId;
    private int iboId;
    private int indexCount;
    private int vertexCount;

    private FloatBuffer pendingVerts;
    private ShortBuffer pendingIdxs;

    public Chunk(ChunkPos pos, byte[][][] blocks) {
        this.pos = pos;
        this.blocks = blocks;
        this.lightMap = new byte[SIZE][SIZE][SIZE];
        this.state = State.GENERATED;
    }

    public ChunkPos pos() { return pos; }
    public State state() { return state; }

    public byte getBlock(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) return 0;
        return blocks[x][y][z];
    }

    public byte getLightLocal(int x, int y, int z) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) return 0;
        return lightMap[x][y][z];
    }

    public void setLightLocal(int x, int y, int z, byte level) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) return;
        lightMap[x][y][z] = level;
    }

    public boolean setBlock(int x, int y, int z, byte id) {
        if (x < 0 || x >= SIZE || y < 0 || y >= SIZE || z < 0 || z >= SIZE) return false;
        if (blocks[x][y][z] == id) return false;
        blocks[x][y][z] = id;
        return true;
    }

    /** 工作线程：构建 mesh（跨 Chunk 查询邻居 + 光照）。昼夜调制由渲染时 glColor 全局乘。 */
    public void buildMesh(LightProvider world) {
        FloatBuffer verts = MemoryUtil.memAllocFloat(SIZE * SIZE * SIZE * 6 * 4 * 6);
        ShortBuffer idxs = MemoryUtil.memAllocShort(SIZE * SIZE * SIZE * 6 * 6);
        short v = 0;
        int baseX = pos.cx * SIZE;
        int baseZ = pos.cz * SIZE;

        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    byte id = blocks[x][y][z];
                    if (id == 0) continue;
                    int wx = baseX + x, wy = y, wz = baseZ + z;
                    if (isExposed(world, wx, wy, wz + 1)) v = emit(verts, idxs, v, x, y, z, wx, wy, wz, id, FACE_FRONT, world);
                    if (isExposed(world, wx, wy, wz - 1)) v = emit(verts, idxs, v, x, y, z, wx, wy, wz, id, FACE_BACK, world);
                    if (isExposed(world, wx, wy + 1, wz)) v = emit(verts, idxs, v, x, y, z, wx, wy, wz, id, FACE_TOP, world);
                    if (isExposed(world, wx, wy - 1, wz)) v = emit(verts, idxs, v, x, y, z, wx, wy, wz, id, FACE_BOTTOM, world);
                    if (isExposed(world, wx + 1, wy, wz)) v = emit(verts, idxs, v, x, y, z, wx, wy, wz, id, FACE_RIGHT, world);
                    if (isExposed(world, wx - 1, wy, wz)) v = emit(verts, idxs, v, x, y, z, wx, wy, wz, id, FACE_LEFT, world);
                }
            }
        }
        verts.flip();
        idxs.flip();
        this.pendingVerts = verts;
        this.pendingIdxs = idxs;
        this.vertexCount = v;
        this.indexCount = idxs.limit();
        this.state = State.MESH_READY;
    }

    /** 邻居是空气或透明方块 → 该面暴露。 */
    private static boolean isExposed(LightProvider world, int wx, int wy, int wz) {
        byte b = world.getBlock(wx, wy, wz);
        if (b == 0) return true;
        return !BlockType.byId(b).opaque;
    }

    public void uploadMesh() {
        if (state != State.MESH_READY || pendingVerts == null) return;
        if (vboId != 0) glDeleteBuffers(vboId);
        if (iboId != 0) glDeleteBuffers(iboId);
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, pendingVerts, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        iboId = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, pendingIdxs, GL_STATIC_DRAW);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        MemoryUtil.memFree(pendingVerts);
        MemoryUtil.memFree(pendingIdxs);
        pendingVerts = null;
        pendingIdxs = null;
        state = State.UPLOADED;
    }

    public void render() {
        if (state != State.UPLOADED || indexCount == 0) return;
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        int stride = 6 * Float.BYTES;
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, stride, 0L);
        glEnableClientState(GL_COLOR_ARRAY);
        glColorPointer(3, GL_FLOAT, stride, 3L * Float.BYTES);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId);
        glDrawElements(GL_TRIANGLES, indexCount, GL_UNSIGNED_SHORT, 0L);
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_VERTEX_ARRAY);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    public void dispose() {
        if (vboId != 0) glDeleteBuffers(vboId);
        if (iboId != 0) glDeleteBuffers(iboId);
        if (pendingVerts != null) MemoryUtil.memFree(pendingVerts);
        if (pendingIdxs != null) MemoryUtil.memFree(pendingIdxs);
        vboId = 0; iboId = 0; indexCount = 0;
        pendingVerts = null; pendingIdxs = null;
        state = State.DISPOSED;
    }

    public int getVertexCount() { return vertexCount; }
    public int getIndexCount() { return indexCount; }

    // —— 面定义 ——
    static final int FACE_FRONT = 0, FACE_BACK = 1, FACE_TOP = 2, FACE_BOTTOM = 3, FACE_RIGHT = 4, FACE_LEFT = 5;
    // 每面 4 顶点偏移；每顶点对应的"外侧"邻居偏移（用于取光照平滑）
    private static final float[][] FACE_OFFSETS = new float[6][];
    // 每面 4 顶点各自对应的光照采样邻居偏移（dx,dy,dz）
    private static final int[][][] FACE_LIGHT_NEIGHBORS = new int[6][4][3];
    static {
        FACE_OFFSETS[FACE_FRONT]  = new float[]{0,0,1, 1,0,1, 1,1,1, 0,1,1};
        FACE_OFFSETS[FACE_BACK]   = new float[]{1,0,0, 0,0,0, 0,1,0, 1,1,0};
        FACE_OFFSETS[FACE_TOP]    = new float[]{0,1,1, 1,1,1, 1,1,0, 0,1,0};
        FACE_OFFSETS[FACE_BOTTOM] = new float[]{0,0,0, 1,0,0, 1,0,1, 0,0,1};
        FACE_OFFSETS[FACE_RIGHT]  = new float[]{1,0,1, 1,0,0, 1,1,0, 1,1,1};
        FACE_OFFSETS[FACE_LEFT]   = new float[]{0,0,0, 0,0,1, 0,1,1, 0,1,0};

        // FRONT (+z)：外侧邻居 z+1，4 顶点采样 (x,y,z+1) 周围
        FACE_LIGHT_NEIGHBORS[FACE_FRONT] = new int[][] {
            {-1, 0, 1}, {1, 0, 1}, {1, 1, 1}, {-1, 1, 1}};
        FACE_LIGHT_NEIGHBORS[FACE_BACK] = new int[][] {
            {1, 0, -1}, {-1, 0, -1}, {-1, 1, -1}, {1, 1, -1}};
        FACE_LIGHT_NEIGHBORS[FACE_TOP] = new int[][] {
            {-1, 1, 0}, {1, 1, 0}, {1, 1, -1}, {-1, 1, -1}};
        FACE_LIGHT_NEIGHBORS[FACE_BOTTOM] = new int[][] {
            {-1, -1, 0}, {1, -1, 0}, {1, -1, 1}, {-1, -1, 1}};
        FACE_LIGHT_NEIGHBORS[FACE_RIGHT] = new int[][] {
            {1, 0, 1}, {1, 0, -1}, {1, 1, -1}, {1, 1, 1}};
        FACE_LIGHT_NEIGHBORS[FACE_LEFT] = new int[][] {
            {-1, 0, -1}, {-1, 0, 1}, {-1, 1, 1}, {-1, 1, -1}};
    }

    private static float[] faceColor(BlockType t, int face) {
        float k = (face == FACE_TOP) ? 1.0f : (face == FACE_BOTTOM) ? 0.6f : 0.8f;
        return new float[]{ t.r * k, t.g * k, t.b * k };
    }

    /** 取某世界坐标的光照（用于顶点平滑）。 */
    private static float lightAt(LightProvider world, int wx, int wy, int wz) {
        if (wy < 0) return 0;
        if (wy >= Chunk.SIZE) return LightEngine.SUN_LIGHT; // 上方默认阳光
        return world.getLight(wx, wy, wz) & 0xFF;
    }

    private static short emit(FloatBuffer verts, ShortBuffer idxs, short sv,
                               int bx, int by, int bz, int wx, int wy, int wz,
                               byte id, int face, LightProvider world) {
        float[] o = FACE_OFFSETS[face];
        BlockType t = BlockType.byId(id);
        float[] c = faceColor(t, face);
        int[][] ln = FACE_LIGHT_NEIGHBORS[face];

        // 4 顶点光照采样该顶点外侧邻居方块光照
        float[] light = new float[4];
        for (int i = 0; i < 4; i++) {
            light[i] = lightAt(world, wx + ln[i][0], wy + ln[i][1], wz + ln[i][2]);
        }
        // 取该面 4 顶点平均光照做整面调制（稳定，避免顶点闪烁）；昼夜由渲染时 glColor 全局乘
        float avg = (light[0] + light[1] + light[2] + light[3]) * 0.25f;
        float lf = Math.max(0.1f, avg / 15.0f);

        for (int i = 0; i < 4; i++) {
            verts.put(bx + o[i * 3]);
            verts.put(by + o[i * 3 + 1]);
            verts.put(bz + o[i * 3 + 2]);
            verts.put(c[0] * lf);
            verts.put(c[1] * lf);
            verts.put(c[2] * lf);
        }
        idxs.put((short) (sv + 0)); idxs.put((short) (sv + 1)); idxs.put((short) (sv + 2));
        idxs.put((short) (sv + 2)); idxs.put((short) (sv + 3)); idxs.put((short) (sv + 0));
        return (short) (sv + 4);
    }
}
