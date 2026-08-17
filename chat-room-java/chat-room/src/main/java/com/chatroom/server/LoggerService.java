package com.chatroom.server;

import com.chatroom.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class LoggerService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String logDir = "logs/"; // 日志存放在项目根目录的 logs 文件夹

    public LoggerService() {
        File dir = new File(logDir);
        if (!dir.exists()) dir.mkdirs();
    }

    // Bug4: synchronized 防止多线程并发写同一文件时内容交错损坏
    public synchronized void log(Message msg, String clientIp) {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String fileName = logDir + "chat_log_" + dateStr + ".json";

        Map<String, Object> auditLog = new HashMap<>();
        auditLog.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        auditLog.put("type", msg.getType());
        auditLog.put("sender", msg.getSender());
        auditLog.put("target", msg.getTarget());
        auditLog.put("content", msg.getContent());
        auditLog.put("client_ip", clientIp);

        // Bug4: try-with-resources 确保任何异常下 FileWriter 都能被关闭
        try (Writer fw = new OutputStreamWriter(new FileOutputStream(fileName, true), StandardCharsets.UTF_8)) {
            fw.write(mapper.writeValueAsString(auditLog) + "\n");
        } catch (IOException e) {
            System.err.println("❌ [审计] 日志写入失败: " + e.getMessage());
        }
    }
}