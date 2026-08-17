import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * 程序入口与窗口初始化。
 *
 * 为什么用 SwingUtilities.invokeLater：
 * Swing 组件必须在"事件分派线程(EDT)"上创建与操作，否则可能出现线程竞争导致的
 * 随机崩溃或重绘异常。main 方法运行在主线程，所以把窗口创建逻辑投递到 EDT。
 */
public class GameMain {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("2D 打飞机游戏");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false); // 固定尺寸，简化坐标逻辑

            GamePanel panel = new GamePanel();
            frame.setContentPane(panel);

            frame.pack();                       // 根据 panel 的 preferredSize 自适应窗口大小
            frame.setLocationRelativeTo(null);  // 窗口屏幕居中
            frame.setVisible(true);

            panel.requestFocusInWindow(); // 让面板获得键盘焦点，否则按键无响应
            panel.startGame();            // 启动游戏循环（Timer）
        });
    }
}
