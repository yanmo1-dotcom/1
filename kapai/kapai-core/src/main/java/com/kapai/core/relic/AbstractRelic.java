package com.kapai.core.relic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 遗物基类。同时实现 {@link RelicListener}——遗物既是数据也是观察者。
 *
 * 设计思路：counter 用于带计数器的遗物（如"每打出 10 张攻击牌获得 1 能量"），
 * 由各钩子自增并判定触发。具体遗物继承本类并重写需要的钩子。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public abstract class AbstractRelic implements RelicListener {

    protected String id;
    protected String name;
    protected String description;
    /** 计数器，初始 0。 */
    protected int counter;

    public AbstractRelic(String id, String name, String description) {
        this(id, name, description, 0);
    }
}
