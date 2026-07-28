package com.webchat.service.engine;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏引擎注册表：按 gameType 路由到对应引擎。
 * Spring 注入所有 GameEngine 实现，按 supports(gameType) 匹配。
 */
@Component
public class GameEngineRegistry {

    private final Map<String, GameEngine> engineMap = new HashMap<>();

    public GameEngineRegistry(List<GameEngine> engines) {
        for (GameEngine e : engines) {
            // 用每个引擎的 gameType() 作主键
            engineMap.put(e.gameType(), e);
        }
        System.out.println("✅ 已注册游戏引擎: " + engineMap.keySet());
    }

    /** 按 gameType 找到支持它的引擎（用 supports 匹配，处理 RPS 一个引擎两种类型） */
    public GameEngine get(String gameType) {
        GameEngine direct = engineMap.get(gameType);
        if (direct != null && direct.supports(gameType)) return direct;
        for (GameEngine e : engineMap.values()) {
            if (e.supports(gameType)) return e;
        }
        return null;
    }

    public boolean exists(String gameType) { return get(gameType) != null; }
}
