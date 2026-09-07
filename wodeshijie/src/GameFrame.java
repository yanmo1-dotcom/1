import javax.swing.JFrame;
import java.awt.BorderLayout;

/**
 * 游戏主窗口
 */
public class GameFrame extends JFrame {

    private GamePanel gamePanel;

    public GameFrame() {
        setTitle("我的世界 - Minecraft 2D");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        gamePanel = new GamePanel();
        add(gamePanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null); // 窗口居中
        setResizable(true);
        setMinimumSize(new java.awt.Dimension(800, 500));
    }

    public void start() {
        setVisible(true);
        gamePanel.requestFocusInWindow();
    }
}
