package com.webchat.service;

import com.webchat.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 好友上下线状态推送：向某用户的所有在线好友推送 presence 通知。
 */
@Service
public class PresenceService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private UserService userService;

    @Autowired
    private SessionManager sessionManager;

    /** 用户上线：通知其所有在线好友 */
    public void notifyOnline(Long userId) {
        User u = userService.findById(userId);
        if (u == null) return;
        broadcastPresence(userId, u.getUsername(), true);
    }

    /** 用户下线：通知其所有在线好友 */
    public void notifyOffline(Long userId) {
        User u = userService.findById(userId);
        if (u == null) return;
        broadcastPresence(userId, u.getUsername(), false);
    }

    private void broadcastPresence(Long userId, String username, boolean online) {
        User u = userService.findById(userId);
        if (u == null || u.getFriends() == null) return;
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("username", username);
        payload.put("online", online);
        for (Long friendId : u.getFriends()) {
            // 只推送给当前在线的好友
            if (sessionManager.isOnline(friendId)) {
                messagingTemplate.convertAndSendToUser(String.valueOf(friendId), "/queue/presence", payload);
            }
        }
    }
}
