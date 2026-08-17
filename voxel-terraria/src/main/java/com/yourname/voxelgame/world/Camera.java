package com.yourname.voxelgame.world;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;

import static org.lwjgl.opengl.GL11.*;

/**
 * 正交相机：WASD 平移，滚轮缩放。严格正交，禁止透视。
 *
 * 视野以「视高世界单位」为单位：viewHeight 是屏幕高度对应的世界单位数，
 * 缩放越大 viewHeight 越小（画面放大）。视宽 = viewHeight * aspect。
 */
public class Camera implements GLFWScrollCallbackI {

    private float x, y, z;          // 相机位置（平移）
    private float scale = 1.0f;     // 缩放倍率
    private float baseViewHeight;   // scale=1 时屏幕高度对应的世界单位

    private static final float MIN_SCALE = 0.05f;
    private static final float MAX_SCALE = 20.0f;

    private final int width;
    private final int height;

    public Camera(int width, int height) {
        this.width = width;
        this.height = height;
        // 默认让屏幕高度对应 18 个世界单位，能完整看到 16 高的区块
        this.baseViewHeight = 18.0f;
    }

    /** 每帧调用：根据按键状态更新位置。 */
    public void update(long window, float dt) {
        float v = 10.0f * dt; // 世界单位/秒
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) y += v;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) y -= v;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) x -= v;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) x += v;
    }

    @Override
    public void invoke(long window, double xoffset, double yoffset) {
        // yoffset > 0 向上滚 → 放大
        scale *= (yoffset > 0) ? 1.1f : (1.0f / 1.1f);
        if (scale < MIN_SCALE) scale = MIN_SCALE;
        if (scale > MAX_SCALE) scale = MAX_SCALE;
    }

    /** 应用投影与视图矩阵。 */
    public void apply() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float halfH = halfH();
        float halfW = halfW();
        glOrtho(-halfW, halfW, -halfH, halfH, -200.0f, 200.0f);

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        glTranslatef(-x, -y, -z);
    }

    /** 当前视高的一半（世界单位）。 */
    public float halfH() {
        return (baseViewHeight / scale) * 0.5f;
    }

    /** 当前视宽的一半（世界单位）。 */
    public float halfW() {
        return halfH() * ((float) width / (float) height);
    }

    /**
     * 屏幕像素坐标 → 世界坐标（正交投影下，z 取相机平面的 z）。
     * 鼠标在屏幕 (sx, sy)（左上原点），返回世界 (wx, wy)。
     * 正交投影下，沿 z 方向的射线起点 (wx, wy, 任意 z)，方向 (0,0,-1)。
     */
    public float[] screenToWorld(float sx, float sy) {
        // 屏幕中心为原点，y 翻转
        float ndcX = (sx / (float) width) * 2.0f - 1.0f;
        float ndcY = 1.0f - (sy / (float) height) * 2.0f;
        float wx = x + ndcX * halfW();
        float wy = y + ndcY * halfH();
        return new float[] { wx, wy };
    }

    public void setEye(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public float getScale() { return scale; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
