package com.webchat.controller;

import com.webchat.service.RoomService;
import com.webchat.service.SessionManager;
import com.webchat.service.engine.ActionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

/**
 * 对战 WebSocket 消息处理。通用 /app/game.action，按 actionType 分发动作负载。
 * 会话新鲜度校验与 ChatController 一致：旧会话被踢。
 * 防作弊：客户端只发动作（出拳/猜数/落子），胜负由服务端引擎裁决。
 */
@Controller
public class GameStompController {

    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private SessionManager sessionManager;
    @Autowired private RoomService roomService;

    @MessageMapping("/game.action")
    public void handleAction(Map<String, Object> message, Principal principal) {
        Long uid = principal != null ? parseLong(principal.getName()) : null;
        long msgLoginTime = message.containsKey("loginTime") ? parseLong(message.get("loginTime").toString()) : 0;

        if (uid == null || !sessionManager.isLatestLogin(uid, msgLoginTime)) {
            if (uid != null) {
                messagingTemplate.convertAndSendToUser(String.valueOf(uid), "/queue/kickoff", "kicked");
            }
            return;
        }

        String roomId = (String) message.get("roomId");
        String actionType = message.get("actionType") == null ? "" : message.get("actionType").toString();
        Object payload = message.get("payload");
        if (roomId == null) return;

        Object action = parseAction(actionType, payload);
        ActionResult res = roomService.applyAction(roomId, uid, action);
        if (!res.success) {
            Map<String, Object> err = new HashMap<>();
            err.put("roomId", roomId);
            err.put("ok", false);
            err.put("msg", res.message);
            messagingTemplate.convertAndSendToUser(String.valueOf(uid), "/queue/game-error", err);
        }
    }

    /** 按动作类型解析负载为引擎可用的形式 */
    private Object parseAction(String actionType, Object payload) {
        try {
            switch (actionType) {
                case "RPS_MOVE":
                    // payload 为字符串 ROCK/PAPER/SCISSORS
                    return payload == null ? null : payload.toString();
                case "GUESS":
                    // payload 为数字
                    return payload == null ? null : Integer.parseInt(payload.toString());
                case "TICTACTOE_MOVE":
                    // payload 为 [row, col]
                    if (payload instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) payload;
                        return new int[]{ Integer.parseInt(list.get(0).toString()), Integer.parseInt(list.get(1).toString()) };
                    }
                    return null;
                default:
                    return payload;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Long parseLong(String s) {
        try { return Long.valueOf(s); } catch (Exception e) { return null; }
    }
}
