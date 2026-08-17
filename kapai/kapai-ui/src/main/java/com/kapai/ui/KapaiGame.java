package com.kapai.ui;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.kapai.core.battle.BattleManager;
import com.kapai.core.battle.BattlePhase;
import com.kapai.core.card.AbstractCard;
import com.kapai.core.creature.Enemy;
import com.kapai.core.creature.Player;
import com.kapai.core.enums.CardTarget;
import com.kapai.core.enums.CardType;
import com.kapai.core.relic.BurningBloodRelic;
import com.kapai.data.CardDatabase;
import com.kapai.data.CardLoadException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * LibGDX 表现层入口 + 最简战斗场景。
 *
 * 设计思路：
 * - 仅负责渲染与输入转发，战斗逻辑全部委托 core 的 {@link BattleManager}。
 * - 手牌横向排列在屏幕底部，点击即可打出（自动选目标：单体牌打血量最低敌人，
 *   AOE 打全体，自身牌打自己）。
 * - 右下角"结束回合"按钮。
 * - 敌人区在屏幕上方，显示 HP/意图。
 * - 不引入 Skin/Atlas 等资源，纯 ShapeRenderer + BitmapFont 即时绘制，零外部资源依赖。
 */
@Slf4j
public class KapaiGame extends Game implements InputProcessor {

    /** 逻辑世界尺寸。窗口可任意缩放，渲染与输入统一映射到该坐标系，保证放大后仍可玩。 */
    private static final float WORLD_W = 1024f;
    private static final float WORLD_H = 640f;

    /** 卡牌尺寸常量。 */
    private static final float CARD_W = 120f;
    private static final float CARD_H = 170f;
    /** 扇形手牌参数。 */
    private static final float FAN_RADIUS = 320f;
    private static final float FAN_SPREAD_DEG = 26f;
    /** 手牌中心 X 与底部基线 Y。 */
    private static final float HAND_CENTER_X = WORLD_W / 2f;
    private static final float HAND_BASE_Y = 20f;

    private SpriteBatch batch;
    private ShapeRenderer shapes;
    private CjkFont font;
    private CjkFont smallFont;
    private CjkFont titleFont;
    private OrthographicCamera camera;
    private FitViewport viewport;
    /** 卡牌底纹（白色，绘制时按类型着色），用于旋转绘制。 */
    private TextureRegion cardTexture;

    private BattleManager battle;
    private Player player;
    private List<Enemy> enemies;

    /** 当前拖动中的卡牌；null 表示无拖拽。 */
    private AbstractCard draggedCard;
    /** 拖动光标的逻辑世界坐标。 */
    private float dragX, dragY;
    /** 按下时相对卡牌中心的偏移，让拖动卡牌"粘"住点击点。 */
    private float dragOffsetX, dragOffsetY;
    /** 拖起时卡牌在手牌中的原位中心，用于未命中时回手动画终点。 */
    private float dragOriginX, dragOriginY;
    /** 鼠标悬停的手牌索引，-1 表示无；用于卡牌浮起效果。 */
    private int hoverIndex = -1;
    /** 鼠标逻辑世界坐标（mouseMoved 持续更新）。 */
    private float mouseX, mouseY;
    /** 手牌布局缓存：每张卡 [cx, cy, rotation]，长度 = 手牌数*3。 */
    private float[] handLayout = new float[0];

    /** 回手动画状态：未命中目标后卡牌插值飞回原位。animCard 为 null 表示无动画。 */
    private AbstractCard animCard;
    private float animX, animY;        // 当前动画位置
    private float animTargetX, animTargetY;  // 目标位置（手牌原位）
    private float animRot;             // 飞回时旋转（简化为0）

    /** 结束回合按钮矩形。 */
    private final Rectangle endTurnBtn = new Rectangle(800, 20, 184, 56);

    // 玩家面板矩形（中央），供护盾框外标注与点击判定复用
    private final Rectangle playerRect = new Rectangle(362, 190, 300, 150);

    // 敌人矩形缓存，用于点击命中判定
    private final List<Rectangle> enemyRects = new ArrayList<>();

