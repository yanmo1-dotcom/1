package com.webchat.model;

public class FriendRequest {
    private Long id;
    private Long fromId;        // 申请发起者
    private Long toId;          // 申请接收者
    private String status;      // PENDING / ACCEPTED / REJECTED
    private long createdAt;     // 创建时间戳
    private long handledAt;     // 处理时间戳

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFromId() { return fromId; }
    public void setFromId(Long fromId) { this.fromId = fromId; }

    public Long getToId() { return toId; }
    public void setToId(Long toId) { this.toId = toId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getHandledAt() { return handledAt; }
    public void setHandledAt(long handledAt) { this.handledAt = handledAt; }
}
