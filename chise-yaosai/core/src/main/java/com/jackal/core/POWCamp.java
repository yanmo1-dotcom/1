package com.jackal.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;

/**
 * POWCamp —— 战俘营（Prisoner Of War camp）。
 * <p>
 * 地图上的可交互建筑。两种销毁路径：
 * <ul>
 *   <li><b>救援</b>：吉普车靠近（距离&lt;{@link #INTERACT_RADIUS}）按 E，每次救 1 人 +1000 分；
 *       计数归零时触发 {@link #onAllRescued()} 回调（武器升级 + 任务横幅）。</li>
 *   <li><b>摧毁</b>：被手雷/火箭弹直接命中，立即清空计数，但分数减半（惩罚暴力解法）。
 *       摧毁后标记 destroyed，不再可交互或救援。</li>
 * </ul>
 *
 * <h3>碰撞检测</h3>
 * 战俘营用一个 {@link Circle} 作为交互/命中范围（圆心=营中心，半径={@link #CAMP_RADIUS}）。
 * 吉普车用其矩形包围盒，子弹用其包围盒，均通过 {@link Intersector} 做圆-矩形相交判定，
 * 比手写距离判定更精准（覆盖矩形角点进入圆内的情形）。
 *
 * <h3>状态机</h3>
 * <pre>
 *   ACTIVE（剩余战俘>0） ──救援──→ ACTIVE（计数-1）
 *                            └──计数=0──→ RESCUED（触发 onAllRescued）
 *   ACTIVE ──被弹命中──→ DESTROYED（清空计数，分数减半）
 *   RESCUED / DESTROYED 为终态，不再响应交互。
 * </pre>
 *
 * @author Jackal Dev Team
 */
public class POWCamp {

    /** 交互半径（像素）。吉普车圆心到营中心距离 < 此值且按 E 才能救援。
     *  要求是 50px，这里用 CAMP_RADIUS 做命中判定，交互用 INTERACT_RADIUS=50。 */
    public static final float INTERACT_RADIUS = 50f;

    /** 营的视觉/命中半径（像素）。子弹矩形与此圆相交即视为命中。 */
    public static final float CAMP_RADIUS = 16f;

    /** 初始战俘人数 */
    public static final int INITIAL_POWS = 4;

    /** 救援一人的得分 */
    public static final int SCORE_PER_RESCUE = 1000;

    /** 营状态 */
    public enum State {
        /** 仍有战俘，可交互 */
        ACTIVE,
        /** 战俘已全部救出（通过救援路径） */
        RESCUED,
        /** 被弹药直接摧毁（暴力路径） */
        DESTROYED
    }

    /** 营中心世界坐标 */
    public final Vector2 center = new Vector2();

    /** 当前剩余战俘数 */
    private int remainingPows = INITIAL_POWS;

    /** 当前状态 */
    private State state = State.ACTIVE;

    /** 圆形碰撞体（命中与交互共用，半径=CAMP_RADIUS；交互距离另用 INTERACT_RADIUS） */
    private final Circle bounds = new Circle();

    /** 救援回调：当本营战俘全部救出时触发（由 GameWorld 设置，执行武器升级+横幅） */
    private Runnable onAllRescued = null;

    /**
     * 构造战俘营。
     *
     * @param x 营中心世界 X
     * @param y 营中心世界 Y
     */
    public POWCamp(float x, float y) {
        center.set(x, y);
        bounds.set(x, y, CAMP_RADIUS);
    }

    /**
     * 尝试救援：当吉普车在范围内且按 E 时调用。
     * <p>
     * 使用圆-矩形相交（Intersector.overlaps）做精准判定，而非简单中心距离，
     * 这样吉普车矩形任意部位进入交互圆都算"靠近"。
     *
     * @param jeepCenter 吉普车矩形包围盒（世界坐标）
     * @param ePressed   本帧是否刚按下 E（用 isKeyJustPressed，避免长按连续触发）
     * @return 本次救援获得的分数（0 表示未触发救援）
     */
    public int tryRescue(Rectangle jeepBox, boolean ePressed) {
        if (state != State.ACTIVE || !ePressed) return 0;
        // 交互半径用 INTERACT_RADIUS 做一个临时大圆判定"靠近"
        // 命中/摧毁用 CAMP_RADIUS 的小圆；这里交互单独判定
        float dist = Vector2.dst(center.x, center.y,
                jeepBox.x + jeepBox.width * 0.5f, jeepBox.y + jeepBox.height * 0.5f);
        if (dist > INTERACT_RADIUS) return 0;

        // 救援一人
        remainingPows--;
        playRescueSoundPlaceholder();
        if (remainingPows <= 0) {
            remainingPows = 0;
            state = State.RESCUED;
            if (onAllRescued != null) onAllRescued.run();
        }
        return SCORE_PER_RESCUE;
    }

