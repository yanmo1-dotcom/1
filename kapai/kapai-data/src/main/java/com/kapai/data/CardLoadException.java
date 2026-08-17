package com.kapai.data;

/**
 * 卡牌数据加载异常。封装 JSON 解析、IO、资源缺失等错误，供调用方统一捕获处理。
 */
public class CardLoadException extends Exception {

    public CardLoadException(String message) {
        super(message);
    }

    public CardLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
