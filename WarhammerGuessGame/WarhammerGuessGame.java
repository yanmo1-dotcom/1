import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class WarhammerGuessGame {
    static final String SAVE_FILE = "guess_record.txt";
    static final int MIN = 1;
    static final int MAX = 50;
    static final int MAX_TRIES = 8;
    static final int LIMIT_COUNT = 3; 
    static final long LIMIT_MS = 10000; 

    static class Record {
        int totalGames;
        int wins;
        int bestGuesses = Integer.MAX_VALUE;
        int totalGuesses;

        // 构造完整记录
        Record(int t, int w, int b, int g) {
            this.totalGames = t; this.wins = w; 
            this.bestGuesses = b; this.totalGuesses = g;
        }
        
        // 默认构造（用于解析）
        Record() {}
    }

    /**
     * 【新增】底层解析方法：从任意输入流解析数据
     * 供启动加载和保存时的“重读”共用
     */
    static Record parseRecord(InputStream is) {
        Record r = new Record();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String s;
            while ((s = br.readLine()) != null) {
                String[] p = s.split("=", 2);
                if (p.length != 2) continue;
                try {
                    switch (p[0].trim()) {
                        case "totalGames": r.totalGames = Integer.parseInt(p[1]); break;
                        case "wins": r.wins = Integer.parseInt(p[1]); break;
                        case "bestGuesses": r.bestGuesses = Integer.parseInt(p[1]); break;
                        case "totalGuesses": r.totalGuesses = Integer.parseInt(p[1]); break;
                    }
                } catch (NumberFormatException e) {}
            }
        } catch (Exception e) {}
        return r;
    }

    static Record load() {
        File f = new File(SAVE_FILE);
        if (!f.exists()) return new Record(0, 0, Integer.MAX_VALUE, 0);
        
        try (FileInputStream fis = new FileInputStream(f)) {
            return parseRecord(fis);
        } catch (Exception e) {
            System.out.println("读取存档失败，将使用新存档。");
            return new Record(0, 0, Integer.MAX_VALUE, 0);
        }
    }

    /**
     * 【核心修改】原子化保存：在锁内部完成“读取-合并-写入”
     * @param delta 本局产生的增量数据（例如 wins=1, totalGames=1）
     */
    static void save(Record delta) {
        File file = new File(SAVE_FILE);
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
            FileChannel channel = raf.getChannel()) {
            
            // 1. 获取独占写锁 (阻塞式，满足验收标准 i)
            FileLock lock = channel.lock(); 
            
            try {
                // 2. 【关键】在锁内部重新读取最新数据，防止覆盖
                // 将文件指针重置到开头
                raf.seek(0);
                Record current = parseRecord(new FileInputStream(raf.getFD()));
                
                // 3. 合并数据 (原子操作)
                current.totalGames += delta.totalGames;
                current.wins += delta.wins;
                current.totalGuesses += delta.totalGuesses;
                
                // 处理最佳记录：取最小值
                if (delta.bestGuesses != Integer.MAX_VALUE) {
                    if (current.bestGuesses == Integer.MAX_VALUE || 
                        delta.bestGuesses < current.bestGuesses) {
                        current.bestGuesses = delta.bestGuesses;
                    }
                }

                // 4. 清空并写入合并后的最新数据
                raf.setLength(0);
                try (BufferedWriter bw = new BufferedWriter(
                        new OutputStreamWriter(Channels.newOutputStream(channel), StandardCharsets.UTF_8))) {
                    bw.write("totalGames=" + current.totalGames); bw.newLine();
                    bw.write("wins=" + current.wins); bw.newLine();
                    bw.write("bestGuesses=" + current.bestGuesses); bw.newLine();
                    bw.write("totalGuesses=" + current.totalGuesses);
                    bw.flush();
                }
                
            } finally {
                lock.release();
            }
            
        } catch (IOException e) {
            System.out.println("保存存档失败: " + e.getMessage());
        }
    }

    static String rank(int wins) {
        if (wins >= 50) return "星际战士";
        if (wins >= 30) return "政委";
        if (wins >= 20) return "军士";
        if (wins >= 10) return "老兵";
        if (wins >= 5) return "士兵";
        return "新兵";
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // 启动时仅用于显示界面，不用于保存基准
        Record displayRec = load();
        Random rd = new Random();

        while (true) {
            System.out.println("\n========== 战锤档案 ==========");
            System.out.println("当前军衔: " + rank(displayRec.wins));
            System.out.println("总对局: " + displayRec.totalGames + " | 胜利: " + displayRec.wins);
            if (displayRec.bestGuesses < Integer.MAX_VALUE) 
                System.out.println("最快记录: " + displayRec.bestGuesses + " 次");
            System.out.println("============================");
            System.out.println("[规则] 猜 " + MIN + "-" + MAX + " 之间的整数，共 " + MAX_TRIES + " 次机会。");
            System.out.println("[警告] 输入过快会被帝皇制裁(限流)，输入0退出。\n");

            int ans = rd.nextInt(MAX) + MIN;
            long winStart = System.currentTimeMillis();
            int cnt = 0;      
            int validTries = 0; 

            while (validTries < MAX_TRIES) {
                // ... (限流逻辑保持不变) ...
                long now = System.currentTimeMillis();
                if (now - winStart >= LIMIT_MS) { winStart = now; cnt = 0; }
                if (cnt >= LIMIT_COUNT) {
                    long wait = Math.max(0, LIMIT_MS - (now - winStart));
                    System.out.println(">> 输入过于频繁！冷却中... (等待 " + ((wait + 999) / 1000) + " 秒)");
                    try { Thread.sleep(wait); } catch (Exception e) {}
                    winStart = System.currentTimeMillis(); cnt = 0; continue; 
                }

                System.out.print("第 " + (validTries + 1) + " 次猜测 (0退出): ");
                if (!sc.hasNextInt()) { sc.next(); cnt++; System.out.println(">> 异端输入！请输入数字。"); continue; }
                
                int g = sc.nextInt();
                cnt++; 
                if (g == 0) { 
                    // 退出时不需要保存增量，直接返回
                    sc.close(); return; 
                }
                if (g < MIN || g > MAX) { System.out.println(">> 超出范围 (" + MIN + "-" + MAX + ")"); continue; }

                validTries++;

                if (g == ans) {
                    System.out.println("\n*** 恭喜猜中！ ***");
                    // 【修改】构建增量对象并保存
                    Record delta = new Record(1, 1, validTries, validTries);
                    save(delta);
                    
                    // 更新本地显示用的数据（可选，为了界面好看）
                    displayRec.wins++; displayRec.totalGames++; displayRec.totalGuesses += validTries;
                    if(validTries < displayRec.bestGuesses) displayRec.bestGuesses = validTries;
                    break;
                }
                
                System.out.println(g > ans ? ">> 太大了" : ">> 太小了");
                
                if (validTries == MAX_TRIES) {
                    System.out.println("\n!!! 失败 !!! 答案是: " + ans);
                    // 【修改】构建增量对象并保存（只增加总局数，不增加胜场）
                    Record delta = new Record(1, 0, Integer.MAX_VALUE, validTries);
                    save(delta);

                    displayRec.totalGames++; displayRec.totalGuesses += validTries;
                }
            }

            System.out.print("\n再来一局? (y/n): ");
            String choice = sc.next(); 
            if (!choice.equalsIgnoreCase("y")) {
                break;
            }
        }
        sc.close();
    }
}