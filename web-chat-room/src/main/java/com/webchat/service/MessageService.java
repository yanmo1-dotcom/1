package com.webchat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.model.ChatMessage;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageService {

    @Value("${webchat.data-dir:/opt/webchat-data/users/}")
    private String dataDir;

    private static final String FILE_NAME = "messages.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<ChatMessage> messages = new ArrayList<>();
    private long nextId = 1;

    @PostConstruct
    public void init() {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            File dir = new File(dataDir);
            if (!dir.exists()) dir.mkdirs();

            File file = new File(dataDir + FILE_NAME);
            if (!file.exists()) {
                messages = new ArrayList<>();
                saveToFile();
                System.out.println("✅ 已创建空消息记录文件: " + dataDir + FILE_NAME);
                return;
            }

            String content = new String(Files.readAllBytes(file.toPath()));
            if (!content.trim().isEmpty()) {
                messages = objectMapper.readValue(content, new TypeReference<List<ChatMessage>>() {});
                nextId = messages.stream().mapToLong(m -> m.getId() == null ? 0 : m.getId()).max().orElse(0) + 1;
                System.out.println("✅ 成功加载聊天记录，共 " + messages.size() + " 条");
            }
        } catch (Exception e) {
            System.err.println("⚠️ 消息记录初始化失败: " + e.getMessage());
            messages = new ArrayList<>();
        }
    }

    public synchronized ChatMessage save(ChatMessage msg) {
        msg.setId(nextId++);
        messages.add(msg);
        saveToFile();
        return msg;
    }

    /** 私聊历史：双方消息，按时间升序 */
    public List<ChatMessage> getPrivateHistory(Long me, Long friendId) {
        return messages.stream()
                .filter(m -> "private".equals(m.getType())
                        && ((me.equals(m.getSenderId()) && friendId.equals(m.getReceiverId()))
                        || (friendId.equals(m.getSenderId()) && me.equals(m.getReceiverId()))))
                .sorted(Comparator.comparingLong(ChatMessage::getTimestamp))
                .collect(Collectors.toList());
    }

    /** 群聊历史 */
    public List<ChatMessage> getGroupHistory(String groupId) {
        return messages.stream()
                .filter(m -> "group".equals(m.getType()) && groupId.equals(m.getGroupId()))
                .sorted(Comparator.comparingLong(ChatMessage::getTimestamp))
                .collect(Collectors.toList());
    }

    /** 大厅历史（最近 N 条） */
    public List<ChatMessage> getLobbyHistory(int limit) {
        List<ChatMessage> lobby = messages.stream()
                .filter(m -> "lobby".equals(m.getType()))
                .sorted(Comparator.comparingLong(ChatMessage::getTimestamp))
                .collect(Collectors.toList());
        if (lobby.size() <= limit) return lobby;
        return lobby.subList(lobby.size() - limit, lobby.size());
    }

    private void saveToFile() {
        try {
            Path filePath = Paths.get(dataDir + FILE_NAME);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(messages);
            Files.write(filePath, json.getBytes());
        } catch (Exception e) {
            System.err.println("❌ 保存消息记录失败: " + e.getMessage());
        }
    }
}
