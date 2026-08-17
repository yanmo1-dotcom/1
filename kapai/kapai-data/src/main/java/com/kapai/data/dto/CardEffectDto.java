package com.kapai.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 * 卡牌效果 DTO。使用 Jackson 多态注解按 "type" 字段路由到具体子类。
 *
 * 设计思路：JSON 中每个效果形如 {"type":"DAMAGE","amount":6}，
 * Jackson 读取 type 后实例化对应子类并填充其余字段。新增效果只需在此注册
 * 一个 JsonSubTypes 条目并新建对应 DTO，解析逻辑无需改动。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = DamageEffectDto.class, name = "DAMAGE"),
        @JsonSubTypes.Type(value = BlockEffectDto.class, name = "BLOCK"),
        @JsonSubTypes.Type(value = DrawEffectDto.class, name = "DRAW"),
        @JsonSubTypes.Type(value = ApplyStatusEffectDto.class, name = "APPLY_STATUS")
})
public abstract class CardEffectDto {

    protected String type;

    /** 由 DTO 转换为 core 层的效果实例。 */
    public abstract com.kapai.core.effect.CardEffect toEffect();
}
