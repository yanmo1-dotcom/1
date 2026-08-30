package tailai;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * 丐版泰拉瑞亚入口。JDK 26 + 纯 Swing，无第三方依赖。
 * 运行：java -cp out tailai.Main
 * 联机命令行（自动化测试用）：
 *   java -cp out tailai.Main --host [port]       直接以主机身份开始游戏
 *   java -cp out tailai.Main --join <ip> [port]  直接连接主机
 */
public class Main {
    public static void main(String[] args) {
        final int hostPort = -1;
        final String joinIp = null;
        final int joinPort = 25565;

        int hPort = hostPort;
        String jIp = joinIp;
        int jPort = joinPort;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--host") && i + 1 < args.length) {
                hPort = Integer.parseInt(args[i + 1]);
            } else if (args[i].equals("--join") && i + 1 < args.length) {
                jIp = args[i + 1];
                if (i + 2 < args.length) {
                    try {
                        jPort = Integer.parseInt(args[i + 2]);
                    } catch (NumberFormatException ignored) {
                        // 保持默认端口
                    }
                }
            }
        }

        final int fHostPort = hPort;
        final String fJoinIp = jIp;
        final int fJoinPort = jPort;

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("丐版泰拉瑞亚 Terraria Lite (JDK 26)");
            GamePanel panel = new GamePanel();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            frame.add(panel);
            frame.pack();
            if (fHostPort >= 0) {
                frame.setLocation(50, 50);
            } else if (fJoinIp != null) {
                frame.setLocation(380, 180);
            } else {
                frame.setLocationRelativeTo(null);
            }
            frame.setVisible(true);
            panel.start();
            if (fHostPort >= 0) {
                panel.startHost(fHostPort);
            } else if (fJoinIp != null) {
                panel.startClient(fJoinIp, fJoinPort);
            }
        });
    }
}