    private String message = "";

    @Override
    public void create() {
        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        camera = new OrthographicCamera();
        viewport = new FitViewport(WORLD_W, WORLD_H, camera);
        viewport.apply(true);
        font = new CjkFont(22);
        smallFont = new CjkFont(16);
        titleFont = new CjkFont(28);
        // 描边提升复杂背景下可读性，阴影增加层次
        font.setBorder(true, 1.2f);
        smallFont.setBorder(true, 1.0f);
        titleFont.setShadow(2f, -2f, 0.5f);
        cardTexture = new TextureRegion(createCardTexture());

        CardDatabase db = new CardDatabase();
        try {
            db.load();
        } catch (CardLoadException e) {
            log.error("卡牌数据加载失败", e);
            message = "卡牌加载失败：" + e.getMessage();
            return;
        }

        // 构建玩家、敌人、牌组
        player = new Player("P", "战士", 50);
        Enemy e1 = new Enemy("E1", "尖刺史莱姆", 24);
        Enemy e2 = new Enemy("E2", "酸液史莱姆", 20);
        enemies = new ArrayList<>(List.of(e1, e2));

        List<AbstractCard> deck = new ArrayList<>();
        for (int i = 0; i < 5; i++) deck.add(db.createCopy("STRIKE").orElseThrow());
        for (int i = 0; i < 4; i++) deck.add(db.createCopy("DEFEND").orElseThrow());
        for (int i = 0; i < 2; i++) deck.add(db.createCopy("BASH").orElseThrow());
        for (int i = 0; i < 2; i++) deck.add(db.createCopy("IRON_WAVE").orElseThrow());
        for (int i = 0; i < 2; i++) deck.add(db.createCopy("SHRUG_IT_OFF").orElseThrow());

        battle = new BattleManager(player, enemies, deck);
        battle.addRelicListener(new BurningBloodRelic());
        battle.startBattle();

        Gdx.input.setInputProcessor(this);
        log.info("Kapai 图形窗口已启动");
    }

    @Override
    public void render() {
        ScreenUtils.clear(new Color(0.12f, 0.13f, 0.18f, 1f));
        Gdx.gl.glEnable(GL20.GL_BLEND);

        // 所有绘制走相机投影，使逻辑坐标在任意窗口尺寸下一致
        camera.update();
        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);

        if (battle == null) {
            drawMessage();
            return;
        }

        updateCardAnim();   // 推进回手动画
        drawEnemyArea();
        drawPlayerArea();
        drawHand();
        drawEndTurnButton();
        drawMessage();
        drawTitle();

