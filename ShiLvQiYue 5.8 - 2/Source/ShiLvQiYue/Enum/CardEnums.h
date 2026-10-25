// 时律契约 - 卡牌系统枚举
#pragma once

#include "CoreMinimal.h"
#include "CardEnums.generated.h"

// 卡牌类型
UENUM(BlueprintType)
enum class ECardType : uint8
{
	Attack			UMETA(DisplayName = "攻击牌"),
	Buff			UMETA(DisplayName = "增益牌"),
	Debuff			UMETA(DisplayName = "减益牌"),
	Heal			UMETA(DisplayName = "治疗牌"),
	Special			UMETA(DisplayName = "特殊牌"),
	Ultimate		UMETA(DisplayName = "至终仪式"),
	None			UMETA(DisplayName = "无")
};

// 卡牌阶位（1-3星）
UENUM(BlueprintType)
enum class ECardRank : uint8
{
	Rank1		UMETA(DisplayName = "1阶"),
	Rank2		UMETA(DisplayName = "2阶"),
	Rank3		UMETA(DisplayName = "3阶"),
	Max			UMETA(DisplayName = "最高阶")
};

// 卡牌稀有度
UENUM(BlueprintType)
enum class ECardRarity : uint8
{
	Common		UMETA(DisplayName = "普通"),
	Rare		UMETA(DisplayName = "稀有"),
	Epic		UMETA(DisplayName = "史诗"),
	Legendary	UMETA(DisplayName = "传说")
};

// 卡牌状态
UENUM(BlueprintType)
enum class ECardState : uint8
{
	InDeck			UMETA(DisplayName = "在牌库中"),
	InHand			UMETA(DisplayName = "在手牌中"),
	Selected		UMETA(DisplayName = "已选中"),
	Targeting		UMETA(DisplayName = "选择目标中"),
	Played			UMETA(DisplayName = "已打出"),
	Discarded		UMETA(DisplayName = "已弃置"),
	Exhausted		UMETA(DisplayName = "已消耗")
};

// 合成结果
UENUM(BlueprintType)
enum class EMergeResult : uint8
{
	Success			UMETA(DisplayName = "合成成功"),
	Fail_NotSame	UMETA(DisplayName = "卡牌不相同"),
	Fail_NotAdjacent UMETA(DisplayName = "卡牌不相邻"),
	Fail_MaxRank	UMETA(DisplayName = "已达最高阶"),
	Fail_Invalid	UMETA(DisplayName = "无效操作")
};
