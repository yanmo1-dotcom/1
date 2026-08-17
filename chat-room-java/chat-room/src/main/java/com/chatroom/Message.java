package com.chatroom;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private String type;
    private String sender;
    private String content;
    private String target;

    public static Message of(String type,String content){
        return new Message(type,null,content,null);
    }
    public static Message system(String content) {
        // 使用全参构造器创建，type设为"system"，sender设为"Server"
        return new Message("system", "Server", content, null);
    }
}