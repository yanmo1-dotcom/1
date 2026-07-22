package com.chatroom.server;

import com.chatroom.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private final ObjectMapper mapper = new ObjectMapper();
    
    private BufferedReader in;
    // 【修改1】改用 BufferedWriter，以便真正捕获 IOException
    private BufferedWriter out; 
    
    private String username;
    private String currentRoom = "public";
    private final Set<String> friends = ConcurrentHashMap.newKeySet();
    private volatile boolean closed = false;
    // Bug1: 用 AtomicBoolean 保证 close() 的清理逻辑只执行一次
    private final AtomicBoolean cleanupDone = new AtomicBoolean(false);

    // Bug9: key 改为 roomName:username，防止一个用户失败导致整个房间被锁
    private static final Map<String, AtomicInteger> PWD_FAIL_COUNTS = new ConcurrentHashMap<>();
    private static final int MAX_PWD_FAILS = 3;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            // 【修改2】初始化 BufferedWriter
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            sendMessage("{\"type\":\"system\",\"content\":\"请输入昵称:\"}");
            
            String line;
            boolean registered = false;

            while ((line = in.readLine()) != null) {
                Message msg;
                try {
                    msg = mapper.readValue(line, Message.class);
                } catch (Exception e) {
                    sendMessage("{\"type\":\"error\",\"content\":\"消息格式错误\"}");
                    continue;
                }

                // 异步记录日志 (满足需求五-1: 全量日志保存)
                final Message logMsg = msg;
                final String clientIp = socket.getRemoteSocketAddress().toString();
                CompletableFuture.runAsync(() -> server.safeLog(logMsg, clientIp));

                String content = msg.getContent();
                if (content == null) content = "";

                // 1. 注册阶段
                if (!registered) {
                    String trimmed = content.trim();
                    if (trimmed.isEmpty() || trimmed.length() > 20) {
                        sendMessage(mapper.writeValueAsString(new Message("error", "Server", "昵称不能为空且不能超过20字符", null)));
                    } else {
                        // 检查+赋值必须原子，防止并发注册相同昵称
                        synchronized (server.clients) {
                            boolean nameTaken = server.clients.stream()
                                .anyMatch(c -> trimmed.equals(c.getUsername()));
                            if (nameTaken) {
                                sendMessage(mapper.writeValueAsString(new Message("error", "Server", "昵称已被占用，请换一个", null)));
                            } else {
                                this.username = trimmed;
                                registered = true;
                                server.rooms.get("public").addMember(username);
                                server.broadcast(Message.system(username + " 加入了聊天室"), "public");
                                sendMessage(mapper.writeValueAsString(new Message("system", "Server", "欢迎 " + username + "! 输入 /help 查看指令", null)));
                            }
                        }
                    }
                    continue;
                }

                // 2. 指令解析 【包含 /list, /room list, /leave】
                if ("/help".equals(content)) {
                    handleHelp();
                } else if ("/list".equals(content)) {
                    handleListUsers();
                } else if ("/room list".equals(content)) {
                    handleListRooms();
                } else if ("/leave".equals(content)) {
                    handleLeaveRoom();
                } else if (content.startsWith("/create ")) {
                    handleCreate(content.substring(8));
                } else if (content.startsWith("/join ")) {
                    handleJoin(content.substring(6));
                } else if (content.startsWith("/friend add ")) {
                    handleAddFriend(content.substring(12));
                } else if (content.startsWith("/whisper ")) {
                    handleWhisper(content.substring(9));
                } else if ("exit".equalsIgnoreCase(content)) {
                    break;
                } else {
                    // 3. 普通聊天
                    Message chatMsg = new Message("chat", username, content, currentRoom);
                    server.broadcast(chatMsg, currentRoom);
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ [Handler] 异常: " + e.getMessage());
        } finally {
            close();
        }
    }

    // --- 业务方法 ---

    private void handleHelp() throws Exception {
        String help = "可用指令:\n" +
                "  /list               — 查看在线用户\n" +
                "  /room list          — 查看房间列表\n" +
                "  /create <名> [密码] — 创建房间\n" +
                "  /join <名> [密码]   — 加入房间\n" +
                "  /leave              — 退出当前房间回到公共聊天室\n" +
                "  /friend add <用户>  — 添加好友\n" +
                "  /whisper <用户> <消息> — 私聊好友\n" +
                "  exit                — 退出";
        sendMessage(mapper.writeValueAsString(new Message("system", "Server", help, null)));
    }

    private void handleListUsers() throws Exception {
        StringBuilder sb = new StringBuilder("当前在线用户 (" + server.clients.size() + "人):\n");
        for (ClientHandler client : server.clients) {
            if (client.getUsername() != null) {
                sb.append("  - ").append(client.getUsername()).append("\n");
            }
        }
        // Bug5: 用 ObjectMapper 序列化，防止用户名含特殊字符破坏 JSON
        sendMessage(mapper.writeValueAsString(new Message("system", "Server", sb.toString().trim(), null)));
    }

    private void handleListRooms() throws Exception {
        StringBuilder sb = new StringBuilder("当前房间列表:\n");
        for (Map.Entry<String, Room> entry : server.rooms.entrySet()) {
            Room room = entry.getValue();
            String lock = room.hasPassword() ? "[锁]" : "[开]";
            sb.append("  - ").append(entry.getKey())
              .append(" (").append(room.getMembers().size()).append("人) ").append(lock).append("\n");
        }
        sendMessage(mapper.writeValueAsString(new Message("system", "Server", sb.toString().trim(), null)));
    }

    private void handleLeaveRoom() throws Exception {
        if ("public".equals(currentRoom)) {
            sendMessage(mapper.writeValueAsString(new Message("error", "Server", "不能退出公共聊天室", null)));
            return;
        }

        Room oldRoom = server.rooms.get(currentRoom);
        if (oldRoom != null) {
            oldRoom.getMembers().remove(username);
            server.broadcast(Message.system(username + " 离开了房间"), currentRoom);
        }

        this.currentRoom = "public";
        // 回到 public 房间，必须重新加入 members，否则收不到 public 广播
        server.rooms.get("public").addMember(username);
        sendMessage(mapper.writeValueAsString(new Message("system", "Server", "已退出房间，回到公共聊天室", null)));
    }

    private void handleCreate(String args) throws Exception {
        String[] parts = args.split(" ", 2);
        String roomName = parts[0];
        String pwd = parts.length > 1 ? parts[1] : null;

        String res = server.createRoom(roomName, pwd);
        sendMessage(mapper.writeValueAsString(new Message("system", "Server", "创建房间: " + res, null)));

        if ("创建成功".equals(res)) {
            String joinRes = server.joinRoom(username, roomName, pwd);
            if ("加入成功".equals(joinRes)) {
                // 创建后自动加入，也需要先从旧房间移除
                Room oldRoom = server.rooms.get(currentRoom);
                if (oldRoom != null) oldRoom.getMembers().remove(username);
                this.currentRoom = roomName;
                String failKey = roomName + ":" + username;
                PWD_FAIL_COUNTS.remove(failKey);
                sendMessage(mapper.writeValueAsString(new Message("system", "Server", "已自动进入房间 [" + roomName + "]", null)));
            } else {
                sendMessage(mapper.writeValueAsString(new Message("error", "Server", "创建成功但加入失败: " + joinRes, null)));
            }
        }
    }

    private void handleJoin(String args) throws Exception {
        String[] parts = args.split(" ", 2);
        String roomName = parts[0];
        String pwd = parts.length > 1 ? parts[1] : null;

        // Bug9: key 用 roomName:username，只锁当前用户，不影响其他人
        String failKey = roomName + ":" + username;
        AtomicInteger failCount = PWD_FAIL_COUNTS.get(failKey);
        if (failCount != null && failCount.get() >= MAX_PWD_FAILS) {
            sendMessage(mapper.writeValueAsString(new Message("error", "Server", "密码错误次数过多，请稍后再试", null)));
            return;
        }

        if (roomName.equals(currentRoom)) {
            sendMessage(mapper.writeValueAsString(new Message("system", "Server", "你已经在房间 [" + roomName + "] 中了", null)));
            return;
        }

        String res = server.joinRoom(username, roomName, pwd);

        if ("加入成功".equals(res)) {
            // 无论从哪个房间（含 public）加入新房间，都先移除
            Room oldRoom = server.rooms.get(currentRoom);
            if (oldRoom != null) oldRoom.getMembers().remove(username);
            PWD_FAIL_COUNTS.remove(failKey);
            this.currentRoom = roomName;
            sendMessage(mapper.writeValueAsString(new Message("system", "Server", "已进入房间 [" + roomName + "]", null)));
            server.broadcast(Message.system(username + " 进入了房间"), roomName);
        } else {
            if (res.contains("密码错误")) {
                PWD_FAIL_COUNTS.computeIfAbsent(failKey, k -> new AtomicInteger(0)).incrementAndGet();
            }
            sendMessage(mapper.writeValueAsString(new Message("error", "Server", "加入失败: " + res, null)));
        }
    }

    private void handleAddFriend(String target) throws Exception {
        if (target.equals(this.username)) {
            sendMessage(mapper.writeValueAsString(new Message("error", "Server", "不能加自己", null)));
            return;
        }
        boolean exists = server.clients.stream().anyMatch(c -> target.equals(c.getUsername()));
        if (!exists) {
            sendMessage(mapper.writeValueAsString(new Message("error", "Server", "用户 " + target + " 不存在或不在线", null)));
            return;
        }
        if (friends.contains(target)) {
            sendMessage(mapper.writeValueAsString(new Message("system", "Server", target + " 已经是你的好友了", null)));
            return;
        }
        friends.add(target);
        sendMessage(mapper.writeValueAsString(new Message("system", "Server", "已添加 " + target + " 为好友", null)));
    }

    private void handleWhisper(String args) throws Exception {
        String[] parts = args.split(" ", 2);
        if (parts.length < 2) {
            sendMessage(mapper.writeValueAsString(new Message("error", "Server", "用法: /whisper <用户> <消息>", null)));
            return;
        }

        String targetUser = parts[0].trim();
        String messageContent = parts[1].trim();

        if (!friends.contains(targetUser)) {
            sendMessage(mapper.writeValueAsString(new Message("error", "Server", "请先添加 " + targetUser + " 为好友才能私聊", null)));
            return;
        }

        ClientHandler targetHandler = null;
        for (ClientHandler h : server.clients) {
            if (targetUser.equals(h.getUsername())) {
                targetHandler = h;
                break;
            }
        }

        if (targetHandler == null) {
            sendMessage(mapper.writeValueAsString(new Message("error", "Server", "用户不在线", null)));
        } else {
            Message whisperMsg = new Message("whisper", username, messageContent, targetUser);
            String json = mapper.writeValueAsString(whisperMsg);
            targetHandler.sendMessage(json);
            sendMessage(json);
        }
    }

    // --- 工具方法 ---
    
    public void sendMessage(String json) {
        if (out != null && !closed) {
            try {
                out.write(json);
                out.newLine();
                out.flush();
            } catch (IOException e) {
                System.err.println("⚠️ [Handler] 向用户 " + username + " 发送消息失败: " + e.getMessage());
                // Bug1: 发送失败时直接触发完整清理，而不是只设 closed=true
                // 原来只设 closed=true 会导致 close() 的 if(closed)return 短路，
                // 使 removeClient 永远不被调用
                close();
            }
        }
    }

    public String getUsername() { return username; }

    public void close() {
        // Bug1: 用 AtomicBoolean CAS 保证清理逻辑只执行一次，且不会被 closed 标志短路
        if (!cleanupDone.compareAndSet(false, true)) return;
        closed = true;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        server.removeClient(this);
    }
}