package com.webchat.controller;

import com.webchat.TokenManager;
import com.webchat.model.User;
import com.webchat.service.MessageService;
import com.webchat.service.PresenceService;
import com.webchat.service.SessionManager;
import com.webchat.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AuthController {
    @Autowired
    private UserService userService;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MessageService messageService;

    @Autowired
    private PresenceService presenceService;

    // 【核心修改】从外部配置文件读取管理员密码
    // 如果读取失败，默认使用 "default_admin_pass" 防止报错
    @Value("${admin.password:default_admin_pass}")
    private String adminPassword; 

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody User user) {
        Map<String, Object> r = new HashMap<>();
        if (userService.register(user)) {
            r.put("code", 200); r.put("msg", "注册成功");
        } else {
            r.put("code", 400); r.put("msg", "用户名已存在");
        }
        return r;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody User form, HttpServletResponse response) {
        Map<String, Object> r = new HashMap<>();
        User u = userService.login(form.getUsername(), form.getPassword());
        
        if (u != null) {
            String token = TokenManager.createToken(u.getId());

            // 【关键修复】用 SessionManager 返回的同一个时间戳，保证前后端校验一致
            long loginTime = sessionManager.login(u.getId());

            // 【关键修复】主动踢掉该账号已有的旧会话：向其当前 WS 连接发送踢人消息，
            // 携带本次新登录的时间戳，前端比对后只下线旧会话，不误伤刚登录的新会话。
            // 依赖握手阶段把 userId 设为 Principal，convertAndSendToUser 才能精确路由。
            Map<String, Object> kick = new HashMap<>();
            kick.put("reason", "kick");
            kick.put("loginTime", loginTime);
            messagingTemplate.convertAndSendToUser(String.valueOf(u.getId()), "/queue/kickoff", kick);

            r.put("code", 200);
            r.put("msg", "登录成功");
            r.put("token", token);
            r.put("user", u);
            // 【关键】把当前登录的时间戳返回给前端
            r.put("loginTime", loginTime);

            // 通知该用户的所有在线好友：他上线了
            presenceService.notifyOnline(u.getId());
        } else {
            r.put("code", 401); r.put("msg", "账号或密码错误");
        }
        return r;
    }

    @GetMapping("/friends")
    public Map<String, Object> getFriends(@RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        r.put("code", 200); r.put("data", userService.getMyFriends(uid));
        return r;
    }

    /** 返回好友中当前在线的用户ID列表 */
    @GetMapping("/friends/online")
    public Map<String, Object> getOnlineFriends(@RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        List<Long> online = new ArrayList<>();
        for (User f : userService.getMyFriends(uid)) {
            if (sessionManager.isOnline(f.getId())) online.add(f.getId());
        }
        r.put("code", 200); r.put("data", online);
        return r;
    }

    // ================= 好友申请流程 =================

    /** 发起好友申请（替代旧的直接加好友） */
    @PostMapping("/friend/request")
    public Map<String, Object> sendFriendRequest(@RequestParam String toUsername, @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }

        User target = userService.findByUsername(toUsername);
        if (target == null) { r.put("code", 404); r.put("msg", "用户不存在"); return r; }
        if (target.getId().equals(uid)) { r.put("code", 400); r.put("msg", "不能添加自己"); return r; }

        int result = userService.createFriendRequest(uid, target.getId());
        if (result == 1) {
            r.put("code", 200); r.put("msg", "申请已发送，等待对方同意");
            // 实时推送给对方
            Map<String, Object> notice = new HashMap<>();
            notice.put("fromId", uid);
            User me = userService.findById(uid);
            notice.put("fromName", me != null ? me.getUsername() : "");
            messagingTemplate.convertAndSendToUser(String.valueOf(target.getId()), "/queue/friend-request", notice);
        } else if (result == 0) {
            r.put("code", 400); r.put("msg", "已是好友");
        } else if (result == -1) {
            r.put("code", 400); r.put("msg", "已发送过申请，等待对方同意");
        } else {
            r.put("code", 400); r.put("msg", "操作失败");
        }
        return r;
    }

    /** 处理好友申请：同意/拒绝 */
    @PostMapping("/friend/handle")
    public Map<String, Object> handleFriendRequest(@RequestBody Map<String, Object> params, @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }

        Long requestId = Long.valueOf(params.get("requestId").toString());
        boolean accept = "accept".equals(params.get("action"));

        com.webchat.model.FriendRequest req = userService.handleFriendRequest(requestId, uid, accept);
        if (req == null) {
            r.put("code", 400); r.put("msg", "申请不存在或无权处理");
            return r;
        }
        r.put("code", 200);
        r.put("msg", accept ? "已同意" : "已拒绝");

        // 推送结果给申请发起方
        Map<String, Object> result = new HashMap<>();
        result.put("requestId", req.getId());
        result.put("action", accept ? "accept" : "reject");
        result.put("fromId", req.getFromId());
        result.put("toId", req.getToId());
        messagingTemplate.convertAndSendToUser(String.valueOf(req.getFromId()), "/queue/friend-result", result);
        return r;
    }

    /** 查询我收到的待处理申请 */
    @GetMapping("/friend/requests")
    public Map<String, Object> getFriendRequests(@RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }

        // 附带发起方用户名，便于前端展示
        List<Map<String, Object>> data = new ArrayList<>();
        for (com.webchat.model.FriendRequest req : userService.getIncomingPending(uid)) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", req.getId());
            item.put("fromId", req.getFromId());
            User from = userService.findById(req.getFromId());
            item.put("fromName", from != null ? from.getUsername() : "");
            item.put("createdAt", req.getCreatedAt());
            data.add(item);
        }
        r.put("code", 200);
        r.put("data", data);
        return r;
    }

    // ================= 聊天记录历史查询 =================

    @GetMapping("/messages/private")
    public Map<String, Object> getPrivateHistory(@RequestParam Long friendId, @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        r.put("code", 200);
        r.put("data", messageService.getPrivateHistory(uid, friendId));
        return r;
    }

    @GetMapping("/messages/group")
    public Map<String, Object> getGroupHistory(@RequestParam String groupId, @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        r.put("code", 200);
        r.put("data", messageService.getGroupHistory(groupId));
        return r;
    }

    @GetMapping("/messages/lobby")
    public Map<String, Object> getLobbyHistory(@RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        r.put("code", 200);
        r.put("data", messageService.getLobbyHistory(200));
        return r;
    }

    @PostMapping("/join-group")
    public Map<String, Object> joinGroup(@RequestParam String groupName, @RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        
        if (groupName == null || groupName.trim().isEmpty()) {
            r.put("code", 400); r.put("msg", "群名不能为空"); return r;
        }

        if (userService.joinOrCreateGroup(groupName.trim(), uid)) {
            r.put("code", 200); r.put("msg", "加入/创建成功");
        } else {
            r.put("code", 500); r.put("msg", "操作失败");
        }
        return r;
    }

    @GetMapping("/my-groups")
    public Map<String, Object> getMyGroups(@RequestHeader("Authorization") String auth) {
        Map<String, Object> r = new HashMap<>();
        Long uid = TokenManager.verifyToken(auth);
        if (uid == null) { r.put("code", 401); return r; }
        r.put("code", 200); r.put("data", userService.getMyGroups(uid));
        return r;
    }

    /**
     * 管理员重置密码接口
     */
    @PostMapping("/admin/reset-password")
    public Map<String, Object> resetPassword(@RequestBody Map<String, String> params) {
        Map<String, Object> r = new HashMap<>();
        
        String username = params.get("username");
        String newPassword = params.get("newPassword");
        String inputKey = params.get("adminKey");

        // 【核心修改】使用从配置文件读取的密码进行校验
        if (inputKey == null || !inputKey.equals(adminPassword)) {
            r.put("code", 403);
            r.put("msg", "管理员密码错误，无权操作！");
            return r;
        }

        if (username == null || newPassword == null) {
            r.put("code", 400);
            r.put("msg", "参数缺失");
            return r;
        }

        boolean success = userService.adminResetPassword(username, newPassword);
        
        if (success) {
            r.put("code", 200);
            r.put("msg", "密码重置成功");
        } else {
            r.put("code", 404);
            r.put("msg", "用户不存在");
        }
        return r;
    }
}