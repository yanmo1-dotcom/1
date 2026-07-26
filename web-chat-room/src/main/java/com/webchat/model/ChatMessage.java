package com.webchat.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatMessage {
    private Long id;              // 消息ID（持久化用）
    private Long senderId;        // 发送者用户ID
    private Long receiverId;      // 私聊接收者用户ID
    private String groupId;       // 群聊组ID
    private String senderName;    // 发送者昵称（群聊/大厅展示用）
    private String content;       // 消息内容
    private String type;          // private / group / lobby
    private long timestamp;       // 毫秒时间戳
    private String time;          // 展示用 HH:mm:ss

    public ChatMessage() {
        this.timestamp = System.currentTimeMillis();
        this.time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public Long getReceiverId() { return receiverId; }
    public void setReceiverId(Long receiverId) { this.receiverId = receiverId; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getTime() { return time; }
    public void setTime(String time) { this.time = time; }
}
