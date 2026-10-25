// 时律契约 - 属性数据结构
#pragma once

#include "CoreMinimal.h"
#include "UObject/NoExportTypes.h"
#include "AttributeData.generated.h"

// 基础属性结构体
USTRUCT(BlueprintType)
struct FBaseAttributes
{
	GENERATED_BODY()

	// 最大生命值
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性")
	float MaxHP = 1000.0f;

	// 当前生命值
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性")
	float CurrentHP = 1000.0f;

	// 攻击力（影响伤害）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性")
	float AttackPower = 100.0f;

	// 防御力（影响减伤）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性")
	float DefensePower = 50.0f;

	// 暴击率（0-1）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性", meta = (ClampMin = "0.0", ClampMax = "1.0"))
	float CritRate = 0.1f;

	// 暴击伤害倍率
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性")
	float CritDamage = 1.5f;

	// 伤害加成百分比
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性")
	float DamageBonus = 0.0f;

	// 受伤减免百分比
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性")
	float DamageReduction = 0.0f;

	// 治疗效果提升百分比
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性")
	float HealingBonus = 0.0f;

	// 穿透率（无视防御百分比）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性", meta = (ClampMin = "0.0", ClampMax = "1.0"))
	float PenetrationRate = 0.0f;

	FBaseAttributes() {}
};

// 状态效果实例
USTRUCT(BlueprintType)
struct FStatusEffectInstance
{
	GENERATED_BODY()

	// 效果类型
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "状态效果")
	TEnumAsByte<enum EStatusEffectType> EffectType = EStatusEffectType::None;

	// 效果数值（伤害/治疗/百分比等）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "状态效果")
	float EffectValue = 0.0f;

	// 剩余持续回合数
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "状态效果")
	int32 RemainingTurns = 0;

	// 施加者（用于归因）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "状态效果")
	TWeakObjectPtr<class UObject> Source = nullptr;

	// 是否为增益效果
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "状态效果")
	bool bIsBuff = false;

	FStatusEffectInstance() {}
};

// 伤害计算结果
USTRUCT(BlueprintType)
struct FDamageResult
{
	GENERATED_BODY()

	// 最终伤害值
	UPROPERTY(BlueprintReadOnly, Category = "伤害")
	float FinalDamage = 0.0f;

	// 基础伤害
	UPROPERTY(BlueprintReadOnly, Category = "伤害")
	float BaseDamage = 0.0f;

	// 是否暴击
	UPROPERTY(BlueprintReadOnly, Category = "伤害")
	bool bIsCrit = false;

	// 是否属性克制
	UPROPERTY(BlueprintReadOnly, Category = "伤害")
	bool bIsEffective = false;

	// 实际被吸收的护盾值
	UPROPERTY(BlueprintReadOnly, Category = "伤害")
	float ShieldAbsorbed = 0.0f;

	FDamageResult() {}
};