        // 拖拽相关层最后绘制，保证拖拽卡牌在最上层
        drawDropTargetHighlight();
        drawAnimCard();
        drawDraggedCard();
    }

    /** 推进回手 Lerp 动画：每帧向目标位置靠近 18%，足够近则结束。 */
    private void updateCardAnim() {
        if (animCard == null) return;
        float lerp = 0.18f;
        animX += (animTargetX - animX) * lerp;
        animY += (animTargetY - animY) * lerp;
        if (Math.abs(animTargetX - animX) < 0.8f && Math.abs(animTargetY - animY) < 0.8f) {
            // 到位，结束动画
            animCard = null;
        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    // ===== 渲染 =====

    private void drawEnemyArea() {
        enemyRects.clear();
        float y = 430;
        float w = 220, h = 130;
        float gap = 40;
        float totalW = enemies.size() * w + (enemies.size() - 1) * gap;
        float startX = (WORLD_W - totalW) / 2f;

        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            float x = startX + i * (w + gap);
            Rectangle rect = new Rectangle(x, y, w, h);
            enemyRects.add(rect);

            Color fill = e.isDead() ? new Color(0.2f, 0.2f, 0.2f, 0.6f)
                    : (draggedCard != null && draggedCard.getType() == CardType.ATTACK ? new Color(0.5f, 0.2f, 0.2f, 0.85f) : new Color(0.3f, 0.25f, 0.2f, 0.85f));
            drawRect(rect, fill, Color.LIGHT_GRAY);

            batch.begin();
            font.setColor(e.isDead() ? Color.GRAY : Color.WHITE);
            font.drawText(batch, e.getName(), x + 12, y + h - 24);
            font.drawText(batch, "生命 " + e.getCurrentHp() + "/" + e.getMaxHp(), x + 12, y + h - 48);
            if (!e.isDead() && e.getIntent() != null) {
                font.setColor(Color.YELLOW);
                font.drawText(batch, "意图：" + I18n.intentKind(e.getIntent().getKind()) + "(" + e.getIntent().getDamage() + ")", x + 12, y + h - 72);
            }
            if (e.isDead()) {
                font.setColor(Color.RED);
                font.drawText(batch, "[已死亡]", x + 12, y + 24);
            }
            batch.end();
        }
    }

    private void drawPlayerArea() {
        Color fill = (draggedCard != null && draggedCard.getType() != CardType.ATTACK)
                ? new Color(0.2f, 0.4f, 0.6f, 0.9f) : new Color(0.2f, 0.3f, 0.45f, 0.85f);
        drawRect(playerRect, fill, Color.LIGHT_GRAY);

        float x = playerRect.x + 16;
        float top = playerRect.y + playerRect.height;
        batch.begin();
        font.setColor(Color.WHITE);
        font.drawText(batch, player.getName(), x, top - 26);
        font.drawText(batch, "生命 " + player.getCurrentHp() + "/" + player.getMaxHp(), x, top - 54);
        font.drawText(batch, "阶段 " + I18n.phase(battle.getPhase()), x, top - 82);
        batch.end();

        // 护盾：框外左上角（在玩家框左上外侧）
        drawShieldBadge();
    }

    /** 护盾徽标：贴在玩家框左上角外侧，框外不遮挡框内信息。 */
    private void drawShieldBadge() {
        int block = player.statusAmount(com.kapai.core.status.StatusId.BLOCK);
        float bw = 64, bh = 40;
        // 框外左上：x 在框左边再左移一点，y 在框顶部对齐
        float bx = playerRect.x - bw + 12;
        float by = playerRect.y + playerRect.height - bh + 10;
        Rectangle shield = new Rectangle(bx, by, bw, bh);
        drawRect(shield, new Color(0.15f, 0.4f, 0.5f, 0.95f), Color.CYAN);
        batch.begin();
        font.setColor(Color.CYAN);
        font.drawText(batch, "护盾 " + block, bx + 6, by + bh - 10);
        batch.end();
    }

    private void drawHand() {
        List<AbstractCard> hand = battle.getPiles().getHand();
        handLayout = fanLayout(hand.size(), HAND_CENTER_X, HAND_BASE_Y, FAN_RADIUS, FAN_SPREAD_DEG);
        // 更新悬停索引：遍历手牌检测鼠标是否在卡牌（旋转矩形）内
        updateHover(hand);

        boolean playerTurn = battle.getPhase() == BattlePhase.PLAYER_TURN;
        for (int i = 0; i < hand.size(); i++) {
            AbstractCard c = hand.get(i);
            // 拖动中或回手动画中的卡牌不在原位绘制（避免重影）
            if (c == draggedCard || c == animCard) continue;

            float cx = handLayout[i * 3];
            float cy = handLayout[i * 3 + 1];
            float rot = handLayout[i * 3 + 2];
            // 悬停浮起：非拖拽状态下，鼠标悬停的卡牌 y 上移
            if (draggedCard == null && i == hoverIndex) {
                cy += 26f;
            }
            boolean playable = c.canPlay(player) && playerTurn;
            Color fill = playable ? colorByType(c.getType()) : new Color(0.25f, 0.25f, 0.3f, 0.8f);

            // 旋转绘制卡牌底纹：以卡牌中心为旋转原点
            drawCardAt(cx, cy, rot, fill, playable ? Color.LIGHT_GRAY : Color.GRAY, c, playable);
        }
    }

    /** 更新 hoverIndex：遍历手牌用旋转矩形命中检测鼠标坐标。 */
    private void updateHover(List<AbstractCard> hand) {
        hoverIndex = -1;
        if (draggedCard != null) return; // 拖拽中不更新悬停
        for (int i = 0; i < hand.size(); i++) {
            if (i * 3 + 1 >= handLayout.length) break;
            float cx = handLayout[i * 3];
            float cy = handLayout[i * 3 + 1];
            float rot = handLayout[i * 3 + 2];
            if (containsRotated(mouseX, mouseY, cx, cy, CARD_W, CARD_H, rot)) {
                hoverIndex = i;
                return;
            }
        }
    }

    /** 渲染回手 Lerp 动画中的卡牌。 */
    private void drawAnimCard() {
        if (animCard == null) return;
        boolean playable = animCard.canPlay(player);
        Color fill = playable ? colorByType(animCard.getType()) : new Color(0.25f, 0.25f, 0.3f, 0.8f);
        drawCardAt(animX, animY, animRot, fill, Color.LIGHT_GRAY, animCard, playable);
    }

    /** 扇形排列：返回每张卡牌的中心 cx,cy,rotation（长度 count*3）。 */
    private float[] fanLayout(int count, float centerX, float baseY, float radius, float maxSpreadDeg) {
        if (count == 0) return new float[0];
        float[] out = new float[count * 3];
        float mid = (count - 1) / 2f;
        float stepDeg = count <= 1 ? 0 : maxSpreadDeg / (count - 1);
        // 圆心在中间牌下方 radius 处；中间牌中心 y = baseY + CARD_H/2
        float arcCenterY = baseY + CARD_H / 2f + radius;
        for (int i = 0; i < count; i++) {
            float angleDeg = (i - mid) * stepDeg;
            float rad = (float) Math.toRadians(angleDeg);
            float cx = centerX + (float) Math.sin(rad) * radius;
            float cy = arcCenterY - (float) Math.cos(rad) * radius;
            out[i * 3] = cx;
            out[i * 3 + 1] = cy;
            out[i * 3 + 2] = -angleDeg;
        }
        return out;
    }

    /** 在 绘制一张旋转卡牌， 为卡牌中心。 */
    private void drawCardAt(float cx, float cy, float rotation, Color fill, Color border,
                            AbstractCard c, boolean playable) {
        batch.begin();
        batch.setColor(fill);
        // 以卡牌中心为旋转原点绘制底纹
        batch.draw(cardTexture, cx - CARD_W / 2f, cy - CARD_H / 2f,
                CARD_W / 2f, CARD_H / 2f, CARD_W, CARD_H, 1, 1, rotation);
        batch.setColor(Color.WHITE);
        // 文字相对卡牌左上角偏移，转到卡牌中心旋转坐标系
        float textX = cx - CARD_W / 2f + 8;
        float textTop = cy + CARD_H / 2f;
        font.setColor(playable ? Color.WHITE : Color.GRAY);
        font.drawText(batch, c.getName(), textX, textTop - 18, rotation);
        font.setColor(Color.CYAN);
        font.drawText(batch, "费用 " + c.getCost(), textX, textTop - 40, rotation);
        font.setColor(Color.LIGHT_GRAY);
        font.drawText(batch, I18n.type(c.getType()), textX, textTop - 60, rotation);
        int lineY = 84;
        for (com.kapai.core.effect.CardEffect eff : c.getEffects()) {
            font.drawText(batch, I18n.effectType(eff.typeId()) + paramsOf(eff), textX, textTop - lineY, rotation);
            lineY += 18;
        }
        batch.end();

        // 边框用 ShapeRenderer 不支持旋转，这里用 batch 画细线框近似：再画一个描边纹理
        // 简化：用半透明白色描边通过再绘制一次略大的底纹
        batch.begin();
        batch.setColor(new Color(border.r, border.g, border.b, 0.9f));
        // 描边：上下左右各画一条细条（旋转）——简化为画一个略大的边框纹理
        batch.draw(cardTexture, cx - CARD_W / 2f - 2, cy - CARD_H / 2f - 2,
                CARD_W / 2f + 2, CARD_H / 2f + 2, CARD_W + 4, CARD_H + 4, 1, 1, rotation);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /** 拖动中的卡牌跟随光标，缩放 1.3 倍并绘制金色高亮边框与投影。 */
    private void drawDraggedCard() {
        if (draggedCard == null) return;
        boolean playable = draggedCard.canPlay(player) && battle.getPhase() == BattlePhase.PLAYER_TURN;
        Color fill = playable ? colorByType(draggedCard.getType()) : new Color(0.25f, 0.25f, 0.3f, 0.85f);
        float scale = 1.3f;
        float w = CARD_W * scale;
        float h = CARD_H * scale;

        // 1. 投影：在卡牌下方偏移画一个半透明黑色卡牌
        batch.begin();
        batch.setColor(new Color(0, 0, 0, 0.45f));
        batch.draw(cardTexture, dragX - w / 2f + 8, dragY - h / 2f - 8,
                w / 2f, h / 2f, w, h, 1, 1, 0);
        batch.end();

        // 2. 卡牌本体
        batch.begin();
        batch.setColor(fill);
        batch.draw(cardTexture, dragX - w / 2f, dragY - h / 2f,
                w / 2f, h / 2f, w, h, 1, 1, 0);
        batch.setColor(Color.WHITE);
        float textX = dragX - w / 2f + 10;
        float textTop = dragY + h / 2f;
        font.setColor(playable ? Color.WHITE : Color.GRAY);
        font.drawText(batch, draggedCard.getName(), textX, textTop - 22, 0);
        font.setColor(Color.CYAN);
        font.drawText(batch, "费用 " + draggedCard.getCost(), textX, textTop - 48, 0);
        font.setColor(Color.LIGHT_GRAY);
        font.drawText(batch, I18n.type(draggedCard.getType()), textX, textTop - 72, 0);
        int lineY = 96;
        for (com.kapai.core.effect.CardEffect eff : draggedCard.getEffects()) {
            font.drawText(batch, I18n.effectType(eff.typeId()) + paramsOf(eff), textX, textTop - lineY, 0);
            lineY += 20;
        }
        batch.end();

        // 3. 金色高亮边框：用 ShapeRenderer 画双层金边
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.GOLD);
        shapes.rect(dragX - w / 2f - 3, dragY - h / 2f - 3, w + 6, h + 6);
        shapes.setColor(new Color(1f, 0.85f, 0.2f, 0.6f));
        shapes.rect(dragX - w / 2f - 6, dragY - h / 2f - 6, w + 12, h + 12);
        shapes.end();
    }

    /** 拖拽悬停在合法目标上时高亮目标（委托 TargetingHelper）。 */
    private void drawDropTargetHighlight() {
        if (draggedCard == null) return;
        TargetingHelper.drawHover(shapes, draggedCard, dragX, dragY, enemies, enemyRects, playerRect);
    }

    /** 返回当前拖拽悬停的合法目标矩形，无则 null。 */
    private Rectangle dropTargetRect() {
        if (draggedCard == null) return null;
        CardType t = draggedCard.getType();
        boolean attack = t == CardType.ATTACK;
        // 攻击牌 → 敌人
        if (attack) {
            for (int i = 0; i < enemyRects.size(); i++) {
                if (enemyRects.get(i).contains(dragX, dragY) && !enemies.get(i).isDead()) {
                    return enemyRects.get(i);
                }
            }
        } else {
            // 非攻击（技能/能力）→ 玩家
            if (playerRect.contains(dragX, dragY)) return playerRect;
        }
        return null;
    }

    /** 生成 1x1 白色像素卡牌底纹纹理，绘制时用 batch.setColor 着色。 */
    private Texture createCardTexture() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        Texture tex = new Texture(pm);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return tex;
    }

    private String paramsOf(com.kapai.core.effect.CardEffect eff) {
        if (eff instanceof com.kapai.core.effect.DamageEffect d) return " " + d.getAmount();
        if (eff instanceof com.kapai.core.effect.BlockEffect b) return " " + b.getAmount();
        if (eff instanceof com.kapai.core.effect.DrawCardEffect dr) return " " + dr.getAmount();
        if (eff instanceof com.kapai.core.effect.ApplyStatusEffect a)
            return " " + I18n.status(a.getStatusId()) + a.getAmount();
        return "";
    }

    private Color colorByType(CardType type) {
        return switch (type) {
            case ATTACK -> new Color(0.55f, 0.2f, 0.2f, 0.9f);
            case SKILL -> new Color(0.2f, 0.35f, 0.55f, 0.9f);
            case POWER -> new Color(0.45f, 0.25f, 0.55f, 0.9f);
            default -> new Color(0.3f, 0.3f, 0.3f, 0.9f);
        };
    }

    private void drawEndTurnButton() {
        boolean playerTurn = battle.getPhase() == BattlePhase.PLAYER_TURN;
        Color fill = playerTurn ? new Color(0.2f, 0.55f, 0.3f, 0.95f) : new Color(0.3f, 0.3f, 0.3f, 0.7f);
        drawRect(endTurnBtn, fill, Color.WHITE);
        batch.begin();
        font.setColor(playerTurn ? Color.WHITE : Color.GRAY);
        font.drawText(batch, "结束回合", endTurnBtn.x + 36, endTurnBtn.y + 34);
        // 回合数显示在结束按钮左侧
        font.setColor(Color.LIGHT_GRAY);
        font.drawText(batch, "回合 " + battle.getTurnCount(), endTurnBtn.x - 130, endTurnBtn.y + 34);
        batch.end();

        // 能量在左下角独立显示
        drawEnergyBadge();
    }

    /** 能量徽标：左下角。 */
    private void drawEnergyBadge() {
        float bw = 120, bh = 56;
        Rectangle energy = new Rectangle(40, 20, bw, bh);
        drawRect(energy, new Color(0.5f, 0.4f, 0.1f, 0.95f), Color.ORANGE);
        batch.begin();
        font.setColor(Color.ORANGE);
        font.drawText(batch, "能量 " + player.getEnergy(), 52, 20 + bh - 16);
        batch.end();
    }

    private void drawMessage() {
        if (message == null || message.isEmpty()) return;
        batch.begin();
        font.setColor(Color.YELLOW);
        font.drawText(batch, message, 40, 150);
        batch.end();
    }

    private void drawTitle() {
        batch.begin();
        titleFont.setColor(new Color(0.9f, 0.85f, 0.4f, 0.9f));
        titleFont.drawText(batch, "卡牌肉鸽 · 战斗演示", 380, 620);
        batch.end();
    }

    private void drawRect(Rectangle r, Color fill, Color border) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(fill);
        shapes.rect(r.x, r.y, r.width, r.height);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(border);
        shapes.rect(r.x, r.y, r.width, r.height);
        shapes.end();
    }

    // ===== 输入 =====

    private final Vector2 tmpCoords = new Vector2();

    private void toWorld(int screenX, int screenY) {
        tmpCoords.set(screenX, screenY);
        viewport.unproject(tmpCoords);
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (battle == null || battle.getPhase() != BattlePhase.PLAYER_TURN) return false;
        toWorld(screenX, screenY);
        float mx = tmpCoords.x;
        float my = tmpCoords.y;

        // 结束回合按钮
        if (endTurnBtn.contains(mx, my)) {
            setMessage("玩家结束回合");
            battle.endPlayerTurn();
            return true;
        }

        // 手牌命中（旋转矩形判定）：开始拖拽
        List<AbstractCard> hand = battle.getPiles().getHand();
        for (int i = 0; i < hand.size(); i++) {
            AbstractCard c = hand.get(i);
            if (i * 3 + 1 >= handLayout.length) break;
            float cx = handLayout[i * 3];
            float cy = handLayout[i * 3 + 1];
            float rot = handLayout[i * 3 + 2];
            if (containsRotated(mx, my, cx, cy, CARD_W, CARD_H, rot)) {
                if (!c.canPlay(player)) {
                    setMessage("能量不足，无法打出 " + c.getName());
                    return true;
                }
                draggedCard = c;
                dragX = mx;
                dragY = my;
                dragOffsetX = mx - cx;
                dragOffsetY = my - cy;
                dragOriginX = cx;   // 记录原位用于回手动画
                dragOriginY = cy;
                setMessage("拖动 " + c.getName() + " 到目标");
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (draggedCard == null) return false;
        toWorld(screenX, screenY);
        dragX = tmpCoords.x;
        dragY = tmpCoords.y;
        return true;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (draggedCard == null) return false;
        toWorld(screenX, screenY);
        float mx = tmpCoords.x;
        float my = tmpCoords.y;

        AbstractCard c = draggedCard;
        CardType t = c.getType();
        boolean isAttack = t == CardType.ATTACK;
        boolean hit = false;

        if (isAttack) {
            // 攻击牌 → 敌人
            for (int i = 0; i < enemyRects.size(); i++) {
                if (enemyRects.get(i).contains(mx, my) && !enemies.get(i).isDead()) {
                    playCardAndDiscard(c, enemies.get(i));
                    hit = true;
                    break;
                }
            }
            if (!hit) {
                if (playerRect.contains(mx, my)) {
                    setMessage("攻击牌只能拖向敌人");
                } else {
                    setMessage("未命中敌人，卡牌回手");
                }
            }
        } else {
            // 非攻击（技能/能力）→ 玩家
            if (playerRect.contains(mx, my)) {
                playCardAndDiscard(c, player);
                hit = true;
            } else {
                boolean onEnemy = enemyRects.stream().anyMatch(r -> r.contains(mx, my));
                if (onEnemy) {
                    setMessage(c.getType() == CardType.SKILL ? "防御牌只能拖向自己" : "该牌只能拖向自己");
                } else {
                    setMessage("未命中自己，卡牌回手");
                }
            }
        }

        // 未命中且未打出 → 启动回手 Lerp 动画，不直接清坐标
        if (!hit) {
            animCard = c;
            animX = mx;
            animY = my;
            animTargetX = dragOriginX;
            animTargetY = dragOriginY;
            animRot = 0;
        }
        draggedCard = null;
        return true;
    }

    @Override
    public boolean touchCancelled(int i, int i1, int i2, int i3) {
        draggedCard = null;
        return false;
    }

    /**
     * 旋转矩形点包含判定：把世界点 反向旋转到卡牌局部坐标系，再判轴对齐矩形。
     * 为卡牌中心，rotation 为度。
     */
    private boolean containsRotated(float px, float py, float cx, float cy,
                                   float w, float h, float rotation) {
        float rad = (float) Math.toRadians(-rotation);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float dx = px - cx;
        float dy = py - cy;
        float lx = dx * cos - dy * sin;
        float ly = dx * sin + dy * cos;
        return Math.abs(lx) <= w / 2f && Math.abs(ly) <= h / 2f;
    }

    private void playCardAndDiscard(AbstractCard c, com.kapai.core.creature.AbstractCreature target) {
        setMessage("打出 " + c.getName());
        battle.playCard(c, target);
        if (battle.getPhase() == BattlePhase.BATTLE_END) {
            boolean win = enemies.stream().allMatch(Enemy::isDead);
            setMessage(win ? "胜利！" : "失败！");
        }
    }

    private void setMessage(String m) {
        this.message = m;
    }

    // ===== 生命周期 =====

    @Override
    public void dispose() {
        if (batch != null) batch.dispose();
        if (shapes != null) shapes.dispose();
        if (font != null) font.dispose();
        if (smallFont != null) smallFont.dispose();
        if (titleFont != null) titleFont.dispose();
        if (cardTexture != null) cardTexture.getTexture().dispose();
    }
    // 其余 InputProcessor 方法保持默认
    @Override public boolean keyDown(int i) { return false; }
    @Override public boolean keyUp(int i) { return false; }
    @Override public boolean keyTyped(char c) { return false; }
    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        toWorld(screenX, screenY);
        mouseX = tmpCoords.x;
        mouseY = tmpCoords.y;
        return false;
    }
    @Override public boolean scrolled(float v, float v1) { return false; }
}
