package com.kapai.data.dto;

import com.kapai.core.effect.BlockEffect;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class BlockEffectDto extends CardEffectDto {

    private int amount;

    @Override
    public com.kapai.core.effect.CardEffect toEffect() {
        return new BlockEffect(amount);
    }
}
