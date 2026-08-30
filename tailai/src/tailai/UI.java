package tailai;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;

/**
 * UI 常量池：所有渲染中重复使用的 Color/Font/Stroke 预创建为静态常量，
 * 避免每帧 new 导致的 GC 压力。只放跨方法复用的常量，方法内一次性的不搬。
 */
public final class UI {
    private UI() {}

    // ---- 常用字体 ----
    public static final Font FONT_PLAIN_10 = new Font("Dialog", Font.PLAIN, 10);
    public static final Font FONT_PLAIN_11 = new Font("Dialog", Font.PLAIN, 11);
    public static final Font FONT_PLAIN_12 = new Font("Dialog", Font.PLAIN, 12);
    public static final Font FONT_PLAIN_14 = new Font("Dialog", Font.PLAIN, 14);
    public static final Font FONT_PLAIN_16 = new Font("Dialog", Font.PLAIN, 16);
    public static final Font FONT_PLAIN_18 = new Font("Dialog", Font.PLAIN, 18);
    public static final Font FONT_PLAIN_20 = new Font("Dialog", Font.PLAIN, 20);
    public static final Font FONT_BOLD_11 = new Font("Dialog", Font.BOLD, 11);
    public static final Font FONT_BOLD_12 = new Font("Dialog", Font.BOLD, 12);
    public static final Font FONT_BOLD_13 = new Font("Dialog", Font.BOLD, 13);
    public static final Font FONT_BOLD_14 = new Font("Dialog", Font.BOLD, 14);
    public static final Font FONT_BOLD_15 = new Font("Dialog", Font.BOLD, 15);
    public static final Font FONT_BOLD_16 = new Font("Dialog", Font.BOLD, 16);
    public static final Font FONT_BOLD_18 = new Font("Dialog", Font.BOLD, 18);
    public static final Font FONT_BOLD_20 = new Font("Dialog", Font.BOLD, 20);
    public static final Font FONT_BOLD_36 = new Font("Dialog", Font.BOLD, 36);
    public static final Font FONT_BOLD_40 = new Font("Dialog", Font.BOLD, 40);
    public static final Font FONT_BOLD_64 = new Font("Dialog", Font.BOLD, 64);

    // ---- 半透明黑（面板背景） ----
    public static final Color BLACK_120 = new Color(0, 0, 0, 120);
    public static final Color BLACK_130 = new Color(0, 0, 0, 130);
    public static final Color BLACK_140 = new Color(0, 0, 0, 140);
    public static final Color BLACK_150 = new Color(0, 0, 0, 150);
    public static final Color BLACK_160 = new Color(0, 0, 0, 160);
    public static final Color BLACK_170 = new Color(0, 0, 0, 170);
    public static final Color BLACK_180 = new Color(0, 0, 0, 180);

    // ---- 白色及半透明白 ----
    public static final Color WHITE = Color.WHITE;
    public static final Color WHITE_120 = new Color(255, 255, 255, 120);
    public static final Color WHITE_180 = new Color(255, 255, 255, 180);
    public static final Color WHITE_200 = new Color(255, 255, 255, 200);
    public static final Color WHITE_230 = new Color(255, 255, 255, 230);
    public static final Color WHITE_240 = new Color(255, 255, 255, 240);

    // ---- 深灰/面板色 ----
    public static final Color DARK_20 = new Color(20, 22, 34);
    public static final Color DARK_24 = new Color(24, 24, 32);
    public static final Color DARK_26 = new Color(24, 26, 40);
    public static final Color DARK_30 = new Color(30, 30, 30);
    public static final Color DARK_38 = new Color(30, 30, 38);
    public static final Color DARK_40 = new Color(40, 40, 40);
    public static final Color DARK_46 = new Color(40, 46, 64);
    public static final Color DARK_52 = new Color(52, 50, 58);
    public static final Color DARK_60 = new Color(60, 60, 60);
    public static final Color DARK_70 = new Color(60, 60, 70);
    public static final Color DARK_90 = new Color(90, 88, 98);

    // ---- 浅灰/文字色 ----
    public static final Color GRAY_140 = new Color(140, 140, 160);
    public static final Color GRAY_150 = new Color(150, 150, 160);
    public static final Color GRAY_160 = new Color(160, 160, 175);
    public static final Color GRAY_180 = new Color(180, 175, 165);
    public static final Color GRAY_190 = new Color(180, 190, 205);
    public static final Color GRAY_200 = new Color(200, 200, 210);
    public static final Color GRAY_210 = new Color(200, 210, 220);
    public static final Color GRAY_220 = new Color(220, 220, 230);
    public static final Color GRAY_230 = new Color(230, 230, 230);
    public static final Color GRAY_235 = new Color(235, 235, 235);
    public static final Color GRAY_240 = new Color(240, 240, 240);

    // ---- 红色/生命 ----
    public static final Color RED_60 = new Color(60, 10, 20);
    public static final Color RED_80 = new Color(80, 10, 20);
    public static final Color RED_120 = new Color(120, 20, 20);
    public static final Color RED_140 = new Color(140, 60, 60);
    public static final Color RED_180 = new Color(180, 100, 60);
    public static final Color RED_190 = new Color(190, 60, 60);
    public static final Color RED_200 = new Color(200, 160, 160);
    public static final Color RED_220 = new Color(220, 50, 50);
    public static final Color RED_235 = new Color(235, 60, 80);
    public static final Color RED_255 = new Color(255, 60, 60);
    public static final Color RED_255_80 = new Color(255, 80, 80);
    public static final Color RED_200_LIGHT = new Color(255, 200, 200);
    public static final Color RED_220_LIGHT = new Color(255, 220, 220);
    public static final Color RED_240_LIGHT = new Color(255, 240, 240);

