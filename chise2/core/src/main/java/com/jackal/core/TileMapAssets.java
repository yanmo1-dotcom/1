package com.jackal.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

/**
 * TileMapAssets —— 瓦片地图资源加载与元数据访问。
 * <p>
 * 封装 {@link TiledMap} 的加载、尺寸查询、按图层名获取瓦片层，以及 dispose 生命周期。
 * 把 LibGDX Tiled API 的细节集中在此，其余系统（渲染器、碰撞器）只依赖本类的简洁接口。
 *
 * <h3>assets 目录与 VS Code 配置要点</h3>
 * <ul>
 *   <li>.tmx / .tsx / .png 均放在 {@code core/assets/tiles/} 下。</li>
 *   <li>LibGDX 通过 {@code Gdx.files.internal(...)} 加载，路径相对于运行时工作目录。
 *       desktop 模块 build.gradle 的 run.workingDir 已设为 {@code ../core/assets}，
 *       故 internal 路径写 {@code tiles/level1.tmx} 即可。</li>
 *   <li>VS Code 中 .tmx/.tsx 是 XML，默认无高亮；安装 "Tiled Map Editor" 不影响 VS Code。
 *       可装 "XML" 扩展改善编辑体验，但非必须。</li>
 *   <li>构建时 core/build.gradle 已把 assets 加入 resources，jar 会包含地图文件。</li>
 * </ul>
 *
 * @author Jackal Dev Team
 */
public class TileMapAssets {

    /** 地图文件 internal 路径（相对 assets 根） */
    private static final String MAP_PATH = "tiles/level1.tmx";

    /** LibGDX TiledMap 对象，由 TmxMapLoader 解析 .tmx 得到 */
    private final TiledMap map;

    /** 地图属性缓存：宽（瓦片数） */
    private final int tilesWide;
    /** 地图属性缓存：高（瓦片数） */
    private final int tilesHigh;
    /** 单瓦片像素宽 */
    private final int tilePixelWidth;
    /** 单瓦片像素高 */
    private final int tilePixelHeight;
    /** 地图世界像素宽 = tilesWide * tilePixelWidth */
    private final int worldPixelWidth;
    /** 地图世界像素高 = tilesHigh * tilePixelHeight */
    private final int worldPixelHeight;

    /** 图层名常量，与 level1.tmx 中的 layer name 严格一致 */
    public static final String LAYER_GROUND = "Ground";
    public static final String LAYER_COLLISION = "Collision";
    public static final String LAYER_POW = "POW";

    /**
     * 加载默认地图（level1.tmx）。
     * <p>
     * 使用 {@link InternalFileHandleResolver} 让 loader 从 internal 路径解析
     * tsx 引用的相对 PNG（即 tileset.png 与 tmx 同目录）。
     */
    public TileMapAssets() {
        this(MAP_PATH);
    }

    /**
     * 加载指定路径的地图。
     *
     * @param mapPath 关卡 tmx 的 internal 路径（如 "tiles/level2.tmx"）
     */
    public TileMapAssets(String mapPath) {
        // TmxMapLoader 默认即用 InternalFileHandleResolver，这里显式构造以示清晰
        TmxMapLoader loader = new TmxMapLoader(new InternalFileHandleResolver());

        // 校验文件存在，给出清晰错误（否则 LibGDX 抛 NPE 难以排查）
        FileHandle fh = Gdx.files.internal(mapPath);
        if (!fh.exists()) {
            throw new RuntimeException("地图文件不存在: " + mapPath
                    + "（检查 run.workingDir 是否指向 core/assets）");
        }

        this.map = loader.load(mapPath);

        // 从地图属性读取尺寸（Tiled 中 map width/height/tilewidth/tileheight）
        // 用无 Class 参数的 get(name) + 强转，避免反射式 get(name, Class)
        // （GWT 后端不支持反射，那个重载在网页版会编译失败）
        MapProperties props = map.getProperties();
        this.tilesWide = (Integer) props.get("width");
        this.tilesHigh = (Integer) props.get("height");
        this.tilePixelWidth = (Integer) props.get("tilewidth");
        this.tilePixelHeight = (Integer) props.get("tileheight");
        this.worldPixelWidth = tilesWide * tilePixelWidth;
        this.worldPixelHeight = tilesHigh * tilePixelHeight;

        Gdx.app.log("Map", "地图加载完成: " + tilesWide + "x" + tilesHigh
                + " 瓦片, 世界 " + worldPixelWidth + "x" + worldPixelHeight + "px");
    }

    /** @return 原始 TiledMap（供渲染器使用） */
    public TiledMap getMap() {
        return map;
    }

    /** 按名称获取瓦片图层。封装类型转换，调用方直接拿到 TiledMapTileLayer */
    public TiledMapTileLayer getLayer(String name) {
        return (TiledMapTileLayer) map.getLayers().get(name);
    }

    /**
     * 从指定 Object Layer 读取所有点对象的坐标，作为路径点数组。
     * <p>
     * 第 7 步新增：坦克巡逻路径不再硬编码，而是在 Tiled 中用 Object Layer
     * （本地图中名为 "TankPath"）放置若干 type="waypoint" 的点对象，
     * 本方法把它们按地图中出现的顺序读出为 Vector2[]。
     * <p>
     * Tiled 的点对象在 LibGDX 中解析为 {@link RectangleMapObject}（宽高=0），
     * 其 x/y 即点坐标（像素，左下原点，与瓦片世界坐标一致）。
     *
     * @param objectLayerName Object Layer 名称（如 "TankPath"）
     * @return 路径点数组；若该层不存在或无对象，返回空数组（非 null）
     */
    public Vector2[] getWaypoints(String objectLayerName) {
        MapLayer layer = map.getLayers().get(objectLayerName);
        if (layer == null) {
            Gdx.app.log("Map", "Object Layer 不存在: " + objectLayerName + "（返回空路径）");
            return new Vector2[0];
        }
        Array<Vector2> pts = new Array<>(false, 8);
        for (RectangleMapObject obj : layer.getObjects().getByType(RectangleMapObject.class)) {
            Rectangle r = obj.getRectangle();
            pts.add(new Vector2(r.x, r.y));
        }
        return pts.toArray(Vector2.class);
    }

    /** @return 地图世界像素宽 */
    public int getWorldPixelWidth() {
        return worldPixelWidth;
    }

    /** @return 地图世界像素高 */
    public int getWorldPixelHeight() {
        return worldPixelHeight;
    }

    /** @return 单瓦片像素宽 */
    public int getTilePixelWidth() {
        return tilePixelWidth;
    }

    /** @return 单瓦片像素高 */
    public int getTilePixelHeight() {
        return tilePixelHeight;
    }

    /** @return 地图瓦片列数 */
    public int getTilesWide() {
        return tilesWide;
    }

    /** @return 地图瓦片行数 */
    public int getTilesHigh() {
        return tilesHigh;
    }

    /**
     * 把吉普车/相机坐标 clamp 到地图范围内，防止看到地图外黑边。
     *
     * @param r 待限制的矩形（如相机可视范围或实体包围盒）
     */
    public void clampToWorld(Rectangle r) {
        if (r.x < 0f) r.x = 0f;
        if (r.y < 0f) r.y = 0f;
        if (r.x + r.width > worldPixelWidth) r.x = worldPixelWidth - r.width;
        if (r.y + r.height > worldPixelHeight) r.y = worldPixelHeight - r.height;
    }

    /** 释放地图资源（纹理等） */
    public void dispose() {
        map.dispose();
    }
}
