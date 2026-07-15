import java.util.Random;
import java.util.Scanner;

public class myguessnumber {

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);
        Random random = new Random();

        int answer = random.nextInt(50) + 1;

        int maxTimes = 8;

        long startTime = System.currentTimeMillis();

        int guessCount = 0;

        System.out.println("======================");
        System.out.println("欢迎来到猜数字游戏！士兵");
        System.out.println("我已经生成了一个 1-50 之间的数字。");
        System.out.println("你有 " + maxTimes + " 次机会猜中它，证明你的忠诚！");
        System.out.println("======================");

        for (int i = 1; i <= maxTimes; i++) {

            long now = System.currentTimeMillis();

            if (now - startTime >= 10000) {
                startTime = now;
                guessCount = 0;
            }

            if (guessCount >= 3) {

                long waitTime = 10000 - (now - startTime);

                if (waitTime < 0) {
                    waitTime = 0;
                }

                System.out.println("--------------------------------");
                System.out.println("帝国通讯系统检测到异常输入频率！");
                System.out.println("疑似使用混沌科技进行暴力破解！");
                System.out.println("请等待 " + (waitTime / 1000 + 1) + " 秒后继续猜测。");
                System.out.println("--------------------------------");

                i--;

                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                continue;
            }

            System.out.print("第 " + i + " 次猜测，请输入数字：");

            if (!scanner.hasNextInt()) {
                System.out.println("请输入数字，不要输入字母或其他字符！");
                scanner.next();
                i--;
                continue;
            }

            int guess = scanner.nextInt();

            if (guess < 1 || guess > 50) {
                System.out.println("请输入 1~50 之间的数字！");
                i--;
                continue;
            }

            guessCount++;

            if (guess == answer) {

                System.out.println();
                System.out.println("★★★★★★★★★★★★★★★★");
                System.out.println("恭喜你，猜对了！");
                System.out.println("帝皇认可了你的忠诚！");
                System.out.println("答案就是：" + answer);
                System.out.println("你一共猜了 " + i + " 次。");
                System.out.println("★★★★★★★★★★★★★★★★");

                scanner.close();

                System.out.println("我将送你去战场，为帝皇效忠！");
                return;
            }

            if (guess > answer) {
                System.out.println("猜大了！傻子。");
            } else {
                System.out.println("猜小了！畜生。");
            }

            if (i == maxTimes) {

                System.out.println("----------------------");
                System.out.println("该死，8次机会已经用完！");
                System.out.println("你这个恐虐的畜生，永远也猜不到正确答案！");
                System.out.println("正确答案是：" + answer);
                System.out.println("----------------------");
            }
        }

        scanner.close();

        System.out.println("我将送你去战场，为帝皇效忠！");
    }
}