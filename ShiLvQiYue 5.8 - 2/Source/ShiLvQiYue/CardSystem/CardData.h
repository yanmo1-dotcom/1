// 时律契约 - 卡牌数据结构
#pragma once

#include "CoreMinimal.h"
#include "UObject/NoExportTypes.h"
#include "Enum/CardEnums.h"
#include "Enum/BattleEnums.h"
#include "CardData.generated.h"

// 卡牌效果数据
USTRUCT(BlueprintType)
struct FCardEffect
{
	GENERATED_BODY()

	// 效果类型（伤害/治疗/护盾/增益/减益）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌效果")
	TEnumAsByte<enum ECardType> EffectType = ECardType::Attack;

	// 效果数值（基础伤害/治疗量/护盾量）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌效果")
	float BaseValue = 100.0f;

	// 目标类型
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌效果")
	TEnumAsByte<enum ESkillTargetType> TargetType = ESkillTargetType::SingleEnemy;

	// 附加状态效果类型
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌效果")
	TEnumAsByte<enum EStatusEffectType> StatusEffectType = EStatusEffectType::None;

	// 状态效果数值
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌效果")
	float StatusEffectValue = 0.0f;

	// 状态效果持续回合
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌效果")
	int32 StatusEffectDuration = 0;

	// 效果触发概率（0-1，1为必定触发）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌效果", meta = (ClampMin = "0.0", ClampMax = "1.0"))
	float TriggerChance = 1.0f;

	FCardEffect() {}
};

// 卡牌运行时数据
USTRUCT(BlueprintType)
struct FCardRuntimeData
{
	GENERATED_BODY()

	// 卡牌唯一ID
	UPROPERTY(BlueprintReadOnly, Category = "卡牌")
	FGuid CardID;

	// 卡牌名称
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌")
	FText CardName;

	// 卡牌描述
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌")
	FText CardDescription;

	// 卡牌类型
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌")
	TEnumAsByte<enum ECardType> CardType = ECardType::Attack;

	// 卡牌阶位
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌")
	TEnumAsByte<enum ECardRank> CardRank = ECardRank::Rank1;

	// 卡牌属性
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌")
	TEnumAsByte<enum EAttributeType> CardAttribute = EAttributeType::None;

	// 所属角色ID
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌")
	FGuid OwnerCharacterID;

	// 卡牌效果列表（一张牌可有多个效果）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌效果")
	TArray<FCardEffect> Effects;

	// 当前状态
	UPROPERTY(BlueprintReadOnly, Category = "卡牌")
	TEnumAsByte<enum ECardState> CurrentState = ECardState::InDeck;

	// 在准备区中的位置索引（-1表示不在准备区）
	UPROPERTY(BlueprintReadOnly, Category = "卡牌")
	int32 HandSlotIndex = -1;

	// 阶位伤害倍率
	float GetRankMultiplier() const
	{
		switch (CardRank)
		{
		case ECardRank::Rank1: return 1.0f;
		case ECardRank::Rank2: return 1.8f;
		case ECardRank::Rank3: return 2.8f;
		default: return 1.0f;
		}
	}

	// 是否可以合成（不是最高阶且不是大招）
	bool CanMerge() const
	{
		return CardRank != ECardRank::Rank3 && CardType != ECardType::Ultimate;
	}

	FCardRuntimeData()
	{
		CardID = FGuid::NewGuid();
	}
};
