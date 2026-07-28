package com.webchat.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.model.RankRule;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 积分规则引擎：规则持久化在 rank-rules.json，管理员可随时热修改，无需重启。
 */
@Service
public class RankRuleService {

    @Value("${webchat.data-dir:/opt/webchat-data/users/}")
    private String dataDir;

    private static final String FILE_NAME = "rank-rules.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile RankRule rules = new RankRule();

    @PostConstruct
    public void init() {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try {
            File dir = new File(dataDir);
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dataDir + FILE_NAME);
            if (!file.exists()) {
                rules = new RankRule();
                saveToFile();
                System.out.println("✅ 已创建默认积分规则文件: " + dataDir + FILE_NAME);
                return;
            }
            String content = new String(Files.readAllBytes(file.toPath()));
            if (!content.trim().isEmpty()) {
                rules = objectMapper.readValue(content, RankRule.class);
                System.out.println("✅ 已加载积分规则: 胜+" + rules.getWinPoints()
                        + " 负" + rules.getLossPoints() + " 平+" + rules.getDrawPoints());
            }
        } catch (Exception e) {
            System.err.println("⚠️ 积分规则初始化失败: " + e.getMessage());
            rules = new RankRule();
        }
    }

    public RankRule getRules() { return rules; }

    /** 热更新规则：写文件并替换内存，无需重启 */
    public synchronized void updateRules(RankRule newRules) {
        this.rules = newRules;
        saveToFile();
        System.out.println("✅ 积分规则已热更新: 胜+" + newRules.getWinPoints()
                + " 负" + newRules.getLossPoints() + " 平+" + newRules.getDrawPoints()
                + " 分差阈值" + newRules.getDiffThreshold() + " 爆冷加成" + newRules.getUpsetBonus());
    }

    /**
     * 计算一局对战的积分变动。
     * @param winnerPoints 胜者当前积分
     * @param loserPoints  败者当前积分
     * @return [胜者delta, 败者delta]
     */
    public int[] calcRankDelta(int winnerPoints, int loserPoints) {
        RankRule r = rules;
        int winDelta = r.getWinPoints();
        int lossDelta = r.getLossPoints();
        // 动态调整：低分赢高分额外 +，高分输低分额外 -
        int diff = loserPoints - winnerPoints; // 正=胜者分低于败者(低分赢高分)
        if (diff >= r.getDiffThreshold()) {
            winDelta += r.getUpsetBonus();
            lossDelta -= r.getUpsetBonus();
        }
        return new int[]{winDelta, lossDelta};
    }

    /** 平局双方变动 */
    public int drawDelta() { return rules.getDrawPoints(); }

    private void saveToFile() {
        try {
            Path filePath = Paths.get(dataDir + FILE_NAME);
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rules);
            Files.write(filePath, json.getBytes());
        } catch (Exception e) {
            System.err.println("❌ 保存积分规则失败: " + e.getMessage());
        }
    }
}
