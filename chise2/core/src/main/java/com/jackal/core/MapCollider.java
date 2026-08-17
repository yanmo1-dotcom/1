package com.jackal.core;

import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;

/**
 * MapCollider —— 地图碰撞检测与响应。
 * <p>
 * 读取 {@link TileMapAssets#LAYER_COLLISION} 层，把其中非空瓦片视为 32x32 的实心方块。
 * 提供：
 * <ul>
 *   <li>{@link #collides(Rectangle)} —— 矩形是否与任意墙壁瓦片重叠（子弹/实体判定）</li>
 *   <li>{@link #resolve(Rectangle, float, float)} —— 对移动实体做轴分离碰撞响应（吉普车滑墙）</li>
 * </ul>
 *
 * <h3>轴分离碰撞响应推导</h3>
 * 实体从旧位置移动到新位置后若与墙重叠，需要把它"推回"非重叠位置。
 * 采用分轴解决（resolve each axis independently），简单且能产生"沿墙滑动"手感：
 * <pre>
 *   1) 先只沿 X 移动：若新 X 包围盒与墙重叠 → 回退 X（视为 X 方向被阻挡）
 *   2) 再只沿 Y 移动：若新 Y 包围盒与墙重叠 → 回退 Y
 * </pre>
 * 这样对角撞墙时能保留另一轴的位移，实现"滑行"而非"卡死"。
 *
 * <h3>瓦片查询优化</h3>
 * 实体包围盒覆盖的瓦片范围有限，只遍历该范围（minX..maxX, minY..maxY）内的瓦片，
 * 而非整张地图，保证 O(覆盖瓦片数) 而非 O(全图)。
 *
 * @author Jackal Dev Team
 */
public class MapCollider {

    /** 碰撞图层引用 */
    private final TiledMapTileLayer collisionLayer;
    /** 单瓦片像素尺寸（用于把世界坐标换算成瓦片索引） */
    private final float tileW;
    private final float tileH;

    /**
     * @param assets 地图资源（从中取 Collision 层与瓦片尺寸）
     */
    public MapCollider(TileMapAssets assets) {
        this.collisionLayer = assets.getLayer(TileMapAssets.LAYER_COLLISION);
        this.tileW = assets.getTilePixelWidth();
        this.tileH = assets.getTilePixelHeight();
    }

