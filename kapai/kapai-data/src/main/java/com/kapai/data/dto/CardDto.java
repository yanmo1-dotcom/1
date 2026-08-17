package com.kapai.data.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡牌 DTO。与 cards.json 结构一一对应。
 * 解析后由 {@link com.kapai.data.CardDatabase} 转换为 core 层 {@link com.kapai.core.card.DataCard}。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CardDto {

    private String id;
    private String name;
    private int cost;
    private String rarity;   // 对应 CardRarity 枚举名
    private String type;     // 对应 CardType 枚举名
    private String target;   // 对应 CardTarget 枚举名

    /** 卡牌描述文本，纯展示用。 */
    private String description;

    /** 扁平伤害值；当 effects 为空时用于生成 DamageEffect（便于单效果卡牌简写）。 */
    private Integer damage;

    /** 扁人格挡值；当 effects 为空时用于生成 BlockEffect。 */
    private Integer block;

    private List<CardEffectDto> effects = new ArrayList<>();
}
