import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * 程序化音效系统：用 javax.sound.sampled 实时合成 PCM 音效，无需任何外部音频文件。
 *
 * 【原理】所有音效都是数学波形（方波/正弦/白噪声）写入 byte 缓冲区后播放。
 *   - 射击：高频方波快速衰减
 *   - 爆炸：白噪声衰减
 *   - 受击：低频锯齿波
 *   - 拾取道具：上升正弦音阶
 *   - Boss 出现/死亡：低频持续 / 长爆炸
 * 【背景音乐】独立线程循环播放 4 小节简单旋律，可 M 键静音。
 * 【静音】muted=true 时所有 play* 方法直接返回；背景线程也暂停发声。
 *
 * 用 SourceDataLine 实时流式播放短音效，避免预生成大量 Clip 占用内存。
 */
public class SoundManager {

    private static final int SAMPLE_RATE = 22050;
    private static final int BYTES_PER_FRAME = 2; // 16-bit PCM

    private volatile boolean muted = false;
    private volatile boolean musicOn = true;
    private final java.util.concurrent.ExecutorService pool =
            java.util.concurrent.Executors.newCachedThreadPool();
    private final Thread musicThread;
    private volatile boolean running = true;

    public SoundManager() {
        musicThread = new Thread(this::musicLoop, "BGM-Thread");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    public void toggleMute() {
        muted = !muted;
    }

    public boolean isMuted() {
        return muted;
    }

    public void stop() {
        running = false;
        pool.shutdownNow();
    }

    /** 射击声：方波 880Hz，60ms 衰减。 */
    public void playShoot() {
        if (muted) return;
        playTone(880, 0.06, 0.25, WaveType.SQUARE, true);
    }

    /** 爆炸声：白噪声 250ms 衰减。 */
    public void playExplosion() {
        if (muted) return;
        playNoise(0.25, 0.4);
    }

    /** 受击声：锯齿波 220Hz，200ms 衰减。 */
    public void playHit() {
        if (muted) return;
        playTone(220, 0.20, 0.35, WaveType.SAW, true);
    }

    /** 拾取道具：上升正弦音阶。 */
    public void playPowerUp() {
        if (muted) return;
        playTone(523, 0.06, 0.3, WaveType.SINE, false);
        playToneDelayed(659, 0.06, 0.3, WaveType.SINE, 60);
        playToneDelayed(784, 0.10, 0.3, WaveType.SINE, 120);
    }

    /** Boss 出现警告：低频持续 800ms。 */
    public void playBossWarn() {
        if (muted) return;
        playTone(110, 0.80, 0.4, WaveType.SAW, false);
    }

    /** Boss 死亡：长爆炸 + 低频收尾。 */
    public void playBossDeath() {
        if (muted) return;
        playNoise(0.6, 0.5);
        playToneDelayed(80, 0.40, 0.4, WaveType.SAW, 300);
    }

    /** 按钮点击：短促正弦。 */
    public void playClick() {
        if (muted) return;
        playTone(660, 0.05, 0.3, WaveType.SINE, false);
    }

    // ===== 合成核心 =====

    private enum WaveType { SINE, SQUARE, SAW }

    private void playTone(double freq, double durSec, double volume, WaveType type, boolean decay) {
        int frames = (int) (SAMPLE_RATE * durSec);
        byte[] buffer = new byte[frames * BYTES_PER_FRAME];
        for (int i = 0; i < frames; i++) {
            double t = i / (double) SAMPLE_RATE;
            double sample;
            switch (type) {
                case SINE:
                    sample = Math.sin(2 * Math.PI * freq * t);
                    break;
                case SQUARE:
                    sample = Math.sin(2 * Math.PI * freq * t) >= 0 ? 1 : -1;
                    break;
                case SAW:
                default:
                    sample = 2 * ((freq * t) - Math.floor(freq * t + 0.5));
                    break;
            }
            double amp = decay ? volume * (1.0 - (double) i / frames) : volume;
            short val = (short) (sample * amp * Short.MAX_VALUE);
            buffer[i * 2]     = (byte) (val & 0xFF);
            buffer[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
        }
        playBuffer(buffer);
    }

    private void playToneDelayed(double freq, double durSec, double volume, WaveType type, int delayMs) {
        new Thread(() -> {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            playTone(freq, durSec, volume, type, true);
        }, "SFX-Delayed").start();
    }

    private void playNoise(double durSec, double volume) {
        int frames = (int) (SAMPLE_RATE * durSec);
        byte[] buffer = new byte[frames * BYTES_PER_FRAME];
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < frames; i++) {
            double sample = rnd.nextDouble() * 2 - 1;
            double amp = volume * (1.0 - (double) i / frames);
            short val = (short) (sample * amp * Short.MAX_VALUE);
            buffer[i * 2]     = (byte) (val & 0xFF);
            buffer[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
        }
        playBuffer(buffer);
    }

    /**
     * 异步播放：在独立线程写入音频线并 drain。
     *
     * 【为什么异步】drain() 会阻塞直到音频播完（爆炸音 250ms）。此前在 EDT（游戏循环线程）
     * 同步调用，导致每次击杀敌机时画面冻结一帧——表现为"打中就卡顿"。改用线程池提交，
     * 让音频阻塞发生在后台线程，EDT 立即返回继续渲染。
     */
    private void playBuffer(byte[] buffer) {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) return;
        pool.submit(() -> {
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(format, buffer.length);
                line.start();
                line.write(buffer, 0, buffer.length);
                line.drain();
            } catch (LineUnavailableException ignored) {
            }
        });
    }

    /** 背景音乐：循环 4 小节简单旋律。 */
    private void musicLoop() {
        // C 大调简单旋律频率（Hz）
        double[] melody = {262, 330, 392, 523, 392, 330, 262, 330};
        double noteDur = 0.28;
        while (running) {
            if (muted || !musicOn) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                continue;
            }
            for (double freq : melody) {
                if (!running) break;
                if (!muted && musicOn) {
                    playTone(freq, noteDur, 0.08, WaveType.SINE, false);
                }
                try { Thread.sleep((long) (noteDur * 1000 * 0.9)); } catch (InterruptedException ignored) {}
            }
        }
    }
}
