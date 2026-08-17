package 旧的;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 纯标准 Java 实现 JSON 读写示例（无第三方依赖）。
 * 真实项目中应使用 Jackson 或 Gson 库，本例用于理解底层原理。
 */
public class JsonExample {

    static final String FILE_PATH = "player.json";

    // ── 数据模型 ────────────────────────────────────────────────────────
    static class Player {
        String name;
        int wins;
        int totalGames;
        List<String> achievements;

        Player(String name, int wins, int totalGames, List<String> achievements) {
            this.name = name;
            this.wins = wins;
            this.totalGames = totalGames;
            this.achievements = achievements;
        }
    }

    // ── 序列化：把 Player 对象 → JSON 字符串 ───────────────────────────
    static String toJson(Player p) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(p.name).append("\",\n");
        sb.append("  \"wins\": ").append(p.wins).append(",\n");
        sb.append("  \"totalGames\": ").append(p.totalGames).append(",\n");
        sb.append("  \"achievements\": [");
        for (int i = 0; i < p.achievements.size(); i++) {
            sb.append("\n    \"").append(p.achievements.get(i)).append("\"");
            if (i < p.achievements.size() - 1) sb.append(",");
        }
        sb.append("\n  ]\n}");
        return sb.toString();
    }

    // ── 反序列化：从 JSON 字符串中提取字段 ────────────────────────────
    static String parseString(String json, String key) {
        String marker = "\"" + key + "\": \"";
        int start = json.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    static int parseInt(String json, String key) {
        String marker = "\"" + key + "\": ";
        int start = json.indexOf(marker);
        if (start < 0) return 0;
        start += marker.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) end++;
        return Integer.parseInt(json.substring(start, end));
    }

    static List<String> parseStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String marker = "\"" + key + "\": [";
        int start = json.indexOf(marker);
        if (start < 0) return result;
        start = json.indexOf("[", start) + 1;
        int end = json.indexOf("]", start);
        String block = json.substring(start, end);
        for (String item : block.split(",")) {
            String trimmed = item.trim().replaceAll("^\"|\"$", "");
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    static Player fromJson(String json) {
        return new Player(
            parseString(json, "name"),
            parseInt(json, "wins"),
            parseInt(json, "totalGames"),
            parseStringArray(json, "achievements")
        );
    }

    // ── 文件读写 ────────────────────────────────────────────────────────
    static void writeFile(String path, String content) throws IOException {
        Files.writeString(Path.of(path), content);
    }

    static String readFile(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    // ── 主流程 ──────────────────────────────────────────────────────────
    public static void main(String[] args) throws Exception {

        // 1. 生成 JSON 文件
        System.out.println("=== 1. 生成 JSON 文件 ===");
        Player player = new Player("昊昊", 5, 10,
                new ArrayList<>(Arrays.asList("首次胜利", "连胜三局")));
        String json = toJson(player);
        writeFile(FILE_PATH, json);
        System.out.println("已写入 " + FILE_PATH + "：\n" + json);

        // 2. 解析 JSON 文件
        System.out.println("\n=== 2. 解析 JSON 文件 ===");
        String raw = readFile(FILE_PATH);
        Player loaded = fromJson(raw);
        System.out.println("名字: " + loaded.name);
        System.out.println("胜场: " + loaded.wins);
        System.out.println("总场: " + loaded.totalGames);
        System.out.println("成就: " + loaded.achievements);

        // 3. 修改字段并保存
        System.out.println("\n=== 3. 修改：wins+1，新增成就 ===");
        loaded.wins += 1;
        loaded.achievements.add("十连击");
        writeFile(FILE_PATH, toJson(loaded));
        System.out.println("修改完成");

        // 4. 打印最终 JSON
        System.out.println("\n=== 4. 打印最终 JSON ===");
        System.out.println(readFile(FILE_PATH));
    }
}