    // ---- 绿色/生命/自然 ----
    public static final Color GREEN_30 = new Color(30, 50, 30);
    public static final Color GREEN_60 = new Color(60, 110, 80);
    public static final Color GREEN_70 = new Color(70, 120, 90);
    public static final Color GREEN_90 = new Color(90, 170, 70);
    public static final Color GREEN_110 = new Color(110, 112, 118);
    public static final Color GREEN_120 = new Color(120, 200, 120);
    public static final Color GREEN_140 = new Color(140, 220, 120);
    public static final Color GREEN_160 = new Color(160, 220, 160);
    public static final Color GREEN_190 = new Color(190, 225, 255);
    public static final Color GREEN_200 = new Color(200, 240, 160);
    public static final Color GREEN_210 = new Color(210, 235, 210);
    public static final Color GREEN_50 = new Color(50, 160, 80);
    public static final Color GREEN_70B = new Color(70, 150, 60);
    public static final Color GREEN_90B = new Color(90, 190, 70);
    public static final Color GREEN_120B = new Color(120, 84, 50);
    public static final Color GREEN_150 = new Color(150, 190, 240);
    public static final Color GREEN_180 = new Color(180, 220, 160);
    public static final Color GREEN_200B = new Color(200, 230, 255);
    public static final Color GREEN_225 = new Color(225, 222, 205);
    public static final Color GREEN_230 = new Color(230, 235, 245);
    public static final Color GREEN_232 = new Color(232, 222, 200);
    public static final Color GREEN_235 = new Color(235, 235, 240);
    public static final Color GREEN_240 = new Color(240, 235, 225);

    // ---- 蓝色/魔法/水 ----
    public static final Color BLUE_120 = new Color(120, 200, 255);
    public static final Color BLUE_140 = new Color(140, 200, 230);
    public static final Color BLUE_150 = new Color(150, 180, 220);
    public static final Color BLUE_160 = new Color(160, 180, 255);
    public static final Color BLUE_180 = new Color(180, 220, 255);
    public static final Color BLUE_200 = new Color(200, 220, 240);
    public static final Color BLUE_220 = new Color(220, 250, 255);
    public static final Color BLUE_225 = new Color(220, 228, 240);
    public static final Color BLUE_120A = new Color(120, 200, 255, 200);
    public static final Color BLUE_150A = new Color(150, 180, 220, 180);
    public static final Color BLUE_180A = new Color(180, 220, 255, 180);
    public static final Color BLUE_180B = new Color(180, 220, 255, 220);
    public static final Color BLUE_190A = new Color(190, 225, 255, 65);
    public static final Color BLUE_200A = new Color(200, 230, 255);

    // ---- 黄色/金币/经验 ----
    public static final Color GOLD_150 = new Color(150, 110, 70);
    public static final Color GOLD_170 = new Color(170, 150, 110);
    public static final Color GOLD_180 = new Color(180, 100, 60);
    public static final Color GOLD_200 = new Color(200, 178, 120);
    public static final Color GOLD_200B = new Color(200, 180, 140);
    public static final Color GOLD_230 = new Color(230, 190, 150);
    public static final Color GOLD_240 = new Color(240, 230, 220);
    public static final Color GOLD_255 = new Color(255, 200, 60);
    public static final Color GOLD_255B = new Color(255, 180, 80);
    public static final Color GOLD_220 = new Color(255, 220, 90);
    public static final Color GOLD_220B = new Color(255, 220, 120);
    public static final Color GOLD_230B = new Color(255, 230, 120);
    public static final Color GOLD_230C = new Color(255, 230, 140);
    public static final Color GOLD_240B = new Color(255, 240, 160);
    public static final Color GOLD_255_230 = new Color(255, 255, 210, 230);
    public static final Color GOLD_230A = new Color(255, 230, 120, 55);

    // ---- 紫色/腐化 ----
    public static final Color PURPLE_40 = new Color(40, 10, 50);
    public static final Color PURPLE_80 = new Color(80, 30, 100);
    public static final Color PURPLE_120 = new Color(120, 80, 140);
    public static final Color PURPLE_140 = new Color(140, 70, 170);
    public static final Color PURPLE_150 = new Color(150, 70, 170);
    public static final Color PURPLE_180 = new Color(180, 140, 200);
    public static final Color PURPLE_180A = new Color(180, 100, 200, 120);
    public static final Color PURPLE_140A = new Color(140, 70, 170, 70);

    // ---- 粉色/护士 ----
    public static final Color PINK_230 = new Color(255, 230, 240);
    public static final Color PINK_255 = new Color(255, 120, 160);

    // ---- 描边 ----
    public static final BasicStroke STROKE_1 = new BasicStroke(1);
    public static final BasicStroke STROKE_3 = new BasicStroke(3);
    public static final BasicStroke STROKE_4 = new BasicStroke(4);
    public static final BasicStroke STROKE_7 = new BasicStroke(7);
    public static final BasicStroke STROKE_ROUND_14 = new BasicStroke(14, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
}
