package com.chatroom.server;

import com.chatroom.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

public class ChatServer {
    private final int PORT = 8888;
    private final ObjectMapper mapper = new ObjectMapper();
    
    // 公开列表，方便 ClientHandler 遍历查找用户
    public final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    public final Map<String, Room> rooms = new ConcurrentHashMap<>();
    
    // 日志服务
    private LoggerService logger;

    // 【新增】房间名校验正则：允许中文、字母、数字，长度1-20
    private static final Pattern ROOM_NAME_PATTERN = Pattern.compile("^[\\w\\u4e00-\\u9fa5]{1,20}$");

    public ChatServer() {
        try {
            this.logger = new LoggerService();
            System.out.println("📝 [审计] 日志系统初始化成功");
        } catch (Exception e) {
            System.err.println("⚠️ [审计] 日志系统初始化失败，将禁用日志功能: " + e.getMessage());
            this.logger = null;
        }
        
        // 初始化默认的公共聊天室
        rooms.put("public", new Room("public", null));
    }

    public void start() {
        System.out.println(" [服务端] 启动成功，监听端口: " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("✅ [服务端] 新连接来自: " + clientSocket.getRemoteSocketAddress());
                
                ClientHandler handler = new ClientHandler(clientSocket, this);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("❌ [服务端] 严重异常: " + e.getMessage());
        }
    }

    public void broadcast(Message msg, String targetRoom) {
        String json = null;
        try { json = mapper.writeValueAsString(msg); } catch (Exception e) { return; }

        Room room = rooms.get(targetRoom);
        if (room == null) return;

        // Bug7: 公共聊天室也通过 members 列表过滤，只发给在该房间的用户
        for (ClientHandler client : clients) {
            if (client.getUsername() != null && room.getMembers().contains(client.getUsername())) {
                client.sendMessage(json);
            }
        }
    }

    public void safeLog(Message msg, String ip) {
        if (logger != null) {
            try {
                logger.log(msg, ip);
            } catch (Exception e) {
                // 忽略日志错误
            }
        }
    }

    // 【修改】增加了命名规范校验
    public String createRoom(String name, String pwd) {
        // 1. 校验房间名格式
        if (name == null || !ROOM_NAME_PATTERN.matcher(name).matches()) {
            return "房间名无效(1-20位字母/数字/中文)";
        }
        // 2. 校验密码格式 (如果有密码)
        if (pwd != null && (pwd.length() < 1 || pwd.length() > 20)) {
            return "密码长度必须在1-20之间";
        }
        // 3. 检查重复
        if (rooms.containsKey(name)) return "房间已存在";
        
        rooms.put(name, new Room(name, pwd));
        return "创建成功";
    }

    public String joinRoom(String user, String roomName, String pwd) {
        Room room = rooms.get(roomName);
        if (room == null) return "房间不存在";

        // Bug3: 检查人数上限和加入成员必须原子执行，否则并发时上限失效
        synchronized (room) {
            if (room.getMembers().size() >= 10) {
                return "房间已满 (上限10人)";
            }
            if (room.hasPassword() && !room.getPassword().equals(pwd)) return "密码错误";
            room.addMember(user);
        }
        return "加入成功";
    }

    public void removeClient(ClientHandler handler) {
        clients.remove(handler);
        // 可选：用户断开时将其从所有房间移除，防止占用名额
        for (Room room : rooms.values()) {
            room.getMembers().remove(handler.getUsername());
        }
        String who = handler.getUsername() != null ? handler.getUsername() : "(未注册连接)";
        System.out.println("⚠️ [服务端] 用户 " + who + " 已断开");
    }

    public static void main(String[] args) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        new ChatServer().start();
    }
}