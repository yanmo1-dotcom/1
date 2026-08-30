package tailai;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 纯代码合成的音效与背景音乐播放器（无外部音频文件）。
 * 音效：8-bit 单声道 PCM，异步播放，80ms 去重。
 * 背景音乐：后台线程循环播放简单旋律，支持白天/夜晚/Boss/地下四种。
 */
public class SoundPlayer {

    private static final int SAMPLE_RATE = 22050;
    private static final long MIN_INTERVAL_MS = 80;
    private static volatile boolean muted = false;
    private static volatile boolean musicMuted = false;
    private static final Map<String, Long> LAST_PLAY = new HashMap<>();
    private static final ExecutorService SFX_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sfx-player");
        t.setDaemon(true);
        return t;
    });

    // ================= 背景音乐 =================
    public enum Music { DAY, NIGHT, BOSS, UNDERGROUND }
    private static volatile Music currentMusic = null;
    private static volatile SourceDataLine musicLine = null;
    private static volatile Thread musicThread = null;
    private static final Object musicLock = new Object();

    public static void setMuted(boolean m) {
        muted = m;
    }

    public static boolean isMuted() {
        return muted;
    }

    public static void setMusicMuted(boolean m) {
        musicMuted = m;
        if (m && musicLine != null) {
            musicLine.stop();
        }
    }

    public static boolean isMusicMuted() {
        return musicMuted;
    }

    /** 切换背景音乐；传 null 停止。 */
    public static void playMusic(Music m) {
        if (m == currentMusic) return;
        currentMusic = m;
        synchronized (musicLock) {
            if (musicLine != null) {
                musicLine.stop();
                musicLine.close();
                musicLine = null;
            }
            if (musicThread != null) {
                musicThread.interrupt();
                musicThread = null;
            }
            if (m == null || musicMuted) return;
            musicThread = new Thread(() -> musicLoop(m), "bgm-" + m);
            musicThread.setDaemon(true);
            musicThread.start();
        }
    }

    /** 背景音乐循环线程。 */
    private static void musicLoop(Music m) {
        try {
            AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
            musicLine = AudioSystem.getSourceDataLine(fmt);
            musicLine.open(fmt, 8192);
            musicLine.start();
            while (!Thread.interrupted() && currentMusic == m && !musicMuted) {
                byte[] melody = generateMelody(m);
                if (melody == null) break;
                // 分段写入，避免阻塞
                int pos = 0;
                while (pos < melody.length && currentMusic == m && !musicMuted) {
                    int chunk = Math.min(1024, melody.length - pos);
                    musicLine.write(melody, pos, chunk);
                    pos += chunk;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (musicLine != null) {
                musicLine.stop();
                musicLine.close();
                musicLine = null;
            }
        }
    }

    /** 生成一段背景音乐旋律（约 8 秒循环）。 */
    private static byte[] generateMelody(Music m) {
        float bpm;
        int[] melody;
        int[] bass;
        float vol;
        switch (m) {
            case DAY:
                // 白天：C 大调轻快旋律
                bpm = 120;
                melody = new int[]{523, 587, 659, 698, 784, 698, 659, 587,
                                   523, 659, 784, 880, 784, 659, 587, 523};
                bass = new int[]{131, 131, 165, 165, 196, 196, 175, 175};
                vol = 0.12f;
                break;
            case NIGHT:
                // 夜晚：A 小调神秘旋律
                bpm = 90;
                melody = new int[]{440, 523, 659, 523, 440, 392, 440, 523,
                                   659, 784, 659, 523, 440, 392, 349, 440};
                bass = new int[]{110, 110, 131, 131, 98, 98, 110, 110};
                vol = 0.10f;
                break;
            case BOSS:
                // Boss：紧张的低音+快速旋律
                bpm = 160;
                melody = new int[]{330, 349, 392, 440, 392, 349, 330, 311,
                                   330, 392, 440, 523, 440, 392, 349, 330};
                bass = new int[]{82, 82, 87, 87, 92, 92, 82, 82};
                vol = 0.15f;
                break;
            case UNDERGROUND:
            default:
                // 地下：低沉氛围
                bpm = 70;
                melody = new int[]{262, 294, 330, 294, 262, 233, 262, 294,
                                   330, 349, 330, 294, 262, 233, 220, 262};
                bass = new int[]{65, 65, 73, 73, 82, 82, 73, 73};
                vol = 0.08f;
                break;
        }
        float beatDur = 60f / bpm;
        int totalBeats = melody.length;
        float totalDur = totalBeats * beatDur;
        int n = (int) (SAMPLE_RATE * totalDur);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / SAMPLE_RATE;
            int beat = (int) (t / beatDur);
            float beatT = (t % beatDur) / beatDur;
            // 主旋律（方波，8-bit 风格）
            float mel = 0;
            if (beat < melody.length) {
                float phase = (float) (2 * Math.PI * melody[beat] * (t - beat * beatDur));
                mel = (float) (Math.sin(phase) >= 0 ? 0.6 : -0.6);
                // 音符包络
                float env = beatT < 0.1f ? beatT / 0.1f : (beatT > 0.8f ? (1 - beatT) / 0.2f : 1f);
                mel *= env;
            }
            // 低音（正弦，每两拍一个）
            float bs = 0;
            int bassIdx = Math.min(beat / 2, bass.length - 1);
            if (bassIdx < bass.length) {
                float bphase = (float) (2 * Math.PI * bass[bassIdx] * t);
                bs = (float) Math.sin(bphase) * 0.5f;
            }
            float sample = (mel + bs) * 0.5f;
            out[i] = (byte) (sample * vol * 127);
        }
        return out;
    }

    /** 播放指定音效；若 80ms 内已播过同名则跳过。 */
    public static void play(String name) {
        if (muted) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (LAST_PLAY) {
            Long last = LAST_PLAY.get(name);
            if (last != null && now - last < MIN_INTERVAL_MS) {
                return;
            }
            LAST_PLAY.put(name, now);
        }
        SFX_POOL.execute(() -> {
            try {
                byte[] data = generate(name);
                if (data == null || data.length == 0) {
                    return;
                }
                AudioFormat fmt = new AudioFormat(SAMPLE_RATE, 8, 1, true, false);
                SourceDataLine line = AudioSystem.getSourceDataLine(fmt);
                line.open(fmt, Math.min(data.length, 4096));
                line.start();
                line.write(data, 0, data.length);
                line.drain();
                line.close();
            } catch (Exception ignored) {
                // 音频设备不可用时静默失败，不影响游戏
            }
        });
    }

    private static byte[] generate(String name) {
        switch (name) {
            case "jump":
                return sweep(420, 680, 0.12f, 0.18f, false);
            case "mine":
                return noise(0.07f, 0.22f);
            case "hurt":
                return sweep(320, 110, 0.18f, 0.28f, false);
            case "kill":
                return sweep(500, 820, 0.16f, 0.22f, false);
            case "pickup":
                return sweep(880, 1320, 0.09f, 0.2f, false);
            case "craft":
                return chord(new int[]{523, 659, 784}, 0.22f, 0.22f);
            case "equip":
                return sweep(180, 420, 0.11f, 0.32f, true);
            case "boss":
                return sweep(90, 55, 0.45f, 0.38f, true);
            case "chat":
                return sweep(700, 900, 0.06f, 0.15f, false);
            case "rain":
                return noise(0.12f, 0.08f);
            default:
                return null;
        }
    }

    /** 频率渐变音。square=true 用方波，false 用正弦。 */
    private static byte[] sweep(float f0, float f1, float dur, float vol, boolean square) {
        int n = (int) (SAMPLE_RATE * dur);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float freq = f0 + (f1 - f0) * t;
            float phase = (float) (2 * Math.PI * freq * i / SAMPLE_RATE);
            float sample = square ? (Math.sin(phase) >= 0 ? 1f : -1f) : (float) Math.sin(phase);
            float env = 1f;
            if (t < 0.1f) {
                env = t / 0.1f;
            } else if (t > 0.7f) {
                env = Math.max(0, (1f - t) / 0.3f);
            }
            out[i] = (byte) (sample * env * vol * 127);
        }
        return out;
    }

    /** 白噪声（带低通感的随机衰减）。 */
    private static byte[] noise(float dur, float vol) {
        int n = (int) (SAMPLE_RATE * dur);
        byte[] out = new byte[n];
        float last = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float raw = (float) (Math.random() * 2 - 1);
            last = last * 0.6f + raw * 0.4f;
            float env = (float) Math.pow(1 - t, 1.5);
            out[i] = (byte) (last * env * vol * 127);
        }
        return out;
    }

    /** 多频率叠加和弦（正弦）。 */
    private static byte[] chord(int[] freqs, float dur, float vol) {
        int n = (int) (SAMPLE_RATE * dur);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float sum = 0;
            for (int f : freqs) {
                sum += (float) Math.sin(2 * Math.PI * f * i / SAMPLE_RATE);
            }
            sum /= freqs.length;
            float env = 1f;
            if (t < 0.08f) {
                env = t / 0.08f;
            } else if (t > 0.6f) {
                env = Math.max(0, (1f - t) / 0.4f);
            }
            out[i] = (byte) (sum * env * vol * 127);
        }
        return out;
    }
}
