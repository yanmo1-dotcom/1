package com.chatroom.server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class Room {
    private final String name;
    private final String password; // 必须有这个字段
    private final List<String> members;

    public Room(String name, String password) {
        this.name = name;
        this.password = password;
        this.members = new CopyOnWriteArrayList<>();
    }

    public String getName() { return name; }
    public String getPassword() { return password; }
    public List<String> getMembers() { return members; }

    public boolean hasPassword() {
        return password != null && !password.isEmpty();
    }

    public void addMember(String username) {
        if (!members.contains(username)) members.add(username);
    }
}