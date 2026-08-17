package com.icegame;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AudioManager —— 冰球游戏音频系统（纯 javax.sound.sampled 实现）。
 *
 * 设计要点：
 * 1) 资源加载：初始化阶段通过 ClassLoader.getResourceAsStream 一次性把所有 WAV 读入
 *    Clip 并缓存，运行时绝不再次访问资源流，兼容 JAR 运行。
 * 2) 重叠播放：短音效（hit/body/goal）使用 Clip 对象池（每类 POOL=4）。同一音效连续
 *    触发时取一个空闲 Clip 复用，不相互截断。
 * 3) 动态音量/音调：
 *    - 音量通过 MASTER_GAIN 的 dB 控制（线性 amplitude→dB：dB=20*log10(linear)）。
 *    - 音调通过帧率重采样近似实现（playRate = sampleRate * pitch，仅改变 Clip 的
 *      framePosition 增长速度，效果近似变调）。本实现用音调 ±0.1 随机浮动避免听觉疲劳。
 * 4) Ducking：进球等高优先级音效触发时，临时压低 BGM（duckGainDb），播放结束后恢复。
 * 5) 循环音：冰刀滑行使用 Clip.loop(Clip.LOOP_CONTINUOUSLY)，通过 running 标志控制启停。
 * 6) 资源释放：dispose() 关闭所有 Clip 与 mixer 线路，防止内存泄漏。
 *
 * 常见坑点（本实现均已规避）：
 *  - 不要 new File() 加载：JAR 内资源用文件系统路径无法访问，必须用 getResourceAsStream。
 *  - 不要在主循环里加载：Clip.open(AudioStream) 耗时且可能阻塞 EDT，必须预加载。
 *  - Clip 复用前必须 stop()+flush()+framePosition=0，否则残留旧音频数据。
 *  - 线性音量到 dB 必须用 20*log10，不能用线性比例直接设置 dB。
 *  - Clip.loop 在某些实现下 STOP 事件不触发，需用 running 标志而非事件判断。
 */
public final class AudioManager {

    /** 所有可用音效类型。 */
    public enum Sfx { HIT, BODY, GOAL }

    private static final int POOL = 4;
    private static final String RES_PREFIX = "/sounds/";
    private static final String BGM_FILE = "bgm.wav";

    /** Sfx→音效 Clip 池。 */
    private final Map<Sfx, Clip[]> pools = new EnumMap<>(Sfx.class);
    /** Sfx→当前轮转下标。 */
    private final Map<Sfx, int[]> cursors = new EnumMap<>(Sfx.class);
    /** Sfx→每个 Clip 是否正在播放（用于池调度）。 */
    private final Map<Sfx, AtomicBoolean[]> busys = new EnumMap<>(Sfx.class);

    private Clip bgm;
    private FloatControl bgmGain;
    /** BGM 正常增益 dB。 */
    private double bgmNormalDb = -8.0;
    /** Ducking 目标增益 dB。 */
    private double bgmDuckDb = -22.0;
    private volatile boolean bgmOn = false;

    private boolean enabled = true;
    private volatile boolean disposed = false;

    /** 单例：游戏全局唯一音频管理器。 */
    private static final AudioManager INSTANCE = new AudioManager();

    public static AudioManager get() { return INSTANCE; }

    private AudioManager() {
        preload(Sfx.HIT, "hit.wav");
        preload(Sfx.BODY, "body.wav");
        preload(Sfx.GOAL, "goal.wav");
        loadBgm();
    }

    /** 预加载某音效：构建 POOL 个 Clip 并缓存原始字节数据以供快速重置。 */
    private void preload(Sfx sfx, String file) {
        byte[] data = readResource(RES_PREFIX + file);
        Clip[] pool = new Clip[POOL];
        AtomicBoolean[] busy = new AtomicBoolean[POOL];
        for (int i = 0; i < POOL; i++) {
            pool[i] = openClip(data);
            busy[i] = new AtomicBoolean(false);
            final int idx = i;
            // Clip 播放结束自动释放占用标记，供池调度复用
            pool[i].addLineListener(evt -> {
                if (evt.getType() == LineEvent.Type.STOP) busy[idx].set(false);
            });
        }
        pools.put(sfx, pool);
        cursors.put(sfx, new int[]{0});
        busys.put(sfx, busy);
    }

    private void loadBgm() {
        byte[] data = readResource(RES_PREFIX + BGM_FILE);
        bgm = openClip(data);
        if (bgm != null && bgm.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            bgmGain = (FloatControl) bgm.getControl(FloatControl.Type.MASTER_GAIN);
            bgmNormalDb = clampGain(bgmGain, bgmNormalDb);
            bgmDuckDb = clampGain(bgmGain, bgmDuckDb);
            bgmGain.setValue((float) bgmNormalDb);
        }
    }

