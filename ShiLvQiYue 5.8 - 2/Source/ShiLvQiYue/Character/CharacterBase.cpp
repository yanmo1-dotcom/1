// 时律契约 - 角色基类实现
#include "Character/CharacterBase.h"
#include "Character/PassionComponent.h"
#include "Core/BattleManager.h"
#include "Data/CardDataAsset.h"

ACharacterBase::ACharacterBase()
{
	PrimaryActorTick.bCanEverTick = false;

	// 创建激情组件
	PassionComponent = CreateDefaultSubobject<UPassionComponent>(TEXT("PassionComponent"));
}

void ACharacterBase::BeginPlay()
{
	Super::BeginPlay();
}

void ACharacterBase::InitializeCharacter(const FBaseAttributes& InAttributes, EAttributeType InAttribute, ECharacterRole InRole)
{
	Attributes = InAttributes;
	Attributes.CurrentHP = Attributes.MaxHP;
	CharacterAttribute = InAttribute;
	CharacterRole = InRole;
	bIsDead = false;

	if (PassionComponent)
	{
		PassionComponent->InitializePassion(5);
	}

	UE_LOG(LogCharacter, Log, TEXT("角色初始化: %s, 属性: %d, 定位: %d, HP: %.0f"), 
		*CharacterName.ToString(), (int32)CharacterAttribute, (int32)CharacterRole, Attributes.MaxHP);
}

float ACharacterBase::TakeDamage(float DamageAmount, ACharacterBase* DamageSource)
{
	if (bIsDead || DamageAmount <= 0)
	{
		return 0.0f;
	}

	// 计算实际伤害
	float ActualDamage = CalculateIncomingDamage(DamageAmount);

	// 应用护盾
	ActualDamage = ApplyShield(ActualDamage);

	// 扣血
	Attributes.CurrentHP = FMath::Max(0.0f, Attributes.CurrentHP - ActualDamage);

	UE_LOG(LogCharacter, Log, TEXT("%s 受到伤害: %.0f (实际: %.0f), 剩余HP: %.0f"), 
		*CharacterName.ToString(), DamageAmount, ActualDamage, Attributes.CurrentHP);

	OnCharacterDamaged.Broadcast(this, ActualDamage);

	// 检查死亡
	if (Attributes.CurrentHP <= 0 && !bIsDead)
	{
		Die();
	}

	return ActualDamage;
}

float ACharacterBase::Heal(float HealingAmount)
{
	if (bIsDead || HealingAmount <= 0)
	{
		return 0.0f;
	}

	float OldHP = Attributes.CurrentHP;
	Attributes.CurrentHP = FMath::Min(Attributes.MaxHP, Attributes.CurrentHP + HealingAmount);
	float ActualHealing = Attributes.CurrentHP - OldHP;

	UE_LOG(LogCharacter, Log, TEXT("%s 受到治疗: %.0f (实际: %.0f), 当前HP: %.0f"), 
		*CharacterName.ToString(), HealingAmount, ActualHealing, Attributes.CurrentHP);

	OnCharacterHealed.Broadcast(this, ActualHealing);
	return ActualHealing;
}

void ACharacterBase::Die()
{
	bIsDead = true;
	Attributes.CurrentHP = 0;

	UE_LOG(LogCharacter, Log, TEXT("角色死亡: %s"), *CharacterName.ToString());

	OnCharacterDied.Broadcast(this);
}

void ACharacterBase::Revive(float HealthPercent)
{
	bIsDead = false;
	Attributes.CurrentHP = Attributes.MaxHP * HealthPercent;

	UE_LOG(LogCharacter, Log, TEXT("角色复活: %s, HP恢复至: %.0f (%.0f%%)"), 
		*CharacterName.ToString(), Attributes.CurrentHP, HealthPercent * 100);
}

