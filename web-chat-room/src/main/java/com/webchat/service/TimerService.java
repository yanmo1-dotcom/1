package com.webchat.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 应用级定时器：驱动房间锁定倒计时与出招超时。
 * 用单线程调度器，任务自身处理并发安全。
 */
@Component
public class TimerService {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "game-timer");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<String, ScheduledFuture<?>> taskMap = new ConcurrentHashMap<>();

    /** 延迟 delayMs 毫秒后执行一次任务，关联 key 以便取消。 */
    public void schedule(String key, long delayMs, Runnable task) {
        cancel(key);
        ScheduledFuture<?> f = scheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        taskMap.put(key, f);
    }

    public void cancel(String key) {
        ScheduledFuture<?> f = taskMap.remove(key);
        if (f != null) f.cancel(false);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
