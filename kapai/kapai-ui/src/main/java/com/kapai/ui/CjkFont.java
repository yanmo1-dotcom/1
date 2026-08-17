package com.kapai.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import lombok.extern.slf4j.Slf4j;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * 纯 Java 中文字体渲染器（不依赖任何 native 库）。
 *
 * 设计思路：LibGDX 的 freetype native 在 JDK 26 上会崩溃，故改用 AWT 的
 * {@link Font} 渲染每个字符到 BufferedImage，再上传为 LibGDX 的 {@link TextureRegion}。
 * 按需渲染并缓存：第一次遇到某字符时才生成其纹理，避免一次性渲染全部汉字。
 * 单字符单纹理——简单可靠，字符总量有限（卡牌/界面文案），性能足够。
 *
 * 清晰度优化：
 * - 纹理过滤设为 Linear，避免缩放时锯齿；
 * - 以超采样系数 ss 渲染字形（先按 size*ss 渲染再按需缩放），保证放大后仍清晰；
 * - 直接从 classpath 加载 simhei.ttf，保证字形稳定（不依赖系统字体回退）。
 */
@Slf4j
public class CjkFont {

    /** 超采样倍数：字形先按 4 倍分辨率渲染，再缩放回目标尺寸，放大后仍锐利。 */
    private static final int SS = 4;

    private final Font awtFont;
    private final FontMetrics metrics;
    private final int ascent;
    private final int lineHeight;
    private final Map<Character, Glyph> glyphs = new HashMap<>();
    private Color color = Color.WHITE;
    /** 描边开关：开启后每次绘制先画一圈黑色描边再画本体。 */
    private boolean borderEnabled = false;
    private float borderStrength = 1.5f;
    /** 阴影偏移（像素），>0 时绘制半透明黑色阴影。 */
    private float shadowOffsetX = 0f;
    private float shadowOffsetY = 0f;
    private float shadowAlpha = 0.6f;

    public CjkFont(int size) {
        this.awtFont = loadFont(size * SS);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = probe.createGraphics();
        g.setFont(awtFont);
        this.metrics = g.getFontMetrics();
        this.ascent = metrics.getAscent();
        this.lineHeight = metrics.getHeight();
        g.dispose();
    }

    /** 直接从 classpath 加载 simhei.ttf，避免系统字体回退导致字形丑。 */
    private Font loadFont(int size) {
        try {
            Font base = Font.createFont(Font.TRUETYPE_FONT, Gdx.files.classpath("simhei.ttf").read());
            return base.deriveFont(Font.PLAIN, size);
        } catch (Exception e) {
            log.warn("加载 simhei.ttf 失败，退回 SansSerif 逻辑字体：{}", e.getMessage());
            return new Font(Font.SANS_SERIF, Font.PLAIN, size);
        }
    }

