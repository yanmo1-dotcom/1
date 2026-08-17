package com.chatroom.client;

import com.chatroom.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ChatClient {
    private final String HOST = "121.43.147.27";
    private final int PORT = 8888;
    private final ObjectMapper mapper = new ObjectMapper();
    
    private String myName;
    // Bug8: 主线程读、接收线程写，必须加 volatile 保证可见性
    private volatile boolean isRegistered = false;

    public void connect() {
        try (Socket socket = new Socket(HOST, PORT);
             Scanner scanner = new Scanner(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            System.out.println("🔗 已连接服务器");

            // 启动接收线程
            Thread recvThread = new Thread(() -> receiveMessages(socket));
            recvThread.setDaemon(true);
            recvThread.start();

            // 主线程发送
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input.trim())) break;

                Message msg;
                
                // 核心逻辑：判断当前状态
                if (!isRegistered) {
                    if (input.trim().isEmpty()) {
                        System.out.println("❌ 昵称不能为空！");
                        continue;
                    }
                    // 【修复】确保昵称长度符合服务端要求 (1-20)
                    if (input.trim().length() > 20) {
                        System.out.println("❌ 昵称不能超过20个字符！");
                        continue;
                    }
                    
                    myName = input.trim();
                    // 【关键修复】昵称必须放在 content 字段，服务端从 content 读取
                    msg = new Message("login", null, myName, null);
                    // 注意：这里不立即设 isRegistered=true，等收到系统欢迎语再设
                    // 但为了简化，我们先假设发送即尝试注册
                    // 实际应由服务端确认后切换状态，但当前架构下这样也可以
                    System.out.println(" 正在注册昵称 [" + myName + "]...");
                } else {
                    // 已注册，正常发送
                    msg = new Message("chat", myName, input, null);
                }
                
                out.println(mapper.writeValueAsString(msg));
            }
        } catch (IOException e) {
            System.err.println("❌ 连接断开: " + e.getMessage());
        } finally {
            System.out.println("👋 客户端已退出");
        }
    }

    private void receiveMessages(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                Message msg = mapper.readValue(line, Message.class);

                switch (msg.getType()) {
                    case "system":
                        System.out.println("[系统] " + msg.getContent());
                        if (msg.getContent() != null && msg.getContent().contains("欢迎")) {
                            isRegistered = true;
                        }
                        break;
                    case "chat":
                        System.out.println("[" + msg.getTarget() + "] " + msg.getSender() + ": " + msg.getContent());
                        break;
                    case "whisper":
                        if (msg.getSender().equals(myName))
                            System.out.println("[我对 " + msg.getTarget() + " 说]: " + msg.getContent());
                        else
                            System.out.println("[" + msg.getSender() + " 私语]: " + msg.getContent());
                        break;
                    case "error":
                        System.out.println("[错误] " + msg.getContent());
                        break;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ 与服务器的连接已断开");
        } finally {
            // Bug10: 接收线程退出时关闭 socket，使主线程的 out.println 抛出异常从而退出循环
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public static void main(String[] args) throws UnsupportedEncodingException {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        new ChatClient().connect();
    }
}