package com.webchat.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class AdminAuditService {
    private final JdbcTemplate jdbc;
    public AdminAuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void record(String action, String target, String detail) {
        jdbc.update("INSERT INTO admin_logs(action,target,detail_text,created_at) VALUES(?,?,?,?)", action,target,detail,System.currentTimeMillis());
    }
    public List<Map<String,Object>> recent() {
        return jdbc.queryForList("SELECT id,action,target,detail_text,created_at FROM admin_logs ORDER BY id DESC LIMIT 100");
    }
}
