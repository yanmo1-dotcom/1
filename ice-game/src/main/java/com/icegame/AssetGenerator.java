package com.icegame;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioFileFormat;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

/**
 * 离线工具：在构建前生成游戏所需的 WAV 音效资产（44100Hz / 16bit / mono）。
 * 生成结果提交到 src/main/resources/sounds，运行时由 AudioManager 通过
 * ClassLoader.getResourceAsStream 加载，不依赖文件系统路径，兼容 JAR 运行。
 *
 * 设计思路：
 *  - hit: 带噪声的木质敲击，快速衰减，模拟球拍击球
 *  - body: 低频闷响 + 短噪声，模拟身体冲撞
 *  - goal: 持续约 0.5s 的双音上升汽笛
 *  - skate: 可循环的冰刀滑行摩擦噪声，首位尾衔接平滑
 *  - bgm:  简单循环节奏背景（用于演示 ducking）
 */
public final class AssetGenerator {
    private static final float RATE = 44100f;
    private static final Random RND = new Random(42);

    public static void main(String[] args) throws Exception {
        Path dir = Paths.get("src/main/resources/sounds");
        Files.createDirectories(dir);
        writeWav(dir.resolve("hit.wav"), hit());
        writeWav(dir.resolve("body.wav"), body());
        writeWav(dir.resolve("goal.wav"), goal());
        writeWav(dir.resolve("bgm.wav"), bgm());
        System.out.println("Generated assets into " + dir.toAbsolutePath());
    }

    private static void writeWav(Path path, double[] samples) throws Exception {
        byte[] bytes = toPcm16(samples);
        AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
        try (AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(bytes), fmt, samples.length);
             OutputStream out = Files.newOutputStream(path)) {
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
        }
    }

    private static byte[] toPcm16(double[] samples) {
        byte[] buf = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            double s = Math.max(-1, Math.min(1, samples[i]));
            short v = (short) (s * Short.MAX_VALUE);
            buf[i * 2] = (byte) (v & 0xff);
            buf[i * 2 + 1] = (byte) ((v >> 8) & 0xff);
        }
        return buf;
    }

    private static double[] hit() {
        double dur = 0.08;
        int n = (int) (RATE * dur);
        double[] s = new double[n];
        int fade = (int) (RATE * 0.005);
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double env = Math.exp(-t * 40);
            double tone = Math.sin(2 * Math.PI * 520 * t);
            double noise = noise(i);
            double fadeEnv = 1.0;
            if (i < fade) fadeEnv = (double) i / fade;
            if (i > n - fade) fadeEnv = (double) (n - i) / fade;
            s[i] = (tone * 0.65 + noise * 0.35) * env * fadeEnv * 0.9;
        }
        return s;
    }

    private static double[] body() {
        double dur = 0.22;
        int n = (int) (RATE * dur);
        double[] s = new double[n];
        int fade = (int) (RATE * 0.008); // 首尾淡入淡出，消除爆音杂音
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double env = Math.exp(-t * 14);
            double low = Math.sin(2 * Math.PI * 120 * t);
            double thud = Math.sin(2 * Math.PI * 70 * t) * 0.6;
            double fadeEnv = 1.0;
            if (i < fade) fadeEnv = (double) i / fade;
            if (i > n - fade) fadeEnv = (double) (n - i) / fade;
            s[i] = (low + thud) * env * fadeEnv * 0.7;
        }
        return s;
    }

    private static double[] goal() {
        double dur = 0.55;
        int n = (int) (RATE * dur);
        double[] s = new double[n];
        double f1 = 660, f2 = 990;
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            double rise = Math.min(1, t / 0.06);
            double env = Math.min(1, t / 0.04) * Math.exp(-t * 3.5);
            double freq = f1 + (f2 - f1) * rise;
            double tone = Math.sin(2 * Math.PI * freq * t) + Math.sin(2 * Math.PI * freq * 2 * t) * 0.2;
            s[i] = tone * env * 0.55;
        }
        return s;
    }

    private static double[] bgm() {
        // 简短循环节奏：低音节拍 + 轻微和声，约 1.2s
        double dur = 1.2;
        int n = (int) (RATE * dur);
        double[] s = new double[n];
        double[] bassFreqs = {130.81, 130.81, 174.61, 146.83};
        for (int i = 0; i < n; i++) {
            double t = (double) i / RATE;
            int beat = (int) (t / 0.3) % 4;
            double beatEnv = Math.exp(-((t % 0.3) * 18));
            double bass = Math.sin(2 * Math.PI * bassFreqs[beat] * t) * beatEnv * 0.4;
            double pad = Math.sin(2 * Math.PI * 392 * t) * 0.08 + Math.sin(2 * Math.PI * 523 * t) * 0.06;
            s[i] = (bass + pad) * 0.7;
        }
        return s;
    }

    private static double noise(int i) {
        return RND.nextDouble() * 2 - 1;
    }
}
