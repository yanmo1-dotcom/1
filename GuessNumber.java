import java.util.Random;
import java.util.Scanner;

/**
 * 猜数字小游戏
 * 
 * 游戏规则：
 * 1. 程序随机生成一个 1-100 之间的数字
 * 2. 玩家有 7 次机会猜数字
 * 3. 每次猜测后，程序会提示：
 *    - 猜大了
 *    - 猜小了
 *    - 猜对了
 * 4. 如果 7 次机会用完仍未猜中，游戏结束
 */
public class GuessNumber {

    public static void main(String[] args) {

        // 创建 Scanner 对象，用于接收玩家输入的数字
        Scanner scanner = new Scanner(System.in);

        // 创建 Random 对象，用于生成随机数字
        Random random = new Random();

        // 生成一个 1-100 之间的随机数
        // nextInt(100) 会生成 0-99 的数字，所以需要 +1
        int answer = random.nextInt(100) + 1;


        // 设置玩家最大猜测次数为 7 次
        int maxTimes = 7;


        // 游戏开始提示
        System.out.println("======================");
        System.out.println("欢迎来到猜数字游戏！");
        System.out.println("我已经生成了一个 1-100 之间的数字");
        System.out.println("你有 " + maxTimes + " 次机会猜中它！");
        System.out.println("======================");


        // 使用 for 循环控制玩家最多猜 7 次
        for (int i = 1; i <= maxTimes; i++) {

            System.out.print("第 " + i + " 次猜测，请输入数字：");


            // 接收玩家输入的数字
            int guess = scanner.nextInt();


            // 判断玩家猜测结果

            if (guess == answer) {

                // 如果猜对，输出成功信息并结束游戏
                System.out.println("恭喜你，猜对了！");
                System.out.println("答案就是：" + answer);
                System.out.println("你一共猜了 " + i + " 次。");

                break; // 结束循环

            } else if (guess > answer) {

                // 如果玩家输入的数字比答案大
                System.out.println("猜大了！再试一次。");


            } else {

                // 如果玩家输入的数字比答案小
                System.out.println("猜小了！再试一次。");

            }


            // 如果已经使用完最后一次机会
            if (i == maxTimes) {

                System.out.println("----------------------");
                System.out.println("很遗憾，7次机会已经用完！");
                System.out.println("正确答案是：" + answer);
                System.out.println("----------------------");

            }
        }


        // 关闭 Scanner，释放资源
        scanner.close();

        System.out.println("游戏结束，感谢游玩！");
    }
}