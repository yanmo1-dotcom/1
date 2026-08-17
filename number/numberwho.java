package number;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class numberwho {
    // 基础配置
    static final int MIN = 1;
    static final int MAX = 50;
    static final int MAX_TRIES = 8;
    
    static final AtomicLong cooldownUntil = new AtomicLong(0);
    static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();

    // ==================== 1. 纯原生 JSON 工具类 (零依赖) ====================
    static class NativeJsonUtil {
        
        /**
         * 将 Map 和 List 转换为简单的 JSON 字符串
         */
        static String toJson(Map<String, Object> map) {
            StringBuilder sb = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sb.append("  \"").append(entry.getKey()).append("\": ");
                Object val = entry.getValue();
                if (val instanceof String) {
                    sb.append("\"").append(val.toString().replace("\"", "\\\"")).append("\"");
                } else if (val instanceof List) {
                    sb.append("[");
                    List<?> list = (List<?>) val;
                    for (int j = 0; j < list.size(); j++) {
                        sb.append("\"").append(list.get(j).toString().replace("\"", "\\\"")).append("\"");
                        if (j < list.size() - 1) sb.append(", ");
                    }
                    sb.append("]");
                } else {
                    sb.append(val);
                }
                if (i < map.size() - 1) sb.append(",");
                sb.append("\n");
                i++;
            }
            sb.append("}");
            return sb.toString();
        }

        /**
         * 简易 JSON 解析器 (仅支持当前存档格式)
         */
        static Map<String, Object> fromJson(String json) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (json == null || !json.startsWith("{")) return map;
            
            // 移除首尾花括号
            String content = json.substring(1, json.lastIndexOf("}")).trim();
            
            // 简单分割键值对
            String[] pairs = content.split(",\\s*\""); 
            // 修复第一个元素可能没有前缀引号的问题
            if (pairs.length > 0 && !pairs[0].startsWith("\"")) {
                pairs[0] = "\"" + pairs[0];
            }

            for (String pair : pairs) {
                // 查找 key 和 value 的分隔符 ": "
                int colonIdx = pair.indexOf("\": ");
                if (colonIdx == -1) continue;
                
                String key = pair.substring(1, colonIdx); // 去掉前面的引号
                String valStr = pair.substring(colonIdx + 3).trim();
                
                // 处理值
                Object value;
                if (valStr.startsWith("[")) {
                    // 解析数组
                    List<String> list = new ArrayList<>();
                    String arrContent = valStr.substring(1, valStr.lastIndexOf("]"));
                    if (!arrContent.trim().isEmpty()) {
                        String[] items = arrContent.split(",");
                        for (String item : items) {
                            String clean = item.trim().replace("\"", "");
                            if (!clean.isEmpty()) list.add(clean);
                        }
                    }
                    value = list;
                } else if (valStr.startsWith("\"")) {
                    // 字符串
                    value = valStr.substring(1, valStr.lastIndexOf("\""));
                } else {
                    // 数字
                    try { value = Integer.parseInt(valStr); } 
                    catch (Exception e) { value = valStr; }
                }
                map.put(key, value);
            }
            return map;
        }
    }

    // ==================== 2. 存档管理器 ====================
    static class SaveManager {
        static Map<String, Object> load(String filename) {
            File f = new File(filename);
            if (!f.exists()) return null;
            try {
                String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                return NativeJsonUtil.fromJson(json);
            } catch (Exception e) {
                System.out.println(">> [警告] 档案读取受损，已初始化新档案。");
                return null;
            }
        }

        static void save(String filename, Map<String, Object> data) {
            File file = new File(filename);
            
            // 【核心修复】判空防止 NPE
            File parentDir = file.getParentFile();
            if (parentDir != null) {
                parentDir.mkdirs(); 
            }

            try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
                FileChannel channel = raf.getChannel()) {
                
                FileLock lock = channel.lock();
                try {
                    // 合并旧数据 (防止覆盖未保存的字段)
                    Map<String, Object> oldData = load(filename);
                    if (oldData != null) {
                        for (Map.Entry<String, Object> e : oldData.entrySet()) {
                            if (!data.containsKey(e.getKey())) {
                                data.put(e.getKey(), e.getValue());
                            }
                        }
                    }
                    
                    data.put("lastPlayTime", LocalDateTime.now().toString());
                    
                    String json = NativeJsonUtil.toJson(data);
                    raf.setLength(0);
                    raf.write(json.getBytes(StandardCharsets.UTF_8));
                    channel.force(true);
                } finally {
                    lock.release();
                }
            } catch (IOException e) {
                System.out.println(">> 档案保存失败: " + e.getMessage());
            }
        }

        static Map<String, Object> createInitial(String user, String pwd) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", user);
            m.put("password", pwd);
            m.put("totalGames", 0);
            m.put("wins", 0);
            m.put("bestGuesses", Integer.MAX_VALUE);
            m.put("totalGuesses", 0);
            m.put("guessHistory", new ArrayList<String>());
            return m;
        }
    }

    // 用户会话类
    static class UserSession {
        String username;
        String password;
        String saveFile;
        UserSession(String user, String pwd) {
            this.username = user;
            this.password = pwd;
            this.saveFile = "save_" + user.replaceAll("[^a-zA-Z0-9_]", "_") + ".json";
        }
    }

    // ... (rank, fetchDailyQuote 保持不变) ...
    static String rank(int wins) {
        if (wins >= 50) return "星际战士";
        if (wins >= 30) return "政委";
        if (wins >= 20) return "军士";
        if (wins >= 10) return "老兵";
        if (wins >= 5) return "士兵";
        return "新兵";
    }

    static String fetchDailyQuote() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.quotable.io/random?tags=technology"))
                .GET().timeout(Duration.ofSeconds(2))
                .header("Accept", "application/json").build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            int start = body.indexOf("\"content\":\"") + 11;
            int end = body.indexOf("\"", start);
            if (start > 10 && end > start) {
                return body.substring(start, end).replace("\\\"", "\"").replace("\\n", " ");
            }
        } catch (Exception e) {}
        return "为了帝皇！荣耀属于人类！(网络获取失败，使用默认战吼)";
    }

    /**
     * 登录/注册系统
     */
    static UserSession login(Scanner sc) {
        System.out.println("\n========== 帝国终端接入 ==========");
        while (true) {
            System.out.print("请输入代号(用户名): ");
            String user = sc.nextLine().trim();
            if (user.isEmpty()) continue;

            String safeUser = user.replaceAll("[^a-zA-Z0-9_]", "_");
            String fileName = "save_" + safeUser + ".json";
            File f = new File(fileName);

            if (f.exists()) {
                System.out.print(">> 检测到已有档案，请输入访问密钥(密码): ");
                String pwd = sc.nextLine().trim();
                
                Map<String, Object> data = SaveManager.load(fileName);
                if (data != null && pwd.equals(data.get("password"))) {
                    System.out.println(">> 身份确认。欢迎回来，" + user + "。");
                    return new UserSession(safeUser, pwd);
                } else {
                    System.out.println(">> 密钥错误！访问拒绝。");
                }
            } else {
                System.out.print(">> 新晋士兵，请设置访问密钥(密码): ");
                String newPwd = sc.nextLine().trim();
                SaveManager.save(fileName, SaveManager.createInitial(safeUser, newPwd));
                System.out.println(">> 档案建立成功。祝你好运，" + user + "。");
                return new UserSession(safeUser, newPwd);
            }
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        UserSession session = login(sc);
        
        Map<String, Object> saveData = SaveManager.load(session.saveFile);
        if (saveData == null) saveData = SaveManager.createInitial(session.username, session.password);
        
        Random rd = new Random();
        long winStart = System.currentTimeMillis();
        int cnt = 0;

        while (true) {
            int totalGames = (int) saveData.getOrDefault("totalGames", 0);
            int wins = (int) saveData.getOrDefault("wins", 0);
            int bestGuesses = (int) saveData.getOrDefault("bestGuesses", Integer.MAX_VALUE);

            System.out.println("\n========== 战锤档案 ==========");
            System.out.println("操作员: " + session.username);
            System.out.println("当前军衔: " + rank(wins));
            System.out.println("总对局: " + totalGames + " | 胜利: " + wins);
            if (bestGuesses < Integer.MAX_VALUE) 
                System.out.println("最快记录: " + bestGuesses + " 次");
            System.out.println("============================");
            
            System.out.println("[每日战吼]: " + fetchDailyQuote());
            System.out.println("[规则] 猜 " + MIN + "-" + MAX + " 之间的整数，共 " + MAX_TRIES + " 次机会。");
            System.out.println("[警告] 输入过快会被帝皇制裁(限流)，输入0退出。\n");

            int ans = rd.nextInt(MAX) + MIN;
            int validTries = 0;
            int lastPrintedSecond = -1;

            while (validTries < MAX_TRIES) {
                long now = System.currentTimeMillis();
                long coolEnd = cooldownUntil.get();
                if (now < coolEnd) {
                    long remainingSec = (coolEnd - now + 999) / 1000;
                    if ((int)remainingSec != lastPrintedSecond) {
                        System.out.println(">> 帝皇网络过载！冷却中... (剩余 " + remainingSec + " 秒)");
                        lastPrintedSecond = (int)remainingSec;
                    }
                    try { Thread.sleep(200); } catch (Exception e) {}
                    continue;
                }
                lastPrintedSecond = -1;

                if (now - winStart >= 10000) { winStart = now; cnt = 0; }
                if (cnt >= 3) {
                    System.out.println(">> 输入过于频繁！触发防御协议，锁定 10 秒。");
                    cooldownUntil.set(now + 10000);
                    cnt = 0;
                    continue;
                }

                System.out.print("第 " + (validTries + 1) + " 次猜测 (0退出): ");
                if (!sc.hasNextInt()) { sc.next(); cnt++; System.out.println(">> 异端输入！请输入数字。"); continue; }
                
                int g = sc.nextInt();
                cnt++;
                if (g == 0) { System.out.println(">> 正在安全断开连接..."); sc.close(); return; }
                if (g < MIN || g > MAX) { System.out.println(">> 超出范围 (" + MIN + "-" + MAX + ")"); continue; }

                validTries++;

                if (g == ans) {
                    System.out.println("\n*** 恭喜猜中！ ***");
                    saveData.put("totalGames", totalGames + 1);
                    saveData.put("wins", wins + 1);
                    saveData.put("bestGuesses", Math.min(bestGuesses, validTries));
                    saveData.put("totalGuesses", (int)saveData.getOrDefault("totalGuesses", 0) + validTries);
                    
                    // 添加历史记录
                    @SuppressWarnings("unchecked")
                    List<String> history = (List<String>) saveData.get("guessHistory");
                    if (history == null) history = new ArrayList<>();
                    history.add("Game#" + (totalGames+1) + ": Win in " + validTries + " tries");
                    saveData.put("guessHistory", history);
                    
                    SaveManager.save(session.saveFile, saveData);
                    break;
                }
                
                System.out.println(g > ans ? ">> 太大了" : ">> 太小了");
                
                if (validTries == MAX_TRIES) {
                    System.out.println("\n!!! 失败 !!! 答案是: " + ans);
                    saveData.put("totalGames", totalGames + 1);
                    saveData.put("totalGuesses", (int)saveData.getOrDefault("totalGuesses", 0) + validTries);
                    SaveManager.save(session.saveFile, saveData);
                }
            }

            System.out.print("\n再来一局? (y/n): ");
            String choice = sc.next();
            if (!choice.equalsIgnoreCase("y")) {
                System.out.println(">> 正在安全断开连接...");
                break;
            }
        }
        sc.close();
    }
}