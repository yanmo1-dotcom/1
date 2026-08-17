package com.kapai.ui.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.kapai.ui.KapaiGame;

/**
 * LibGDX 桌面启动器（lwjgl3 后端）。
 *
 * 运行方式：
 *   mvn -DskipTests install
 *   mvn -pl kapai-ui exec:java
 * 或直接运行本类的 main 方法。
 *
 * 设计思路：启动器只负责创建窗口与渲染后端，不包含任何游戏逻辑；
 * 游戏逻辑全部在 {@link KapaiGame} 中，便于移植到其他后端（如 Web 的 TeaVM）。
 */
public final class Lwjgl3Launcher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Kapai — Roguelike Card Game");
        config.setWindowedMode(1024, 640);
        config.setForegroundFPS(60);
        config.setIdleFPS(30);
        config.useVsync(true);
        // 注：lwjgl 3.3+ 已自带 native，无需手动设置
        new Lwjgl3Application(new KapaiGame(), config);
    }

    private Lwjgl3Launcher() {
    }
}