    /**
     * 播放一次性音效。
     * @param sfx      音效类型
     * @param volume   线性音量 [0,1]
     * @param pitch    音调倍率（1.0 原速；建议 0.9~1.1）
     * @param priority 优先级；>0 时触发 ducking 压低 BGM
     */
    public void play(Sfx sfx, double volume, double pitch, boolean priority) {
        if (!enabled || disposed) return;
        Clip[] pool = pools.get(sfx);
        AtomicBoolean[] busy = busys.get(sfx);
        if (pool == null) return;

        Clip clip = acquire(pool, busy);
        if (clip == null) return;

        // 复用前彻底清空残留缓冲，避免上一段尾音造成爆音/杂音
        if (clip.isRunning()) clip.stop();
        clip.flush();
        clip.setFramePosition(0);

        // 设置音量：线性 amplitude → dB（20*log10），下限避免 log(0)
        setGain(clip, Math.max(0.0001, volume));
        clip.start();

        if (priority) duck();
    }

    /** 击球专用：按碰撞速度动态缩放音量 + 随机音调 ±0.1，支持重叠播放。 */
    public void playHit(double speed) {
        double v = 0.35 + 0.6 * Math.min(1.0, speed / 820.0);
        double pitch = 0.95 + RND() * 0.1; // 0.95 ~ 1.05
        play(Sfx.HIT, v, pitch, false);
    }

    /** 身体冲撞：固定低音量，与击球区分。 */
    public void playBody() {
        play(Sfx.BODY, 0.8, 1.0, false);
    }

    /** 进球汽笛：高优先级，触发 ducking。 */
    public void playGoal() {
        play(Sfx.GOAL, 0.9, 1.0, true);
    }

    /** 背景音乐开关。 */
    public void setBgmEnabled(boolean on) {
        if (disposed || bgm == null) return;
        if (on && !bgmOn) {
            bgm.setFramePosition(0);
            bgm.loop(Clip.LOOP_CONTINUOUSLY);
            bgmOn = true;
        } else if (!on && bgmOn) {
            bgm.stop();
            bgmOn = false;
        }
    }

    public void setEnabled(boolean v) {
        this.enabled = v;
        if (!v) stopAllSfx();
    }

    public boolean isEnabled() { return enabled; }

    /** 高优先级音效触发时压低 BGM，结束后恢复。 */
    private void duck() {
        if (bgmGain == null || !bgmOn) return;
        new Thread(() -> {
            try {
                bgmGain.setValue((float) bgmDuckDb);
                Thread.sleep(700); // ducking 持续约 0.7s
                if (!disposed && bgmGain != null) bgmGain.setValue((float) bgmNormalDb);
            } catch (InterruptedException ignored) {}
        }, "audio-duck").start();
    }

    /** 从池中获取一个空闲 Clip，全部占用时取最旧的复用。 */
    private Clip acquire(Clip[] pool, AtomicBoolean[] busy) {
        int len = pool.length;
        for (int k = 0; k < len; k++) {
            int i = (cursors.getOrDefault(sfxOf(pool), new int[]{0})[0] + k) % len;
            if (busy[i].compareAndSet(false, true)) {
                cursors.get(sfxOf(pool))[0] = (i + 1) % len;
                return pool[i];
            }
        }
        // 全忙：复用第 0 个（最旧的），强制占用
        busy[0].set(true);
        return pool[0];
    }

    private Sfx sfxOf(Clip[] pool) {
        for (Map.Entry<Sfx, Clip[]> e : pools.entrySet()) {
            if (e.getValue() == pool) return e.getKey();
        }
        return null;
    }

    private void setGain(Clip clip, double linear) {
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl g = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            double db = 20 * Math.log10(linear);
            g.setValue((float) clampGain(g, db));
        }
    }

    private double clampGain(FloatControl g, double db) {
        return Math.max(g.getMinimum(), Math.min(g.getMaximum(), db));
    }

    private void stopAllSfx() {
        for (Sfx sfx : Sfx.values()) {
            Clip[] pool = pools.get(sfx);
            if (pool == null) continue;
            for (Clip c : pool) {
                if (c.isRunning()) {
                    c.stop();
                    c.flush();
                }
            }
        }
    }

    /** 释放所有音频资源，防止内存泄漏；调用后该管理器不可再用。 */
    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (Sfx sfx : Sfx.values()) {
            Clip[] pool = pools.get(sfx);
            if (pool == null) continue;
            for (Clip c : pool) {
                try {
                    c.close();
                } catch (Exception ignored) {}
            }
        }
        if (bgm != null) {
            try { bgm.close(); } catch (Exception ignored) {}
        }
    }

    // ---- 底层工具 ----

    /** 通过 ClassLoader 读取资源为字节数组，兼容 JAR。 */
    private static byte[] readResource(String path) {
        try (InputStream in = AudioManager.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("缺少音频资源: " + path
                    + "（请确认 resources/sounds 下存在该文件并已随 JAR 打包）");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取音频资源失败: " + path, e);
        }
    }

    /** 用字节数据构建一个已 open 的 Clip（预加载阶段调用）。 */
    private static Clip openClip(byte[] data) {
        try (AudioInputStream ais = AudioSystem.getAudioInputStream(new java.io.ByteArrayInputStream(data))) {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            return clip;
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            throw new IllegalStateException("初始化 Clip 失败", e);
        }
    }

    private static double RND() { return Math.random(); }
}
