// 时律契约 - 敌人角色实现
#include "Character/EnemyCharacter.h"
#include "Core/BattleManager.h"

AEnemyCharacter::AEnemyCharacter()
{
	Faction = EFaction::Enemy;
}

void AEnemyCharacter::InitializeEnemy(const FBaseAttributes& InAttributes, EAttributeType InAttribute, EAttributeType InWeakAttribute)
{
	InitializeCharacter(InAttributes, InAttribute, ECharacterRole::MainDPS);
	WeakAttribute = InWeakAttribute;
	CurrentCharge = 0;
}

void AEnemyCharacter::ExecuteAIAction()
{
	if (bIsDead) return;

	EEnemyAIBahavior NextAction = GetNextAIAction();

	UE_LOG(LogCharacter, Log, TEXT("敌人 %s 执行AI行动: %d"), *CharacterName.ToString(), (int32)NextAction);

	switch (NextAction)
	{
	case EEnemyAIBahavior::NormalAttack:
		ExecuteNormalAttack();
		AddCharge(1);
		break;
	case EEnemyAIBahavior::Charge:
		AddCharge(1);
		break;
	case EEnemyAIBahavior::Skill:
		if (IsChargeFull())
		{
			ExecuteChargeSkill();
			ResetCharge();
		}
		else
		{
			ExecuteNormalAttack();
			AddCharge(1);
		}
		break;
	default:
		ExecuteNormalAttack();
		AddCharge(1);
		break;
	}
}

void AEnemyCharacter::AddCharge(int32 Amount)
{
	CurrentCharge = FMath::Min(CurrentCharge + Amount, MaxCharge);
	UE_LOG(LogCharacter, Log, TEXT("敌人 %s 蓄力增加: %d/%d"), 
		*CharacterName.ToString(), CurrentCharge, MaxCharge);
}

void AEnemyCharacter::ResetCharge()
{
	CurrentCharge = 0;
}

EEnemyAIBahavior AEnemyCharacter::GetNextAIAction() const
{
	// 蓄力满则释放技能
	if (IsChargeFull())
	{
		return EEnemyAIBahavior::Skill;
	}

	// 简单AI：70%普通攻击，30%蓄力
	if (FMath::FRand() < 0.7f)
	{
		return EEnemyAIBahavior::NormalAttack;
	}
	return EEnemyAIBahavior::Charge;
}

void AEnemyCharacter::ExecuteNormalAttack()
{
	ACharacterBase* Target = SelectAttackTarget();
	if (Target)
	{
		UE_LOG(LogCharacter, Log, TEXT("敌人 %s 普通攻击 %s，伤害: %.0f"), 
			*CharacterName.ToString(), *Target->GetCharacterName().ToString(), NormalAttackDamage);
		Target->TakeDamage(NormalAttackDamage, this);
	}
}

void AEnemyCharacter::ExecuteChargeSkill()
{
	ACharacterBase* Target = SelectAttackTarget();
	if (Target)
	{
		UE_LOG(LogCharacter, Log, TEXT("敌人 %s 释放蓄力技能攻击 %s，伤害: %.0f"), 
			*CharacterName.ToString(), *Target->GetCharacterName().ToString(), ChargeSkillDamage);
		Target->TakeDamage(ChargeSkillDamage, this);
	}
}

ACharacterBase* AEnemyCharacter::SelectAttackTarget() const
{
	// 简单AI：选择HP最低的玩家角色
	if (!BattleManager.IsValid()) return nullptr;

	const TArray<ACharacterBase*>& PlayerCharacters = BattleManager->GetPlayerCharacters();
	ACharacterBase* Target = nullptr;
	float LowestHP = FLT_MAX;

	for (ACharacterBase* Character : PlayerCharacters)
	{
		if (Character && !Character->IsDead() && Character->GetCurrentHP() < LowestHP)
		{
			LowestHP = Character->GetCurrentHP();
			Target = Character;
		}
	}

	return Target;
}
