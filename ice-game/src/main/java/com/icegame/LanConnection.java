package com.icegame;

import javax.swing.SwingUtilities;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

final class LanConnection implements AutoCloseable {
    static final int PORT = 5050;
    private volatile Socket socket;
    private volatile ServerSocket server;
    private volatile PrintWriter writer;
    private volatile boolean open = true;

    void host(Consumer<String> message, Consumer<String> input) {
        Thread thread = new Thread(() -> {
            try {
                server = new ServerSocket(PORT);
                ui(message, "等待玩家加入… 房主 IP：" + GameFrame.localIp() + "  端口：" + PORT);
                socket = server.accept();
                setup(input);
                ui(message, "玩家已连接，比赛开始！");
            } catch (IOException e) {
                if (open) ui(message, "创建房间失败：" + e.getMessage());
            }
        }, "ice-game-host");
        thread.setDaemon(true);
        thread.start();
    }

    void join(String host, Consumer<String> message, Consumer<String> state) {
        Thread thread = new Thread(() -> {
            try {
                ui(message, "正在连接 " + host + "…");
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, PORT), 5000);
                setup(state);
                ui(message, "已连接房主，比赛开始！");
            } catch (IOException e) {
                if (open) ui(message, "连接失败：" + e.getMessage());
            }
        }, "ice-game-client");
        thread.setDaemon(true);
        thread.start();
    }

    boolean connected() { return socket != null && socket.isConnected() && !socket.isClosed(); }

    void send(String line) {
        PrintWriter out = writer;
        if (out != null) out.println(line);
    }

    private void setup(Consumer<String> receiver) throws IOException {
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (open && (line = reader.readLine()) != null) receiver.accept(line);
        } finally {
            close();
        }
    }

    private static void ui(Consumer<String> consumer, String value) {
        SwingUtilities.invokeLater(() -> consumer.accept(value));
    }

    @Override public void close() {
        open = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        try { if (server != null) server.close(); } catch (IOException ignored) {}
    }
}
