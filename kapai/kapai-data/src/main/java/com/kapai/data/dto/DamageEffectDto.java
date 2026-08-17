package com.kapai.data.dto;

import com.kapai.core.effect.DamageEffect;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class DamageEffectDto extends CardEffectDto {

    private int amount;

    @Override
    public com.kapai.core.effect.CardEffect toEffect() {
        return new DamageEffect(amount);
    }
}
