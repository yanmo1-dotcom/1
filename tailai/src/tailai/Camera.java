package tailai;

/** 摄像机：平滑跟随玩家并限制在世界边界内。 */
public class Camera {
    public float x, y;

    public Camera() {
        this.x = 0;
        this.y = 0;
    }

    public void follow(float targetX, float targetY, float viewW, float viewH,
                       float worldW, float worldH, float dt) {
        float tx = targetX - viewW / 2f;
        float ty = targetY - viewH / 2f;
        float k = Math.min(1f, 16f * dt);
        x += (tx - x) * k;
        y += (ty - y) * k;
        x = clamp(x, 0, Math.max(0, worldW - viewW));
        y = clamp(y, 0, Math.max(0, worldH - viewH));
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (Math.min(v, hi));
    }

    public float screenX(float worldX) {
        return worldX - x;
    }

    public float screenY(float worldY) {
        return worldY - y;
    }
}
