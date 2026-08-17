package com.jackal.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * AudioManager —— 全局音频管理单例。
 * <p>
 * 统一管理背景音乐（{@link Music}）与短音效（{@link Sound}）：
 * <ul>
 *   <li>BGM 用 Music 流式加载，支持循环播放、音量、暂停/恢复</li>
 *   <li>SFX 用 Sound 加载到内存，支持按事件名播放、节流防重叠</li>
 *   <li>对缺失音效文件优雅降级（打印日志而非崩溃），便于资源未就绪时继续开发</li>
 * </ul>
 *
 * <h3>单例实现</h3>
 * 用静态持有 + dispose 时置空，而非 enum 单例：LibGDX 的音频资源依赖
 * {@link com.badlogic.gdx.Audio} 在 Application 启动后才可用，
 * 单例实例在 create() 时创建，dispose() 时释放，生命周期与游戏一致。
 *
 * <h3>防重叠（节流）</h3>
 * 连续开枪等高频事件若每帧都 new 一个播放实例会爆音。每个 SFX 事件
 * 维护一个"上次播放时间"，距上次小于 {@link #SFX_MIN_INTERVAL} 则跳过，
 * 避免短时间内同一音效叠成噪音墙。不同事件互不影响。
 *
 * <h3>assets/sounds 目录配置</h3>
 * 音效文件放 {@code core/assets/sounds/}。desktop 模块 build.gradle 的
 * {@code run.workingDir = ../core/assets}，故 internal 路径写 {@code sounds/xxx.wav}。
 * 构建时 core/build.gradle 已把 assets 加入 resources，jar 会包含音频。
 *
 * @author Jackal Dev Team
 */
public class AudioManager implements Disposable {

    /** 单例实例（create 后由 AudioManager.create() 设置） */
    private static AudioManager instance;

    /**
     * 初始化单例：加载所有音效与 BGM。
     * <p>
     * 必须在 LibGDX Application 创建后（如 Game.create 中）调用。
     */
    public static AudioManager create() {
        instance = new AudioManager();
        return instance;
    }

    /** @return 单例（create 之前为 null） */
    public static AudioManager get() {
        return instance;
    }

    /** 释放单例并置空，之后不可再用直到下一次 create */
    public static void disposeInstance() {
        if (instance != null) {
            instance.dispose();
            instance = null;
        }
    }

    // ============== 配置 ==============

    /** BGM 音量 [0,1] */
    private float musicVolume = 0.4f;
    /** SFX 音量 [0,1] */
    private float sfxVolume = 0.6f;
    /** 同一 SFX 两次播放的最小间隔（秒）。小于此值跳过，防止连续开枪爆音 */
    private static final float SFX_MIN_INTERVAL = 0.04f;
    /** BGM 是否启用 */
    private boolean musicEnabled = true;
    /** SFX 是否启用 */
    private boolean sfxEnabled = true;

    /** 已加载的短音效表：事件名 → Sound。键用业务名而非文件名，便于换源 */
    private final ObjectMap<String, Sound> sounds = new ObjectMap<>();
    /** 每个事件的最近一次播放时间（秒，Gdx.audio 纪元）。用于节流防重叠 */
    private final ObjectMap<String, Float> lastPlayTime = new ObjectMap<>();
    /** BGM Music 实例 */
    private Music bgm;

    /** 私有构造：加载资源。缺失文件时降级，不抛异常 */
    private AudioManager() {
        // —— 加载短音效 ——
        loadSfx("mg", "sounds/mg.wav");
        loadSfx("grenade", "sounds/grenade.wav");
        loadSfx("rocket", "sounds/rocket.wav");
        loadSfx("enemy_die", "sounds/enemy_die.wav");
        loadSfx("rescue", "sounds/rescue.wav");
        loadSfx("hit", "sounds/hit.wav");
        loadSfx("gameover", "sounds/gameover.wav");

        // —— 加载并启动 BGM ——
        loadBgm("sounds/bgm.wav");
    }

    /**
     * 加载一个短音效。文件不存在时打印日志并跳过，播放时静默返回。
     */
    private void loadSfx(String name, String path) {
        FileHandle fh = Gdx.files.internal(path);
        if (!fh.exists()) {
            Gdx.app.log("Audio", "音效缺失（降级静音）: " + path);
            return;
        }
        try {
            sounds.put(name, Gdx.audio.newSound(fh));
        } catch (Exception e) {
            Gdx.app.log("Audio", "音效加载失败: " + path + " - " + e.getMessage());
        }
    }

    /**
     * 加载 BGM 并循环播放。缺失时降级（无 BGM）。
     */
    private void loadBgm(String path) {
        FileHandle fh = Gdx.files.internal(path);
        if (!fh.exists()) {
            Gdx.app.log("Audio", "BGM 缺失（降级静音）: " + path);
            return;
        }
        try {
            bgm = Gdx.audio.newMusic(fh);
            bgm.setLooping(true);
            bgm.setVolume(musicVolume);
            if (musicEnabled) bgm.play();
            Gdx.app.log("Audio", "BGM 已加载并循环播放");
        } catch (Exception e) {
            Gdx.app.log("Audio", "BGM 加载失败: " + path + " - " + e.getMessage());
        }
    }

    // ============== 播放接口 ==============

    /**
     * 播放一个短音效，带节流防重叠。
     * <p>
     * 若距上次播放同事件不足 {@link #SFX_MIN_INTERVAL}，则跳过本次播放。
     * 这样按住左键连射机枪时，0.04s 间隔（≈每秒25次）已足够听清每发，
     * 又不会叠成持续白噪。手雷/火箭等低频事件节流几乎不触发。
     *
     * @param name 音效事件名（mg/grenade/rocket/enemy_die/rescue/hit/gameover）
     */
    public void playSfx(String name) {
        if (!sfxEnabled) return;
        Sound s = sounds.get(name);
        if (s == null) return; // 未加载（缺文件），静默
        float now = getCurrentTime();
        Float last = lastPlayTime.get(name);
        if (last != null && (now - last) < SFX_MIN_INTERVAL) {
            return; // 节流跳过
        }
        lastPlayTime.put(name, now);
        s.play(sfxVolume);
    }

    /** 获取当前时间（秒，单调）。用于节流比较，绝对值不重要，只需单调递增。 */
    private static float getCurrentTime() {
        return com.badlogic.gdx.utils.TimeUtils.nanoTime() / 1_000_000_000f;
    }

    /** 暂停 BGM（如切关卡时） */
    public void pauseBgm() {
        if (bgm != null && bgm.isPlaying()) bgm.pause();
    }

    /** 恢复 BGM */
    public void resumeBgm() {
        if (bgm != null && musicEnabled) bgm.play();
    }

    /** @param v BGM 音量 [0,1] */
    public void setMusicVolume(float v) {
        musicVolume = Math.max(0f, Math.min(1f, v));
        if (bgm != null) bgm.setVolume(musicVolume);
    }

    /** @param v SFX 音量 [0,1] */
    public void setSfxVolume(float v) {
        sfxVolume = Math.max(0f, Math.min(1f, v));
    }

    /** 静音 BGM */
    public void setMusicEnabled(boolean enabled) {
        musicEnabled = enabled;
        if (bgm == null) return;
        if (enabled && !bgm.isPlaying()) bgm.play();
        else if (!enabled && bgm.isPlaying()) bgm.pause();
    }

    /** 静音 SFX（不释放资源，便于运行时开关） */
    public void setSfxEnabled(boolean enabled) {
        sfxEnabled = enabled;
    }

    /** @return BGM 是否启用 */
    public boolean isMusicEnabled() {
        return musicEnabled;
    }

    /** @return SFX 是否启用 */
    public boolean isSfxEnabled() {
        return sfxEnabled;
    }

    @Override
    public void dispose() {
        for (Sound s : sounds.values()) {
            if (s != null) s.dispose();
        }
        sounds.clear();
        lastPlayTime.clear();
        if (bgm != null) {
            bgm.dispose();
            bgm = null;
        }
    }
}