    /** 渲染一段文本所需的字符（按需生成字形）。 */
    public void ensure(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!glyphs.containsKey(c)) {
                glyphs.put(c, renderGlyph(c));
            }
        }
    }

    private Glyph renderGlyph(char c) {
        // 超采样渲染：在高分辨率绘制后缩放回目标尺寸
        int rawW = Math.max(1, metrics.charWidth(c));
        int rawH = metrics.getHeight();
        BufferedImage raw = new BufferedImage(rawW, rawH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = raw.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setFont(awtFont);
        g.setColor(java.awt.Color.WHITE);
        g.drawString(String.valueOf(c), 0, ascent);
        g.dispose();

        // 缩放回目标尺寸（消除超采样），得到清晰的目标分辨率字形
        int w = Math.max(1, rawW / SS);
        int h = Math.max(1, rawH / SS);
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gs = scaled.createGraphics();
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gs.drawImage(raw, 0, 0, w, h, null);
        gs.dispose();

        Texture texture = new Texture(toPixmap(scaled, w, h));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        TextureRegion region = new TextureRegion(texture);
        return new Glyph(region, w, h, (int) Math.ceil((double) metrics.charWidth(c) / SS));
    }

    private Pixmap toPixmap(BufferedImage img, int w, int h) {
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);
        int idx = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = pixels[idx++];
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >>> 16) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                int b = argb & 0xFF;
                // 预乘 alpha，避免黑边
                r = r * a / 255;
                g = g * a / 255;
                b = b * a / 255;
                pixmap.setColor(r / 255f, g / 255f, b / 255f, a / 255f);
                pixmap.drawPixel(x, y);
            }
        }
        return pixmap;
    }

    public void setColor(Color color) {
        this.color = color;
    }

    /** 开启黑色描边，提高复杂背景下可读性。strength 控制描边粗细（像素）。 */
    public void setBorder(boolean enabled, float strength) {
        this.borderEnabled = enabled;
        this.borderStrength = Math.max(0.5f, strength);
    }

    /** 开启阴影：偏移 (ox, oy) 像素，alpha 为不透明度（0~1）。 */
    public void setShadow(float ox, float oy, float alpha) {
        this.shadowOffsetX = ox;
        this.shadowOffsetY = oy;
        this.shadowAlpha = Math.max(0f, Math.min(1f, alpha));
    }

    /**
     * 在 batch 上绘制文本。y 语义与 {@link com.badlogic.gdx.graphics.g2d.BitmapFont#draw}
     * 一致——表示基线位置，内部按 ascent 上移字形。返回绘制宽度。
     * 绘制期间临时设置 batch color 为当前 color，结束后恢复。
     * 若开启描边/阴影，会先画描边/阴影再画本体。
     */
    public float drawText(com.badlogic.gdx.graphics.g2d.Batch batch, CharSequence text, float x, float y) {
        ensure(text);
        Color old = new Color(batch.getColor());
        float cx = x;
        float ascentScaled = (float) ascent / SS;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Glyph g = glyphs.get(c);
            if (g != null) {
                // 1. 阴影
                if (shadowOffsetX != 0f || shadowOffsetY != 0f) {
                    batch.setColor(new Color(0, 0, 0, shadowAlpha));
                    batch.draw(g.region, cx + shadowOffsetX, y - ascentScaled + shadowOffsetY, g.width, g.height);
                }
                // 2. 描边：8 方向偏移画黑色字形
                if (borderEnabled) {
                    batch.setColor(new Color(0, 0, 0, 0.9f));
                    float bs = borderStrength;
                    for (int d = 0; d < 8; d++) {
                        float ox = (float) Math.cos(d * Math.PI / 4) * bs;
                        float oy = (float) Math.sin(d * Math.PI / 4) * bs;
                        batch.draw(g.region, cx + ox, y - ascentScaled + oy, g.width, g.height);
                    }
                }
                // 3. 本体
                batch.setColor(color);
                batch.draw(g.region, cx, y - ascentScaled, g.width, g.height);
                cx += g.advance;
            }
        }
        batch.setColor(old);
        return cx - x;
    }

    /**
     * 旋转版文本绘制。以 为基线起点，整段文本绕该起点旋转 rotation 度（顺时针）。
     * 用于扇形手牌中倾斜的卡牌文字。返回文本总宽度。
     */
    public float drawText(com.badlogic.gdx.graphics.g2d.Batch batch, CharSequence text,
                          float x, float y, float rotation) {
        if (rotation == 0f) {
            return drawText(batch, text, x, y);
        }
        ensure(text);
        Color old = new Color(batch.getColor());
        batch.setColor(color);
        float cx = x;
        float ascentScaled = (float) ascent / SS;
        float rad = (float) Math.toRadians(rotation);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Glyph g = glyphs.get(c);
            if (g != null) {
                // 字形局部坐标：相对基线起点的偏移 (dx, dy)，dy 为字形顶部相对基线 = -ascent
                float dx = cx - x;
                float dy = -ascentScaled;
                // 旋转到世界坐标
                float rx = x + (dx * cos - dy * sin);
                float ry = y + (dx * sin + dy * cos);
                // 阴影
                if (shadowOffsetX != 0f || shadowOffsetY != 0f) {
                    batch.setColor(new Color(0, 0, 0, shadowAlpha));
                    batch.draw(g.region, rx + shadowOffsetX, ry + shadowOffsetY,
                            0, 0, g.width, g.height, 1, 1, rotation);
                }
                // 描边：8 方向偏移画黑色字形（世界坐标偏移，旋转后近似）
                if (borderEnabled) {
                    batch.setColor(new Color(0, 0, 0, 0.9f));
                    float bs = borderStrength;
                    for (int d = 0; d < 8; d++) {
                        float ox = (float) Math.cos(d * Math.PI / 4) * bs;
                        float oy = (float) Math.sin(d * Math.PI / 4) * bs;
                        batch.draw(g.region, rx + ox, ry + oy,
                                0, 0, g.width, g.height, 1, 1, rotation);
                    }
                }
                // 本体
                batch.setColor(color);
                batch.draw(g.region, rx, ry, 0, 0, g.width, g.height, 1, 1, rotation);
                cx += g.advance;
            }
        }
        batch.setColor(old);
        return cx - x;
    }

    /** 行高（已缩放回目标分辨率）。 */
    public int lineHeight() {
        return lineHeight / SS;
    }

    public void dispose() {
        for (Glyph g : glyphs.values()) {
            g.region.getTexture().dispose();
        }
        glyphs.clear();
    }

    private static class Glyph {
        final TextureRegion region;
        final int width;
        final int height;
        final int advance;

        Glyph(TextureRegion region, int width, int height, int advance) {
            this.region = region;
            this.width = width;
            this.height = height;
            this.advance = Math.max(advance, width);
        }
    }
}
