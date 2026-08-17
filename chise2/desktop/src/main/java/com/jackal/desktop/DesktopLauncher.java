package com.jackal.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.jackal.core.JackalGame;

/**
 * DesktopLauncher —— 桌面平台启动器（Lwjgl3 后端）。
 * <p>
 * 职责单一：配置窗口参数，创建并启动 {@link JackalGame} 实例。
 * 不应在此放置任何游戏逻辑——所有逻辑归属 :core 模块。
 *
 * @author Jackal Dev Team
 */
public class DesktopLauncher {

    public static void main(String[] args) {
        // 强制标准输出/错误用 UTF-8，修复 Windows 下中文日志乱码
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
            System.setErr(new java.io.PrintStream(System.err, true, "UTF-8"));
        } catch (Exception ignored) {
        }

        // 1. 创建 Lwjgl3 配置对象（1.12.x 推荐写法，取代旧 LwjglApplicationConfiguration）
        Lwjgl3ApplicationConfiguration config =
                new Lwjgl3ApplicationConfiguration();

        // 2. 窗口标题
        config.setTitle("赤色要塞 Jackal — 复刻版");

        // 3. 窗口初始尺寸（2x 设计分辨率，清晰且不过大）
        //    设置 useVsync(true) 开启垂直同步，帧率上限匹配显示器刷新率
        config.setWindowedMode(JackalGame.VIRTUAL_WIDTH * 2,
                               JackalGame.VIRTUAL_HEIGHT * 2);
        config.useVsync(true);

        // 4. 启用 OpenGL 3.2（Lwjgl3 默认要求，LibGDX 1.12.x 现代后端基准）
        //    显式设置可避免部分集成显卡回退到旧版本导致渲染异常
        config.setOpenGLEmulation(
                Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);

        // 5. 禁止自动退出（ESC 默认会关闭窗口，开发期保留以便调试）
        //    Lwjgl3 中通过 setForegroundFPS 限制前台 FPS；0 表示不限制（依赖 vsync）
        config.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate);

        // 6. 启动应用——传入核心 Game 实例
        //    若配置中出现错误，此处会抛出 Lwjgl3ApplicationException
        new Lwjgl3Application(new JackalGame(), config);
    }
}
