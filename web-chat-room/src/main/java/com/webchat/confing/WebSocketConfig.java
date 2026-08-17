package com.webchat.confing;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 1. 启用简单消息代理，客户端订阅 /topic 和 /queue 开头的消息
        config.enableSimpleBroker("/topic", "/queue");

        // 2. 【核心】设置用户专属消息的前缀为 /user
        // 这样后端发给 /user/{id}/queue/reply 的消息，浏览器只需订阅 /queue/reply
        config.setUserDestinationPrefix("/user");

        // 3. 后端接收消息的前缀
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 4. 注册 WebSocket 端点，允许跨域，并开启 SockJS 兼容
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                // 【关键修复】握手拦截器：从 URL 查询参数提取 userId，放入 session attributes，
                // 供后续 STOMP CONNECT 时设置 Principal，使 convertAndSendToUser 能路由到客户端
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        String query = request.getURI().getQuery();
                        if (query != null) {
                            for (String param : query.split("&")) {
                                String[] kv = param.split("=", 2);
                                if (kv.length != 2) continue;
                                if ("userId".equals(kv[0])) {
                                    attributes.put("userId", kv[1]);
                                } else if ("loginTime".equals(kv[0])) {
                                    attributes.put("loginTime", kv[1]);
                                }
                            }
                        }
                        return true;
                    }

                    @Override
                    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception exception) {
                    }
                })
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 【关键修复】在 STOMP CONNECT 帧到达时，把握手阶段存的 userId 设为 Principal，
        // 这样 SimpMessagingTemplate#convertAndSendToUser(userId, ...) 才能精确路由到该客户端
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    Map<String, Object> attrs = accessor.getSessionAttributes();
                    if (attrs != null && attrs.get("userId") != null) {
                        String userId = attrs.get("userId").toString();
                        accessor.setUser(new Principal() {
                            @Override
                            public String getName() { return userId; }
                        });
                    }
                }
                return message;
            }
        });
    }
}
