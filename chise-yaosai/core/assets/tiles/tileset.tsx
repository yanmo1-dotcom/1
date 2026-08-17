<?xml version="1.0" encoding="UTF-8"?>
<!--
  tileset.tsx —— 瓦片图集定义（Tiled Tileset XML）
  对应程序生成的 tileset.png：128x32 像素，含 4 个 32x32 瓦片。

  瓦片 ID（gid）从 firstgid=1 开始：
    gid=1 → 索引0 草地（Ground 层填充）
    gid=2 → 索引1 道路（Ground 层道路）
    gid=3 → 索引2 墙壁（Collision 层，可阻挡）
    gid=4 → 索引3 战俘营占位（POW 层，第 4 步交互用）

  说明：tsx 用相对路径引用 PNG，LibGDX 加载时基于 tmx 所在目录解析。
-->
<tileset version="1.10" tiledversion="1.10.2" name="tileset"
         tilewidth="32" tileheight="32" tilecount="4" columns="4">
    <image source="tileset.png" width="128" height="32"/>
    <!-- 显式声明每个瓦片的属性，便于代码按 name 查询 -->
    <tile id="0">
        <properties>
            <property name="type" value="grass"/>
        </properties>
    </tile>
    <tile id="1">
        <properties>
            <property name="type" value="road"/>
        </properties>
    </tile>
    <tile id="2">
        <properties>
            <property name="type" value="wall"/>
            <!-- solid=true 表示该瓦片参与碰撞阻挡 -->
            <property name="solid" type="bool" value="true"/>
        </properties>
    </tile>
    <tile id="3">
        <properties>
            <property name="type" value="pow_camp"/>
            <property name="solid" type="bool" value="true"/>
        </properties>
    </tile>
</tileset>
