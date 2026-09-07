import javax.swing.SwingUtilities;

/**
 * 程序入口
 */
public class Main {
    public static void main(String[] args) {
        // 在EDT线程中创建和显示GUI
        SwingUtilities.invokeLater(() -> {
            GameFrame frame = new GameFrame();
            frame.start();
        });
    }
}
