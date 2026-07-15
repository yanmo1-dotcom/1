// WarhammerGuessGame.java
import java.io.*;
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
    }

    static Record load() {
        Record r = new Record();
        File f = new File(SAVE_FILE);
        if (!f.exists()) {
            return r;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String s;
            while ((s = br.readLine()) != null) {
                String[] p = s.split("=", 2);
                if (p.length != 2) continue;
                switch (p[0]) {
                    case "totalGames":
                        r.totalGames = Integer.parseInt(p[1]);
                        break;
                    case "wins":
                        r.wins = Integer.parseInt(p[1]);
                        break;
                    case "bestGuesses":
                        r.bestGuesses = Integer.parseInt(p[1]);
                        break;
                    case "totalGuesses":
                        r.totalGuesses = Integer.parseInt(p[1]);
                        break;
                }
            }
        } catch (Exception e) {
            // 读取失败则继续使用默认存档
        }
        return r;
    }

    static void save(Record r) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(SAVE_FILE))) {
            bw.write("totalGames=" + r.totalGames);
            bw.newLine();
            bw.write("wins=" + r.wins);
            bw.newLine();
            bw.write("bestGuesses=" + r.bestGuesses);
            bw.newLine();
            bw.write("totalGuesses=" + r.totalGuesses);
        } catch (IOException e) {
            System.out.println("保存存档失败。");
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
        Record rec = load();
        Random rd = new Random();

        while (true) {
            System.out.println("========== 存档 ==========");
            System.out.println("军衔: " + rank(rec.wins));
            System.out.println("总局: " + rec.totalGames + " 次");
            System.out.println("猜中: " + rec.wins + " 次");
            if (rec.bestGuesses < Integer.MAX_VALUE) {
                System.out.println("最少猜中次数: " + rec.bestGuesses + " 次");
            }
            System.out.println("所有猜测次数: " + rec.totalGuesses + " 次");
            System.out.println("========================");

            int ans = rd.nextInt(MAX) + MIN;
            long winStart = System.currentTimeMillis();
            int cnt = 0;

            for (int i = 1; i <= MAX_TRIES; i++) {
                long now = System.currentTimeMillis();
                if (now - winStart >= LIMIT_MS) {
                    winStart = now;
                    cnt = 0;
                }
                if (cnt >= LIMIT_COUNT) {
                    long wait = Math.max(0, LIMIT_MS - (now - winStart));
                    System.out.println("输入过快，请等待" + ((wait + 999) / 1000) + "秒");
                    try {
                        Thread.sleep(wait);
                    } catch (Exception e) {
                        // ignore
                    }
                    i--;
                    continue;
                }

                System.out.print("第" + i + "次(0退出): ");
                if (!sc.hasNextInt()) {
                    System.out.println("请输入数字");
                    sc.next();
                    i--;
                    continue;
                }

                int g = sc.nextInt();
                if (g == 0) {
                    save(rec);
                    sc.close();
                    return;
                }
                if (g < MIN || g > MAX) {
                    System.out.println("范围1-50");
                    i--;
                    continue;
                }

                cnt++;
                rec.totalGuesses++;

                if (g == ans) {
                    System.out.println("恭喜猜中!");
                    rec.wins++;
                    rec.totalGames++;
                    if (i < rec.bestGuesses) {
                        rec.bestGuesses = i;
                    }
                    System.out.println("当前累计猜中次数: " + rec.wins + " 次");
                    save(rec);
                    break;
                }

                System.out.println(g > ans ? "猜大了" : "猜小了");

                if (i == MAX_TRIES) {
                    System.out.println("失败! 答案: " + ans);
                    rec.totalGames++;
                    save(rec);
                }
            }

            System.out.print("继续(y/n): ");
            if (!sc.next().equalsIgnoreCase("y")) {
                save(rec);
                break;
            }
        }

        sc.close();
    }
}