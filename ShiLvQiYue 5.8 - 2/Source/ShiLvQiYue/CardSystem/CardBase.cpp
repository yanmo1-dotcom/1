// 时律契约 - 卡牌基类实现
#include "CardSystem/CardBase.h"
#include "Character/CharacterBase.h"
#include "Core/BattleManager.h"
#include "Data/AttributeData.h"

UCardBase::UCardBase()
{
	CardData = FCardRuntimeData();
}

void UCardBase::InitializeCard(const FCardRuntimeData& InCardData, ACharacterBase* InOwner)
{
	CardData = InCardData;
	OwnerCharacter = InOwner;
	CardData.CurrentState = ECardState::InDeck;

	UE_LOG(LogCard, Log, TEXT("卡牌初始化: %s, 阶位: %d"), 
		*CardData.CardName.ToString(), (int32)CardData.CardRank);
}

bool UCardBase::PlayCard(ACharacterBase* TargetCharacter)
{
	if (!CanPlayCard())
	{
		UE_LOG(LogCard, Warning, TEXT("卡牌无法打出: %s"), *CardData.CardName.ToString());
		return false;
	}

	UE_LOG(LogCard, Log, TEXT("打出卡牌: %s, 目标: %s"), 
		*CardData.CardName.ToString(), 
		TargetCharacter ? *TargetCharacter->GetCharacterName().ToString() : TEXT("无"));

	// 执行效果
	ExecuteEffects(TargetCharacter);

	// 更新状态
	CardData.CurrentState = ECardState::Played;
	CardData.HandSlotIndex = -1;

	// 触发事件
	OnCardPlayed.Broadcast(this, TargetCharacter);

	return true;
}

bool UCardBase::CanPlayCard() const
{
	if (CardData.CurrentState != ECardState::InHand && CardData.CurrentState != ECardState::Selected)
	{
		return false;
	}

	if (!OwnerCharacter.IsValid() || OwnerCharacter->IsDead())
	{
		return false;
	}

	return true;
}

void UCardBase::SetCardState(ECardState NewState)
{
	CardData.CurrentState = NewState;
}

void UCardBase::SetHandSlotIndex(int32 NewIndex)
{
	CardData.HandSlotIndex = NewIndex;
}

void UCardBase::ExecuteEffects(ACharacterBase* TargetCharacter)
{
	if (!OwnerCharacter.IsValid() || !TargetCharacter)
	{
		return;
	}

	for (const FCardEffect& Effect : CardData.Effects)
	{
		// 检查触发概率
		if (FMath::FRand() > Effect.TriggerChance)
		{
			continue;
		}

		switch (Effect.EffectType)
		{
		case ECardType::Attack:
		{
			float Damage = CalculateDamage(TargetCharacter, Effect.BaseValue);
			TargetCharacter->TakeDamage(Damage, OwnerCharacter.Get());
			break;
		}
		case ECardType::Heal:
		{
			float Healing = CalculateHealing(Effect.BaseValue);
			TargetCharacter->Heal(Healing);
			break;
		}
		case ECardType::Buff:
		case ECardType::Debuff:
		{
			if (Effect.StatusEffectType != EStatusEffectType::None)
			{
				FStatusEffectInstance StatusEffect;
				StatusEffect.EffectType = Effect.StatusEffectType;
				StatusEffect.EffectValue = Effect.StatusEffectValue;
				StatusEffect.RemainingTurns = Effect.StatusEffectDuration;
				StatusEffect.Source = OwnerCharacter.Get();
				StatusEffect.bIsBuff = (Effect.EffectType == ECardType::Buff);
				TargetCharacter->AddStatusEffect(StatusEffect);
			}
			break;
		}
		default:
			break;
		}
	}
}

float UCardBase::CalculateDamage(ACharacterBase* TargetCharacter, float BaseValue)
{
	if (!OwnerCharacter.IsValid() || !TargetCharacter)
	{
		return 0.0f;
	}

	const FBaseAttributes& AttackerAttrs = OwnerCharacter->GetAttributes();
	const FBaseAttributes& DefenderAttrs = TargetCharacter->GetAttributes();

	// 基础伤害 = 基础值 × 阶位倍率 × (攻击力 / 100)
	float Damage = BaseValue * CardData.GetRankMultiplier() * (AttackerAttrs.AttackPower / 100.0f);

	// 属性克制
	if (CardData.CardAttribute != EAttributeType::None && 
		TargetCharacter->GetWeakAttribute() == CardData.CardAttribute)
	{
		Damage *= 1.3f; // 克制+30%
	}

	// 伤害加成
	Damage *= (1.0f + AttackerAttrs.DamageBonus);

	// 暴击
	bool bIsCrit = FMath::FRand() < AttackerAttrs.CritRate;
	if (bIsCrit)
	{
		Damage *= AttackerAttrs.CritDamage;
	}

	// 防御减伤（考虑穿透率）
	float EffectiveDefense = DefenderAttrs.DefensePower * (1.0f - AttackerAttrs.PenetrationRate);
	float DamageReduction = EffectiveDefense / (EffectiveDefense + 100.0f);
	Damage *= (1.0f - DamageReduction);

	// 受伤减免
	Damage *= (1.0f - DefenderAttrs.DamageReduction);

	return FMath::Max(0.0f, Damage);
}

float UCardBase::CalculateHealing(float BaseValue)
{
	if (!OwnerCharacter.IsValid())
	{
		return 0.0f;
	}

	const FBaseAttributes& HealerAttrs = OwnerCharacter->GetAttributes();
	float Healing = BaseValue * CardData.GetRankMultiplier() * (1.0f + HealerAttrs.HealingBonus);

	return FMath::Max(0.0f, Healing);
}
