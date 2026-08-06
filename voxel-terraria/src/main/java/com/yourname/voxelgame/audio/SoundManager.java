package com.yourname.voxelgame.audio;

import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;

/**
 * 音效系统：用 javax.sound.midi 合成器播放短促音效，无需音频文件。
 * 线程安全：每个音效在独立线程异步触发，避免阻塞主循环。
 */
public final class SoundManager {

    private static Synthesizer synth;
    private static MidiChannel channel;
    private static boolean ok = false;

    static {
        try {
            synth = MidiSystem.getSynthesizer();
            synth.open();
            MidiChannel[] chs = synth.getChannels();
            if (chs.length > 0) channel = chs[0];
            ok = (channel != null);
        } catch (Exception e) {
            System.err.println("SoundManager init failed: " + e.getMessage());
            ok = false;
        }
    }

    public enum Sound {
        BREAK(60, 80, 120),   // 挖块：低音短促
        PLACE(67, 90, 100),    // 放块：中音
        HURT(50, 100, 200),    // 受伤：低沉
        CRAFT(72, 80, 150),    // 合成：高音清脆
        JUMP(76, 70, 80),      // 跳跃
        HIT(55, 100, 100),     // 命中怪
        CLICK(80, 80, 60),     // UI 点击
        DAY(64, 70, 300),      // 昼切换
        PICKUP(84, 70, 80);    // 拾取

        public final int note, velocity, durationMs;
        Sound(int n, int v, int d) { note = n; velocity = v; durationMs = d; }
    }

    /** 异步播放一个音效。 */
    public static void play(Sound s) {
        if (!ok) return;
        Thread t = new Thread(() -> {
            try {
                channel.noteOn(s.note, s.velocity);
                Thread.sleep(s.durationMs);
                channel.noteOff(s.note);
            } catch (InterruptedException ie) {
                // 忽略
            }
        }, "snd");
        t.setDaemon(true);
        t.start();
    }

    public static void shutdown() {
        if (synth != null) {
            try { synth.close(); } catch (Exception e) {}
        }
    }
}
