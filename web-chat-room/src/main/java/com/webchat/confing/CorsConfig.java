package com.webchat.confing;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        
        // 【关键】允许所有来源（包括 localhost:56780）
        config.addAllowedOriginPattern("*"); 
        
        // 【核心】允许携带凭证 (Cookie)
        config.setAllowCredentials(true); 
        
        // 允许所有请求头和方法
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        
        // 【新增】显式暴露 Set-Cookie 头，防止跨域时被浏览器忽略
        config.addExposedHeader("Set-Cookie");
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有 /api/** 路径生效
        source.registerCorsConfiguration("/api/**", config);
        
        return new CorsFilter(source);
    }
}