void ACharacterBase::AddStatusEffect(const FStatusEffectInstance& Effect)
{
	// 检查是否已有同类效果，有则刷新
	for (FStatusEffectInstance& Existing : ActiveStatusEffects)
	{
		if (Existing.EffectType == Effect.EffectType)
		{
			Existing.EffectValue = FMath::Max(Existing.EffectValue, Effect.EffectValue);
			Existing.RemainingTurns = FMath::Max(Existing.RemainingTurns, Effect.RemainingTurns);
			Existing.Source = Effect.Source;
			UE_LOG(LogCharacter, Log, TEXT("%s 刷新状态效果: %d, 持续回合: %d"), 
				*CharacterName.ToString(), (int32)Effect.EffectType, Existing.RemainingTurns);
			return;
		}
	}

	ActiveStatusEffects.Add(Effect);
	UE_LOG(LogCharacter, Log, TEXT("%s 获得状态效果: %d, 数值: %.0f, 持续回合: %d"), 
		*CharacterName.ToString(), (int32)Effect.EffectType, Effect.EffectValue, Effect.RemainingTurns);
}

void ACharacterBase::RemoveStatusEffect(EStatusEffectType EffectType)
{
	ActiveStatusEffects.RemoveAll([EffectType](const FStatusEffectInstance& Effect) {
		return Effect.EffectType == EffectType;
	});
}

bool ACharacterBase::HasStatusEffect(EStatusEffectType EffectType) const
{
	for (const FStatusEffectInstance& Effect : ActiveStatusEffects)
	{
		if (Effect.EffectType == EffectType)
		{
			return true;
		}
	}
	return false;
}

void ACharacterBase::ProcessStatusEffectsAtTurnEnd()
{
	if (bIsDead) return;

	// 处理持续伤害/治疗
	for (int32 i = ActiveStatusEffects.Num() - 1; i >= 0; i--)
	{
		FStatusEffectInstance& Effect = ActiveStatusEffects[i];

		switch (Effect.EffectType)
		{
		case EStatusEffectType::Poison:
		case EStatusEffectType::Burn:
			TakeDamage(Effect.EffectValue, nullptr);
			break;
		case EStatusEffectType::Regeneration:
			Heal(Effect.EffectValue);
			break;
		default:
			break;
		}

		// 减少持续回合
		Effect.RemainingTurns--;
		if (Effect.RemainingTurns <= 0)
		{
			UE_LOG(LogCharacter, Log, TEXT("%s 状态效果结束: %d"), 
				*CharacterName.ToString(), (int32)Effect.EffectType);
			ActiveStatusEffects.RemoveAt(i);
		}
	}
}

int32 ACharacterBase::AddPassion(int32 Amount)
{
	if (PassionComponent)
	{
		return PassionComponent->AddPassion(Amount);
	}
	return 0;
}

int32 ACharacterBase::GetCurrentPassion() const
{
	if (PassionComponent)
	{
		return PassionComponent->GetCurrentPassion();
	}
	return 0;
}

float ACharacterBase::ApplyShield(float DamageAmount)
{
	float RemainingDamage = DamageAmount;

	// 查找护盾效果
	for (int32 i = ActiveStatusEffects.Num() - 1; i >= 0; i--)
	{
		FStatusEffectInstance& Effect = ActiveStatusEffects[i];
		if (Effect.EffectType == EStatusEffectType::Shield && Effect.EffectValue > 0)
		{
			float Absorbed = FMath::Min(Effect.EffectValue, RemainingDamage);
			Effect.EffectValue -= Absorbed;
			RemainingDamage -= Absorbed;

			UE_LOG(LogCharacter, Log, TEXT("%s 护盾吸收伤害: %.0f, 剩余护盾: %.0f"), 
				*CharacterName.ToString(), Absorbed, Effect.EffectValue);

			// 护盾耗尽则移除
			if (Effect.EffectValue <= 0)
			{
				ActiveStatusEffects.RemoveAt(i);
			}
			break;
		}
	}

	return RemainingDamage;
}

float ACharacterBase::CalculateIncomingDamage(float BaseDamage)
{
	float Damage = BaseDamage;

	// 防御降低效果（增加受到伤害）
	for (const FStatusEffectInstance& Effect : ActiveStatusEffects)
	{
		if (Effect.EffectType == EStatusEffectType::DefenseDown)
		{
			Damage *= (1.0f + Effect.EffectValue / 100.0f);
		}
	}

	// 创伤加成（增加受到伤害）
	Damage *= (1.0f + Attributes.DamageReduction);

	return FMath::Max(0.0f, Damage);
}
