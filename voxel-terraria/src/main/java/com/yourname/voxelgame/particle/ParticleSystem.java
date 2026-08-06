package com.yourname.voxelgame.particle;

import com.yourname.voxelgame.world.BlockAccess;
import com.yourname.voxelgame.world.BlockType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/**
 * 粒子系统：破坏方块时飞溅小方块粒子，受重力，约 0.5 秒消失。
 */
public class ParticleSystem {

    private static final float GRAVITY = -12f;
    private static final float LIFE = 0.6f;

    private final List<Particle> particles = new ArrayList<>();

    private static final class Particle {
        float x, y, z;
        float vx, vy, vz;
        float life;
        float r, g, b;
        float size;
    }

    /** 在 (wx, wy, wz) 位置根据方块颜色爆发 N 个粒子。 */
    public void burstBlock(float wx, float wy, float wz, int blockId, int count) {
        BlockType t = BlockType.byId(blockId);
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            p.x = wx; p.y = wy; p.z = wz;
            // 随机初速（用 hashCode 做伪随机避免 Math.random）
            long s = System.nanoTime() + i * 7919L;
            p.vx = rand(s, -2f, 2f);
            p.vy = rand(s >> 7, 1f, 4f);
            p.vz = rand(s >> 13, -0.5f, 0.5f);
            p.life = LIFE * (0.7f + rand(s >> 19, 0f, 0.3f));
            p.r = t.r; p.g = t.g; p.b = t.b;
            p.size = 0.08f;
            particles.add(p);
        }
    }

    private static float rand(long seed, float min, float span) {
        long r = (seed * 6364136223846793005L + 1442695040888963407L) >>> 33;
        return min + (r & 0xFFFF) / 65535.0f * span;
    }

    public void update(float dt) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) { it.remove(); continue; }
            p.vy += GRAVITY * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.z += p.vz * dt;
            p.vx *= 0.96f;
            p.vz *= 0.96f;
        }
    }

    public void render() {
        glBegin(GL_QUADS);
        for (Particle p : particles) {
            glColor3f(p.r, p.g, p.b);
            float s = p.size;
            // 面向 +z 的简单方块（正交侧视）
            glVertex3f(p.x - s, p.y - s, p.z);
            glVertex3f(p.x + s, p.y - s, p.z);
            glVertex3f(p.x + s, p.y + s, p.z);
            glVertex3f(p.x - s, p.y + s, p.z);
        }
        glEnd();
        glColor3f(1f, 1f, 1f);
    }

    public int count() { return particles.size(); }
    public void clear() { particles.clear(); }
}