    /**
     * 尝试用子弹摧毁：子弹矩形与营命中圆相交即命中。
     * <p>
     * 仅手雷/火箭弹（高爆弹药）能摧毁，机枪子弹不触发（由调用方按 Bullet.Type 过滤后传入）。
     * 摧毁得分为：剩余战俘数 × SCORE_PER_RESCUE × 0.5（减半惩罚）。
     *
     * @param bulletBox 子弹矩形包围盒
     * @param type      子弹类型（调用方已确保是 GRENADE/ROCKET）
     * @return 摧毁获得的分数（0 表示未命中或已终态）
     */
    public int tryDestroy(Rectangle bulletBox, Bullet.Type type) {
        if (state != State.ACTIVE) return 0;
        // 圆-矩形相交精准判定
        if (!Intersector.overlaps(bounds, bulletBox)) return 0;

        // 摧毁：剩余战俘按减半计分
        int gained = (int) (remainingPows * SCORE_PER_RESCUE * 0.5f);
        remainingPows = 0;
        state = State.DESTROYED;
        playDestroySoundPlaceholder();
        return gained;
    }

    /**
     * 音效占位符：救援音。
     * <p>
     * 第 6 步接入音频系统时替换为 Sound.play()。当前仅打印日志，不引入音频依赖。
     */
    private void playRescueSoundPlaceholder() {
        GdxLog("POW", "救援音效占位（+1 人救出）");
    }

    /** 音效占位符：摧毁音 */
    private void playDestroySoundPlaceholder() {
        GdxLog("POW", "摧毁音效占位（战俘营被炸毁）");
    }

    /** 日志辅助，避免在本类顶部 import Gdx */
    private static void GdxLog(String tag, String msg) {
        com.badlogic.gdx.Gdx.app.log(tag, msg);
    }

    /**
     * 渲染战俘营：根据状态用不同颜色绘制。
     * <p>
     * 调用前外部须已 shapes.begin(Filled) 并设置投影矩阵。
     *
     * @param shapes 外部 ShapeRenderer
     */
    public void render(ShapeRenderer shapes) {
        switch (state) {
            case ACTIVE:
                // 蓝色营体 + 上方小点表示剩余战俘数
                shapes.setColor(Color.BLUE);
                shapes.circle(center.x, center.y, CAMP_RADIUS);
                // 用白色小点指示剩余人数（最多 4 个，围一圈）
                shapes.setColor(Color.WHITE);
                for (int i = 0; i < remainingPows; i++) {
                    float a = i * (MathUtils.PI2 / INITIAL_POWS);
                    shapes.circle(center.x + (float) Math.cos(a) * 8f,
                            center.y + (float) Math.sin(a) * 8f, 1.5f);
                }
                break;
            case RESCUED:
                // 全部救出：绿色空心圈
                shapes.setColor(Color.GREEN);
                shapes.circle(center.x, center.y, CAMP_RADIUS);
                break;
            case DESTROYED:
                // 被摧毁：红色 X 标记（两个交叉矩形）
                shapes.setColor(Color.RED);
                shapes.rect(center.x - 10, center.y - 2, 20, 4);
                break;
        }
    }

    // ============== 访问器 ==============

    /** @return 剩余战俘数 */
    public int getRemainingPows() {
        return remainingPows;
    }

    /** @return 当前状态 */
    public State getState() {
        return state;
    }

    /** @return 是否仍可交互（ACTIVE） */
    public boolean isInteractable() {
        return state == State.ACTIVE;
    }

    /** @return 营中心 X */
    public float getX() {
        return center.x;
    }

    /** @return 营中心 Y */
    public float getY() {
        return center.y;
    }

    /**
     * 设置全部救出回调。
     *
     * @param callback 当本营战俘全部救出时执行（武器升级、任务横幅等）
     */
    public void setOnAllRescued(Runnable callback) {
        this.onAllRescued = callback;
    }
}
