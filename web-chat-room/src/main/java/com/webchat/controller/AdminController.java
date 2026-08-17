package com.webchat.controller;

import com.webchat.model.MatchRecord;
import com.webchat.model.RankRule;
import com.webchat.model.User;
import com.webchat.service.MatchService;
import com.webchat.service.RankRuleService;
import com.webchat.service.SessionManager;
import com.webchat.service.UserService;
import com.webchat.service.AdminAuditService;
import com.webchat.service.AdminAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserService users;
    private final MatchService matches;
    private final RankRuleService rules;
    private final SessionManager sessions;
    private final AdminAuditService audit;
    private final AdminAccountService adminAccounts;

    @Value("${admin.password:default_admin_pass}")
    private String adminPassword;

    public AdminController(UserService users, MatchService matches, RankRuleService rules, SessionManager sessions,
                           AdminAuditService audit, AdminAccountService adminAccounts) {
        this.users = users; this.matches = matches; this.rules = rules; this.sessions = sessions;
        this.audit = audit; this.adminAccounts = adminAccounts;
    }

    private boolean authorized(String token) {
        return adminAccounts.authenticate(token) != null;
    }

    private ResponseEntity<?> forbidden() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", 401, "msg", "管理员密码错误"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String token = adminAccounts.login(body.get("username"), body.get("password"));
        if (token == null) return forbidden();
        return ResponseEntity.ok(Map.of("code", 200, "msg", "登录成功", "token", token));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String error = adminAccounts.register(body.get("username"), body.get("password"), body.get("inviteCode"), adminPassword);
        if (error != null) return ResponseEntity.badRequest().body(Map.of("code", 400, "msg", error));
        audit.record("REGISTER_ADMIN", body.get("username"), "普通账号通过邀请码注册为管理员");
        return ResponseEntity.ok(Map.of("code", 200, "msg", "管理员注册成功，请登录"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value="X-Admin-Token", required=false) String token) {
        adminAccounts.logout(token);
        return ResponseEntity.ok(Map.of("code", 200));
    }

    @GetMapping("/users")
    public ResponseEntity<?> userList(@RequestHeader(value="X-Admin-Token", required=false) String password) {
        if (!authorized(password)) return forbidden();
        return ResponseEntity.ok(Map.of("code", 200, "data", users.getAllUsers().stream().map(this::safeUser).toList()));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody Map<String, Object> body,
                                         @RequestHeader(value="X-Admin-Token", required=false) String password) {
        if (!authorized(password)) return forbidden();
        if (users.findById(id) == null) return ResponseEntity.notFound().build();
        if (body.containsKey("rankPoints")) users.setRankPoints(id, Integer.parseInt(body.get("rankPoints").toString()));
        if (body.containsKey("status")) {
            String status = body.get("status").toString();
            if (!List.of("ACTIVE", "MUTED", "BANNED").contains(status))
                return ResponseEntity.badRequest().body(Map.of("code", 400, "msg", "无效状态"));
            users.setStatus(id, status);
        }
        audit.record("UPDATE_USER", String.valueOf(id), body.toString());
        return ResponseEntity.ok(Map.of("code", 200, "data", safeUser(users.findById(id))));
    }

    @PostMapping("/users/{id}/reset-password")
    public ResponseEntity<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body,
                                            @RequestHeader(value="X-Admin-Token", required=false) String password) {
        if (!authorized(password)) return forbidden();
        User user = users.findById(id);
        String newPassword = body.get("newPassword");
        if (user == null) return ResponseEntity.notFound().build();
        if (newPassword == null || newPassword.length() < 6)
            return ResponseEntity.badRequest().body(Map.of("code", 400, "msg", "新密码至少 6 位"));
        users.adminResetPassword(user.getUsername(), newPassword);
        audit.record("RESET_PASSWORD", user.getUsername(), "管理员重置用户密码");
        return ResponseEntity.ok(Map.of("code", 200, "msg", "密码已重置"));
    }

    @GetMapping("/logs")
    public ResponseEntity<?> logs(@RequestHeader(value="X-Admin-Token", required=false) String password) {
        if (!authorized(password)) return forbidden();
        return ResponseEntity.ok(Map.of("code", 200, "data", audit.recent()));
    }

    @GetMapping("/matches")
    public ResponseEntity<?> matchList(@RequestHeader(value="X-Admin-Token", required=false) String password) {
        if (!authorized(password)) return forbidden();
        List<MatchRecord> all = matches.getAll();
        return ResponseEntity.ok(Map.of("code", 200, "data", all.subList(0, Math.min(100, all.size()))));
    }

    @GetMapping("/rules")
    public ResponseEntity<?> getRules(@RequestHeader(value="X-Admin-Token", required=false) String password) {
        if (!authorized(password)) return forbidden();
        return ResponseEntity.ok(Map.of("code", 200, "data", rules.getRules()));
    }

    @PutMapping("/rules")
    public ResponseEntity<?> updateRules(@RequestBody RankRule rule,
                                          @RequestHeader(value="X-Admin-Token", required=false) String password) {
        if (!authorized(password)) return forbidden();
        rules.updateRules(rule);
        audit.record("UPDATE_RULES", "rank-rules", rule.toString());
        return ResponseEntity.ok(Map.of("code", 200, "data", rule));
    }

    @GetMapping("/metrics")
    public ResponseEntity<?> metrics(@RequestHeader(value="X-Admin-Token", required=false) String password) {
        if (!authorized(password)) return forbidden();
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("onlineUsers", sessions.getOnlineUserIds().size());
        data.put("totalUsers", users.getAllUsers().size());
        data.put("totalMatches", matches.getAll().size());
        data.put("usedMemoryMb", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        data.put("maxMemoryMb", runtime.maxMemory() / 1024 / 1024);
        data.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        data.put("processors", runtime.availableProcessors());
        data.put("storage", "Relational Database (H2 / MySQL)");
        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }

    private Map<String, Object> safeUser(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getId()); result.put("username", user.getUsername());
        result.put("nickname", user.getNickname()); result.put("rankPoints", user.getRankPoints());
        result.put("wins", user.getWins()); result.put("losses", user.getLosses()); result.put("draws", user.getDraws());
        result.put("status", user.getStatus()); result.put("online", sessions.isOnline(user.getId()));
        result.put("lastLoginAt", user.getLastLoginAt());
        result.put("role", "user");
        result.put("onlineState", sessions.isOnline(user.getId()) ? "online" : "offline");
        return result;
    }
}
