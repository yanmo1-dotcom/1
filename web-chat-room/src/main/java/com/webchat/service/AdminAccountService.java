package com.webchat.service;

import com.webchat.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminAccountService {
    private final JdbcTemplate jdbc;
    private final UserService users;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private static final long SESSION_MS = 8 * 60 * 60 * 1000L;

    public AdminAccountService(JdbcTemplate jdbc, UserService users) {
        this.jdbc = jdbc; this.users = users;
    }

    public String register(String username, String password, String inviteCode, String expectedInviteCode) {
        if (username == null || password == null || username.isBlank() || password.length() < 6)
            return "账号不存在、密码错误或密码少于 6 位";
        if (inviteCode == null || !inviteCode.equals(expectedInviteCode)) return "邀请码错误";
        User user = users.verifyCredentials(username.trim(), password);
        if (user == null) return "已有账号或密码错误";
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM admin_accounts WHERE user_id=? OR username=?", Integer.class, user.getId(), user.getUsername());
        if (count != null && count > 0) return "该账号已经注册为管理员";
        jdbc.update("INSERT INTO admin_accounts(user_id,username,password_hash,created_at,last_login_at) VALUES(?,?,?,?,?)",
                user.getId(), user.getUsername(), hash(password), System.currentTimeMillis(), 0);
        return null;
    }

    public String login(String username, String password) {
        if (username == null || password == null) return null;
        var hashes = jdbc.query("SELECT password_hash FROM admin_accounts WHERE username=?",
                (rs, row) -> rs.getString(1), username.trim());
        if (hashes.isEmpty() || !verify(password, hashes.get(0))) return null;
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new Session(username.trim(), System.currentTimeMillis() + SESSION_MS));
        jdbc.update("UPDATE admin_accounts SET last_login_at=? WHERE username=?", System.currentTimeMillis(), username.trim());
        return token;
    }

    public String authenticate(String token) {
        Session session = token == null ? null : sessions.get(token);
        if (session == null || session.expiresAt < System.currentTimeMillis()) { if (token != null) sessions.remove(token); return null; }
        return session.username;
    }

    public void logout(String token) { if (token != null) sessions.remove(token); }

    private String hash(String password) {
        byte[] salt = new byte[16]; random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(pbkdf2(password, salt));
    }
    private boolean verify(String password, String stored) {
        try { String[] p=stored.split(":",2); byte[] salt=Base64.getDecoder().decode(p[0]); return java.security.MessageDigest.isEqual(pbkdf2(password,salt),Base64.getDecoder().decode(p[1])); }
        catch (Exception e) { return false; }
    }
    private byte[] pbkdf2(String password, byte[] salt) {
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(new PBEKeySpec(password.toCharArray(),salt,120000,256)).getEncoded(); }
        catch (Exception e) { throw new IllegalStateException("Cannot hash password", e); }
    }
    private record Session(String username, long expiresAt) {}
}
