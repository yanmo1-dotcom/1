package com.kapai.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.kapai.core.card.AbstractCard;
import com.kapai.core.card.DataCard;
import com.kapai.core.effect.CardEffect;
import com.kapai.core.enums.CardRarity;
import com.kapai.core.enums.CardTarget;
import com.kapai.core.enums.CardType;
import com.kapai.data.dto.CardDto;
import com.kapai.data.dto.CardEffectDto;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 卡牌数据库：从 cards.json 加载卡牌配置并构建为 {@link DataCard} 实例。
 *
 * 设计思路：
 * - 单例式懒加载，进程内只解析一次 JSON，运行时通过 id 取牌（取牌返回的是同一模板实例，
 *   实际战斗应由调用方自行 clone/复制；这里提供 createCopy 辅助）。
 * - 解析采用 Jackson 多态 + DTO 转换双层：先反序列化为 DTO（容错、忽略未知字段），
 *   再转为 core 层不可变效果对象，使 core 不依赖 Jackson。
 * - 健壮性：单张卡牌解析失败不中断整体加载，记录 WARN 后跳过，保证其余卡牌可用。
 */
@Slf4j
public class CardDatabase {

    private static final String DEFAULT_RESOURCE = "/cards.json";

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    @Getter
    private final Map<String, AbstractCard> cards = new HashMap<>();

    private volatile boolean loaded = false;

    /** 从默认 classpath 资源加载。 */
    public synchronized void load() throws CardLoadException {
        loadFromResource(DEFAULT_RESOURCE);
    }

    /** 从 classpath 指定资源路径加载。 */
    public synchronized void loadFromResource(String resourcePath) throws CardLoadException {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new CardLoadException("卡牌资源未找到: " + resourcePath);
            }
            loadFromStream(in);
        } catch (IOException e) {
            throw new CardLoadException("读取卡牌资源失败: " + resourcePath, e);
        }
    }

    /** 从任意输入流加载，便于测试与自定义来源。 */
    public synchronized void loadFromStream(InputStream in) throws CardLoadException {
        CardListWrapper wrapper;
        try {
            wrapper = mapper.readValue(in, CardListWrapper.class);
        } catch (IOException e) {
            throw new CardLoadException("JSON 解析失败", e);
        }
        cards.clear();
        if (wrapper == null || wrapper.getCards() == null) {
            log.warn("卡牌列表为空");
            loaded = true;
            return;
        }
        int ok = 0, fail = 0;
        for (CardDto dto : wrapper.getCards()) {
            try {
                AbstractCard card = toCard(dto);
                if (cards.putIfAbsent(card.getId(), card) != null) {
                    log.warn("重复卡牌 id 已忽略: {}", card.getId());
                    fail++;
                } else {
                    ok++;
                }
            } catch (Exception e) {
                // 单张失败不阻断整体加载
                log.warn("卡牌解析失败，已跳过: id={}, reason={}", dto.getId(), e.getMessage());
                fail++;
            }
        }
        loaded = true;
        log.info("卡牌数据库加载完成：成功 {} 张，失败 {} 张", ok, fail);
    }

    /** DTO → core 卡牌。effects 非空用 effects；否则用扁平 damage/block 兜底生成效果。 */
    private AbstractCard toCard(CardDto dto) {
        CardRarity rarity = parseEnum(dto.getRarity(), CardRarity.class, "卡牌稀有度");
        CardType type = parseEnum(dto.getType(), CardType.class, "卡牌类型");
        CardTarget target = parseEnum(dto.getTarget(), CardTarget.class, "卡牌目标");

        List<CardEffect> effects = new ArrayList<>();
        if (dto.getEffects() != null && !dto.getEffects().isEmpty()) {
            for (CardEffectDto edto : dto.getEffects()) {
                effects.add(edto.toEffect());
            }
        } else {
            // 扁平字段兜底：单效果卡牌的简写写法
            if (dto.getDamage() != null) {
                effects.add(new com.kapai.core.effect.DamageEffect(dto.getDamage()));
            }
            if (dto.getBlock() != null) {
                effects.add(new com.kapai.core.effect.BlockEffect(dto.getBlock()));
            }
        }
        return new DataCard(dto.getId(), dto.getName(), dto.getCost(), rarity, type, target, effects);
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumType, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " 为空");
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(label + " 非法值: " + value);
        }
    }

    /** 按 id 取卡牌模板实例。 */
    public Optional<AbstractCard> get(String id) {
        ensureLoaded();
        return Optional.ofNullable(cards.get(id));
    }

    /** 按 id 创建一份副本（战斗用），副本拥有独立 effects 列表引用模板效果。 */
    public Optional<AbstractCard> createCopy(String id) {
        ensureLoaded();
        AbstractCard template = cards.get(id);
        if (template == null) {
            return Optional.empty();
        }
        // DataCard 的 effects 为同一策略实例（无状态可复用），故直接复制引用
        return Optional.of(new DataCard(
                template.getId(), template.getName(), template.getCost(),
                template.getRarity(), template.getType(), template.getTarget(),
                new ArrayList<>(template.getEffects())));
    }

    /** 全部卡牌（不可变视图）。 */
    public List<AbstractCard> all() {
        ensureLoaded();
        return Collections.unmodifiableList(new ArrayList<>(cards.values()));
    }

    private void ensureLoaded() {
        if (!loaded) {
            throw new IllegalStateException("CardDatabase 尚未加载，请先调用 load()");
        }
    }

    /** JSON 顶层包装。 */
    private static class CardListWrapper {
        @com.fasterxml.jackson.annotation.JsonProperty("cards")
        private List<CardDto> cards;

        public List<CardDto> getCards() {
            return cards;
        }

        public void setCards(List<CardDto> cards) {
            this.cards = cards;
        }
    }
}
