package com.webchat.confing;

import com.webchat.service.PresenceService;
import com.webchat.service.RoomService;
import com.webchat.service.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

/**
 * 监听 STOMP 会话断开：清理在线状态并通知好友下线。
 *
 * 互踢场景下需谨慎：设备2 登录会覆盖 SessionManager 中的 loginTime 并踢掉设备1。
 * 设备1 断开时不能误删在线记录（设备2 仍在线）。通过比对"该连接握手时携带的
 * loginTime"与"当前最新 loginTime"判定：只有相同时，才是最新会话断开，才清除并通知。
 */
@Component
public class WebSocketEventListener {

    @Autowired
    private SessionManager sessionManager;
    @Autowired
    private PresenceService presenceService;
    @Autowired
    private RoomService roomService;

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = accessor.getUser();
        if (principal == null) return;
        Long userId;
        try { userId = Long.valueOf(principal.getName()); } catch (Exception e) { return; }

        Map<String, Object> attrs = accessor.getSessionAttributes();
        long connLoginTime = 0;
        if (attrs != null && attrs.get("loginTime") != null) {
            try { connLoginTime = Long.parseLong(attrs.get("loginTime").toString()); } catch (Exception ignored) {}
        }

        Long latest = sessionManager.latestLoginTimeOf(userId);
        if (latest == null) return; // 已不在在线集合，无需重复处理
        if (connLoginTime != 0 && connLoginTime != latest) {
            // 旧会话断开（已被新登录挤掉），新会话仍在线，不清除
            return;
        }

        sessionManager.logout(userId);
        presenceService.notifyOffline(userId);
        // 对战房间清理：最新会话断开时触发弃赛/离开（旧会话不触发，避免误结算）
        roomService.notifyDisconnect(userId);
    }
}
