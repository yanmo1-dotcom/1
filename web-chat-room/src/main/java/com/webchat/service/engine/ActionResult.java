package com.webchat.service.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * 引擎动作结果。RoomService 据此决定广播与结算。
 */
public class ActionResult {
    public boolean success = false;       // 动作是否合法接受
    public String message = "";           // 提示消息（回执/广播）
    public boolean finished = false;      // 是否对局结束
    public boolean draw = false;          // 是否平局
    public Long winnerId = null;          // 胜者（平局/未结束为 null）
    public String event = "action";       // 广播事件类型
    public Map<String, Object> extra = new HashMap<>(); // 附加广播数据（如双方出拳、提示大小）
    public boolean scheduleTimeout = false; // 是否需要为下一阶段重新设定超时
    public long nextActionDeadline = 0;   // 下一阶段出招截止时间（scheduleTimeout=true 时用）

    public ActionResult ok(String msg) { this.success = true; this.message = msg; return this; }
    public ActionResult fail(String msg) { this.success = false; this.message = msg; return this; }
}
