package com.webchat.controller;

import com.webchat.model.ChatMessage;
import com.webchat.service.MessageService;
import com.webchat.service.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private MessageService messageService;

    private static final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();

    /**
     * 私聊处理
     */
    @MessageMapping("/chat.private")
    public void handlePrivateMessage(Map<String, Object> message) {
        System.out.println("🔍 [私聊] 收到消息 Keys: " + message.keySet());

        Long senderId = message.containsKey("senderId") ? Long.valueOf(message.get("senderId").toString()) : null;
        long msgLoginTime = message.containsKey("loginTime") ? Long.valueOf(message.get("loginTime").toString()) : 0;

        if (senderId == null || !sessionManager.isLatestLogin(senderId, msgLoginTime)) {
            System.out.println("⚠️ [私聊] 拦截：用户 " + senderId + " 会话无效或已被挤下线 (客户端时间戳:" + msgLoginTime + ")");
            if (senderId != null) {
                messagingTemplate.convertAndSendToUser(String.valueOf(senderId), "/queue/kickoff", "kicked");
            }
            return;
        }
        System.out.println("✅ [私聊] 校验通过，允许发送");

        // ... 原有转发逻辑保持不变 ...
        Object receiverIdObj = message.get("receiverId");
        String content = (String) message.get("content");
        if (receiverIdObj == null || content == null) return;

        Long receiverId = Long.valueOf(receiverIdObj.toString());

        // 持久化私聊消息
        ChatMessage record = new ChatMessage();
        record.setType("private");
        record.setSenderId(senderId);
        record.setReceiverId(receiverId);
        record.setContent(content);
        messageService.save(record);

        Map<String, Object> outMessage = new HashMap<>();
        outMessage.put("content", content);
        outMessage.put("senderId", message.get("senderId"));
        outMessage.put("type", "private");
        outMessage.put("timestamp", System.currentTimeMillis());

        String targetPath = "/topic/user/" + receiverIdObj.toString() + "/reply";
        messagingTemplate.convertAndSend(targetPath, outMessage);
    }

    /**
     * 群聊处理
     */
    @MessageMapping("/chat.group")
    public void handleGroupMessage(Map<String, Object> message) {
        System.out.println(" [群聊] 收到消息 Keys: " + message.keySet());

        Long senderId = message.containsKey("senderId") ? Long.valueOf(message.get("senderId").toString()) : null;
        long msgLoginTime = message.containsKey("loginTime") ? Long.valueOf(message.get("loginTime").toString()) : 0;

        if (senderId == null || !sessionManager.isLatestLogin(senderId, msgLoginTime)) {
            System.out.println("⚠️ [群聊] 拦截：用户 " + senderId + " 会话无效或已被挤下线 (客户端时间戳:" + msgLoginTime + ")");
            if (senderId != null) {
                messagingTemplate.convertAndSendToUser(String.valueOf(senderId), "/queue/kickoff", "kicked");
            }
            return;
        }

        // ... 原有群聊逻辑 ...
        String groupId = (String) message.get("groupId");
        String content = (String) message.get("content");
        String senderName = (String) message.get("senderName");
        if (groupId == null || content == null) return;

        // 持久化群聊消息
        ChatMessage record = new ChatMessage();
        record.setType("group");
        record.setGroupId(groupId);
        record.setSenderId(senderId);
        record.setSenderName(senderName);
        record.setContent(content);
        messageService.save(record);

        Map<String, Object> outMessage = new HashMap<>();
        outMessage.put("content", content);
        outMessage.put("senderName", senderName);
        outMessage.put("type", "group");
        outMessage.put("timestamp", System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/group/" + groupId, outMessage);
    }

    @MessageMapping("/lobby.send")
    public void sendToLobby(Map<String, Object> message) {
        System.out.println("🔍 [大厅] 收到消息 Keys: " + message.keySet());

        Long userId = message.containsKey("userId") ? Long.valueOf(message.get("userId").toString()) : null;
        long msgLoginTime = message.containsKey("loginTime") ? Long.valueOf(message.get("loginTime").toString()) : 0;

        if (userId == null || !sessionManager.isLatestLogin(userId, msgLoginTime)) {
            System.out.println("⚠️ [大厅] 拦截：用户 " + userId + " 会话无效或已被挤下线 (客户端时间戳:" + msgLoginTime + ")");
            if (userId != null) {
                messagingTemplate.convertAndSendToUser(String.valueOf(userId), "/queue/kickoff", "kicked");
            }
            return;
        }

        // ... 原有大厅逻辑 ...
        String username = (String) message.get("username");
        String content = (String) message.get("content");
        if (username == null || content == null) return;
        onlineUsers.add(username);

        // 仅持久化非空内容（上线通知 content 为空，不存）
        if (!content.isEmpty()) {
            ChatMessage record = new ChatMessage();
            record.setType("lobby");
            record.setSenderId(userId);
            record.setSenderName(username);
            record.setContent(content);
            messageService.save(record);
        }

        Map<String, Object> outMessage = new HashMap<>();
        outMessage.put("senderName", username);
        outMessage.put("content", content);
        outMessage.put("timestamp", System.currentTimeMillis());
        messagingTemplate.convertAndSend("/topic/lobby/messages", outMessage);
        broadcastOnlineUsers();
    }

    @MessageMapping("/lobby.leave")
    public void leaveLobby(Map<String, Object> message) {
        String username = (String) message.get("username");
        if (username != null) {
            onlineUsers.remove(username);
            broadcastOnlineUsers();
        }
    }

    private void broadcastOnlineUsers() {
        List<String> userList = new ArrayList<>(onlineUsers);
        messagingTemplate.convertAndSend("/topic/lobby/users", userList);
    }
}