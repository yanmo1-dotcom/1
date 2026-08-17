package com.webchat.service;

import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionManager {
    // 存储结构：UserId -> 登录时的毫秒级时间戳
    // 该 map 同时充当"在线用户集合"：key 存在即在线，移除即下线
    private final ConcurrentHashMap<Long, Long> latestLoginTime = new ConcurrentHashMap<>();

    /**
     * 记录登录时间（每次 HTTP 登录成功后调用）
     * @return 本次登录的时间戳，必须原样返回给前端，用于后续互踢比对
     */
    public long login(Long userId) {
        long now = System.currentTimeMillis();
        latestLoginTime.put(userId, now);
        System.out.println("✅ 用户 " + userId + " 登录，时间戳: " + now);
        return now;
    }

    /**
     * 检查当前操作者是否是"最新登录"的
     * @param userId 用户ID
     * @param clientTimestamp 客户端携带的登录时间戳
     * @return true=是最新的(允许操作), false=是旧的(被挤掉了)
     */
    public boolean isLatestLogin(Long userId, long clientTimestamp) {
        Long serverTimestamp = latestLoginTime.get(userId);
        // 如果服务端没有记录，或者客户端时间戳不等于服务端最新时间戳，说明被挤了
        return serverTimestamp != null && serverTimestamp == clientTimestamp;
    }

    /** 判断用户是否在线 */
    public boolean isOnline(Long userId) {
        return latestLoginTime.containsKey(userId);
    }

    /** 返回用户当前最新登录时间戳，不存在返回 null（供断开监听比对用） */
    public Long latestLoginTimeOf(Long userId) {
        return latestLoginTime.get(userId);
    }

    /** 当前所有在线用户ID */
    public Set<Long> getOnlineUserIds() {
        return latestLoginTime.keySet();
    }

    /**
     * 退出时清除记录
     */
    public void logout(Long userId) {
        latestLoginTime.remove(userId);
    }
}
