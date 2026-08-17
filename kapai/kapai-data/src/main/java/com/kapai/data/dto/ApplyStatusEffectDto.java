package com.kapai.data.dto;

import com.kapai.core.effect.ApplyStatusEffect;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class ApplyStatusEffectDto extends CardEffectDto {

    private String statusId;
    private int amount;
    private boolean toSelf;

    @Override
    public com.kapai.core.effect.CardEffect toEffect() {
        return new ApplyStatusEffect(statusId, amount, toSelf);
    }
}