    /**
     * 判断矩形是否与任意墙壁瓦片重叠。
     * <p>
     * 遍历矩形覆盖的瓦片范围，任一瓦片非空即碰撞。
     *
     * @param r 世界坐标矩形
     * @return true 表示与墙重叠
     */
    public boolean collides(Rectangle r) {
        // 把矩形边界换算为瓦片索引（向下取整左下，向上取整右上）
        int minX = MathUtils.floor(r.x / tileW);
        int minY = MathUtils.floor(r.y / tileH);
        int maxX = MathUtils.floor((r.x + r.width) / tileW);
        int maxY = MathUtils.floor((r.y + r.height) / tileH);

        for (int ty = minY; ty <= maxY; ty++) {
            for (int tx = minX; tx <= maxX; tx++) {
                if (isSolid(tx, ty)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 对移动实体做分轴碰撞响应，返回允许的实际位移增量。
     * <p>
     * 调用方用法（吉普车）：
     * <pre>
     *   Vector2 allowed = collider.resolve(jeepBox, deltaX, deltaY);
     *   jeep.position.x += allowed.x;
     *   jeep.position.y += allowed.y;
     * </pre>
     *
     * @param box    实体当前包围盒（碰撞前位置）
     * @param dx     本帧期望 X 位移
     * @param dy     本帧期望 Y 位移
     * @return 实际允许的位移（存入复用的 Vector2，调用方勿持有跨帧）
     */
    public com.badlogic.gdx.math.Vector2 resolve(Rectangle box, float dx, float dy) {
        // —— X 轴：尝试移动 dx ——
        Rectangle xBox = new Rectangle(box.x + dx, box.y, box.width, box.height);
        float allowedX = dx;
        if (dx != 0f && collides(xBox)) {
            // X 被阻挡：贴到最近墙边。
            // dx>0 向右撞墙 → 实体右边贴墙左边；dx<0 向左撞墙 → 实体左边贴墙右边。
            if (dx > 0f) {
                allowedX = wallLeftOf(xBox) - (box.x + box.width);
            } else {
                allowedX = wallRightOf(xBox) - box.x;
            }
            // 数值保护：贴墙计算可能因浮点产生极小反向位移，钳到与 dx 同向或 0
            if (dx > 0f && allowedX < 0f) allowedX = 0f;
            if (dx < 0f && allowedX > 0f) allowedX = 0f;
        }

        // —— Y 轴：在 X 已解决后的基础上尝试移动 dy ——
        Rectangle yBox = new Rectangle(box.x + allowedX, box.y + dy, box.width, box.height);
        float allowedY = dy;
        if (dy != 0f && collides(yBox)) {
            if (dy > 0f) {
                allowedY = wallBelow(yBox) - (box.y + box.height);
            } else {
                allowedY = wallAbove(yBox) - box.y;
            }
            if (dy > 0f && allowedY < 0f) allowedY = 0f;
            if (dy < 0f && allowedY > 0f) allowedY = 0f;
        }

        // 返回复用向量（避免调用方 new）。注意非线程安全，单线程渲染循环内使用。
        return TMP.set(allowedX, allowedY);
    }

    /** 复用向量，避免 resolve 每次分配 */
    private final com.badlogic.gdx.math.Vector2 TMP = new com.badlogic.gdx.math.Vector2();

    // ===== 贴墙计算：找到阻挡实体某侧的最近墙边坐标 =====

    /** 实体向右移动被挡时，墙的左边界 X（像素） */
    private float wallLeftOf(Rectangle r) {
        int tx = MathUtils.floor((r.x + r.width) / tileW);
        return tx * tileW;
    }

    /** 实体向左移动被挡时，墙的右边界 X（像素） */
    private float wallRightOf(Rectangle r) {
        int tx = MathUtils.floor(r.x / tileW);
        return (tx + 1) * tileW;
    }

    /** 实体向上移动被挡时，墙的下边界 Y（像素） */
    private float wallBelow(Rectangle r) {
        int ty = MathUtils.floor((r.y + r.height) / tileH);
        return ty * tileH;
    }

    /** 实体向下移动被挡时，墙的上边界 Y（像素） */
    private float wallAbove(Rectangle r) {
        int ty = MathUtils.floor(r.y / tileH);
        return (ty + 1) * tileH;
    }

    /**
     * 指定瓦片格是否为实心墙（非空即实心）。
     * 越界（地图外）视为实心，防止实体跑出地图。
     */
    private boolean isSolid(int tx, int ty) {
        // 地图外视为实心墙
        if (tx < 0 || ty < 0 || tx >= collisionLayer.getWidth()
                || ty >= collisionLayer.getHeight()) {
            return true;
        }
        // Cell 为空（null）表示该格无瓦片，不阻挡
        TiledMapTileLayer.Cell cell = collisionLayer.getCell(tx, ty);
        return cell != null && cell.getTile() != null;
    }

    /**
     * 查询某世界像素点是否落在实心墙上（供随机生成敌人时避开墙体用）。
     * <p>
     * 把像素坐标换算为瓦片索引后调用内部 isSolid。
     *
     * @param px 世界 X（像素）
     * @param py 世界 Y（像素）
     * @return true 表示该点在墙内或地图外
     */
    public boolean isSolidAtPixel(float px, float py) {
        int tx = MathUtils.floor(px / tileW);
        int ty = MathUtils.floor(py / tileH);
        return isSolid(tx, ty);
    }
}