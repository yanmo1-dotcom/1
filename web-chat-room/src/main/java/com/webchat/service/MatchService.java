package com.webchat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.model.MatchRecord;
import com.webchat.repository.MatchJdbcRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 战绩记录持久化：matches.json，独立于聊天消息。
 */
@Service
@DependsOn("userService")
public class MatchService {

    private final MatchJdbcRepository matchRepository;

    public MatchService(MatchJdbcRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    @Value("${webchat.data-dir:/opt/webchat-data/users/}")
    private String dataDir;
    private String backupDir;

    private static final String FILE_NAME = "matches.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private List<MatchRecord> matches = new ArrayList<>();
    private long nextId = 1;

    @PostConstruct
    public void init() {
        backupDir = dataDir + "backup/";
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            File dir = new File(dataDir);
            if (!dir.exists()) dir.mkdirs();
            new File(backupDir).mkdirs();

            List<MatchRecord> databaseMatches = matchRepository.findAll();
            if (!databaseMatches.isEmpty()) {
                matches = new ArrayList<>(databaseMatches);
                nextId = matches.stream().mapToLong(m -> m.getId() == null ? 0 : m.getId()).max().orElse(0) + 1;
                System.out.println("Loaded " + matches.size() + " matches from database");
                return;
            }

            File file = new File(dataDir + FILE_NAME);
            if (!file.exists()) {
                matches = new ArrayList<>();
                saveToFile();
                System.out.println("✅ 已创建空战绩文件: " + dataDir + FILE_NAME);
                return;
            }
            String content = new String(Files.readAllBytes(file.toPath()));
            if (!content.trim().isEmpty()) {
                matches = objectMapper.readValue(content, new TypeReference<List<MatchRecord>>() {});
                for (MatchRecord match : matches) matchRepository.save(match);
                nextId = matches.stream().mapToLong(m -> m.getId() == null ? 0 : m.getId()).max().orElse(0) + 1;
                System.out.println("✅ 成功加载战绩记录，共 " + matches.size() + " 条");
            }
        } catch (Exception e) {
            System.err.println("⚠️ 战绩记录初始化失败: " + e.getMessage());
            matches = new ArrayList<>();
        }
    }

    public synchronized MatchRecord save(MatchRecord record) {
        record.setId(nextId++);
        matches.add(record);
        saveToFile();
        return record;
    }

    /** 某用户的全部战绩，按时间倒序 */
    public List<MatchRecord> getHistoryByUser(Long userId) {
        return matches.stream()
                .filter(m -> userId.equals(m.getPlayerAId()) || userId.equals(m.getPlayerBId()))
                .sorted(Comparator.comparingLong(MatchRecord::getFinishedAt).reversed())
                .collect(Collectors.toList());
    }

    /** 某用户最近 N 条 */
    public List<MatchRecord> getRecent(Long userId, int limit) {
        List<MatchRecord> all = getHistoryByUser(userId);
        if (all.size() <= limit) return all;
        return all.subList(0, limit);
    }

    public List<MatchRecord> getAll() {
        return matches.stream()
                .sorted(Comparator.comparingLong(MatchRecord::getFinishedAt).reversed())
                .collect(Collectors.toList());
    }

    private void saveToFile() {
        try {
            for (MatchRecord match : matches) matchRepository.save(match);
            Path filePath = Paths.get(dataDir + FILE_NAME);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(matches);
            Files.write(filePath, json.getBytes());
            backupFile(filePath);
        } catch (Exception e) {
            System.err.println("❌ 保存战绩记录失败: " + e.getMessage());
        }
    }

    private void backupFile(Path source) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupPath = Paths.get(backupDir + "matches_backup_" + timestamp + ".json");
            Files.copy(source, backupPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.err.println("⚠️ 战绩自动备份失败: " + e.getMessage());
        }
    }
}
