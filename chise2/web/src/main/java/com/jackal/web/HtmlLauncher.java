package com.jackal.web;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.gwt.GwtApplication;
import com.badlogic.gdx.backends.gwt.GwtApplicationConfiguration;
import com.jackal.core.JackalGame;

/**
 * HtmlLauncher —— 网页平台启动器（GWT 后端）。
 * <p>
 * 对应 desktop 的 DesktopLauncher，用 LibGDX 的 {@link GwtApplication}
 * 把 {@link JackalGame} 编译为 JavaScript 跑在浏览器里。
 * <p>
 * GWT 后端用 WebGL 渲染（对应桌面 OpenGL），输入用浏览器 DOM 事件。
 * 资源（assets/）通过 GWT preloader 打包进 war/ 目录，运行时异步加载。
 *
 * @author Jackal Dev Team
 */
public class HtmlLauncher extends GwtApplication {

    /** 设计分辨率，与桌面一致 */
    public static final int WIDTH = JackalGame.VIRTUAL_WIDTH;
    public static final int HEIGHT = JackalGame.VIRTUAL_HEIGHT;

    @Override
    public GwtApplicationConfiguration getConfig() {
        // GwtApplicationConfiguration 构造时固定宽高（canvas 尺寸）
        GwtApplicationConfiguration cfg = new GwtApplicationConfiguration(WIDTH, HEIGHT);
        // 禁用音频可提升兼容性，但本项目有 BGM/SFX，保留音频
        cfg.disableAudio = false;
        // 用 GL30 跟桌面一致
        cfg.useGL30 = true;
        return cfg;
    }

    @Override
    public ApplicationListener createApplicationListener() {
        return new JackalGame();
    }
}
