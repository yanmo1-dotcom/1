package com.yourname.voxelgame.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * 中文字体渲染器：用 AWT 把文字画到 BufferedImage，转成 GL 纹理，再贴 quad 渲染。
 * 缓存：同一字符串只生成一次纹理。
 * 需在 GL 上下文创建后才能调用。
 */
public class FontRenderer {

    private final Font font;
    private final Map<String, Integer> cache = new HashMap<>();

    public FontRenderer(int size) {
        // 尝试微软雅黑，回退到无衬线
        Font f = new Font("Microsoft YaHei", Font.PLAIN, size);
        if (!f.getFamily().equals("Microsoft YaHei") && !f.getFamily().equals("微软雅黑")) {
            f = new Font(Font.SANS_SERIF, Font.PLAIN, size);
        }
        this.font = f;
    }

    /** 在屏幕坐标 (x, y)（左上原点，y 向下）绘制文字。须在正交投影下调用。 */
    public void drawText(String text, int x, int y, float r, float g, float b) {
        if (text == null || text.isEmpty()) return;
        int tex = cache.computeIfAbsent(text, this::makeTexture);
        int[] w = sizes(text);
        drawTexturedQuad(tex, x, y, w[0], w[1], r, g, b);
    }

    /** 右对齐：文字右边缘在 x。 */
    public void drawTextRight(String text, int xRight, int y, float r, float g, float b) {
        int[] w = sizes(text);
        drawText(text, xRight - w[0], y, r, g, b);
    }

    /** 居中：文字中心在 (cx, cy)。 */
    public void drawTextCenter(String text, int cx, int cy, float r, float g, float b) {
        int[] w = sizes(text);
        drawText(text, cx - w[0] / 2, cy - w[1] / 2, r, g, b);
    }

    /** 返回 [width, height]。 */
    public int[] sizes(String text) {
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg = tmp.createGraphics();
        gg.setFont(font);
        FontMetrics fm = gg.getFontMetrics();
        int w = fm.stringWidth(text);
        int h = fm.getHeight();
        gg.dispose();
        return new int[] { Math.max(1, w), Math.max(1, h) };
    }

    private int makeTexture(String text) {
        // 画文字到图片
        int[] sz = sizes(text);
        int w = sz[0], h = sz[1];
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gg = img.createGraphics();
        gg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        gg.setFont(font);
        gg.setColor(Color.WHITE);
        gg.drawString(text, 0, gg.getFontMetrics().getAscent());
        gg.dispose();

        // 转 RGBA byte buffer
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);
        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int p = pixels[py * w + px];
                buf.put((byte) ((p >> 16) & 0xFF)); // R
                buf.put((byte) ((p >> 8) & 0xFF));  // G
                buf.put((byte) (p & 0xFF));         // B
                buf.put((byte) ((p >> 24) & 0xFF)); // A
            }
        }
        buf.flip();

        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, buf);
        glBindTexture(GL_TEXTURE_2D, 0);
        return tex;
    }

    private void drawTexturedQuad(int tex, int x, int y, int w, int h, float r, float g, float b) {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, tex);
        // 文字纹理本身是白色，用 glColor 染色
        glColor3f(r, g, b);
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0); glVertex2f(x, y + h);
        glTexCoord2f(1, 0); glVertex2f(x + w, y + h);
        glTexCoord2f(1, 1); glVertex2f(x + w, y);
        glTexCoord2f(0, 1); glVertex2f(x, y);
        glEnd();
        glBindTexture(GL_TEXTURE_2D, 0);
        glDisable(GL_TEXTURE_2D);
    }

    /** 释放所有缓存的纹理。 */
    public void dispose() {
        for (int tex : cache.values()) glDeleteTextures(tex);
        cache.clear();
    }
}
