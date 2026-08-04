package com.icegame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

final class GameFrame extends JFrame {
    private static final Color ICE = new Color(218, 244, 255);

    GameFrame() {
        super("ICE GAME - Java 冰球");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);
        showMenu();
        pack();
        setLocationRelativeTo(null);
    }

    void showMenu() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setPreferredSize(new Dimension(960, 600));
        root.setBackground(new Color(8, 31, 56));
        root.setBorder(new EmptyBorder(45, 80, 45, 80));

        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel title = label("ICE GAME", 54, Font.BOLD, Color.WHITE);
        JLabel subtitle = label("JAVA 冰球竞技场", 20, Font.PLAIN, ICE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(title);
        box.add(Box.createVerticalStrut(4));
        box.add(subtitle);
        box.add(Box.createVerticalStrut(55));
        box.add(button("单人模式 · 对战 AI", () -> start(GamePanel.Mode.SINGLE, null)));
        box.add(Box.createVerticalStrut(16));
        box.add(button("双人联机 · 创建房间", () -> start(GamePanel.Mode.HOST, null)));
        box.add(Box.createVerticalStrut(16));
        box.add(button("双人联机 · 加入房间", this::joinGame));
        box.add(Box.createVerticalStrut(34));
        JLabel tip = label("W / A / S / D 控制球拍 · 先得 7 分获胜", 15, Font.PLAIN, new Color(150, 190, 215));
        tip.setAlignmentX(Component.CENTER_ALIGNMENT);
        box.add(tip);
        root.add(box);
        setContentPane(root);
        revalidate();
        repaint();
        pack();
    }

    private void joinGame() {
        String host = JOptionPane.showInputDialog(this, "请输入房主的 IPv4 地址：", "加入联机房间", JOptionPane.QUESTION_MESSAGE);
        if (host != null && !host.isBlank()) start(GamePanel.Mode.CLIENT, host.trim());
    }

    private void start(GamePanel.Mode mode, String host) {
        GamePanel game = new GamePanel(this, mode, host);
        setContentPane(game);
        revalidate();
        game.requestFocusInWindow();
    }

    static String localIp() {
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) continue;
                for (var address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private JButton button(String text, Runnable action) {
        JButton b = new JButton(text);
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(390, 58));
        b.setPreferredSize(new Dimension(390, 58));
        b.setFont(new Font("SansSerif", Font.BOLD, 18));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(17, 126, 184));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> action.run());
        return b;
    }

    private JLabel label(String text, int size, int style, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("SansSerif", style, size));
        l.setForeground(color);
        return l;
    }
}
