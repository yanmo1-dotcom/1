// 时律契约 - 卡牌数据资产
#pragma once

#include "CoreMinimal.h"
#include "Engine/DataAsset.h"
#include "CardSystem/CardData.h"
#include "CardDataAsset.generated.h"

UCLASS()
class SHILVQIYUE_API UCardDataAsset : public UDataAsset
{
	GENERATED_BODY()

public:
	// 卡牌名称
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌基础")
	FText CardName;

	// 卡牌描述
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌基础")
	FText CardDescription;

	// 卡牌类型
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌基础")
	TEnumAsByte<enum ECardType> CardType = ECardType::Attack;

	// 卡牌属性
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌基础")
	TEnumAsByte<enum EAttributeType> CardAttribute = EAttributeType::None;

	// 卡牌图标
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌基础")
	TSoftObjectPtr<UTexture2D> CardIcon;

	// 卡牌效果列表
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌效果")
	TArray<FCardEffect> Effects;

	// 获取运行时数据
	UFUNCTION(BlueprintPure, Category = "卡牌数据")
	FCardRuntimeData GetRuntimeData() const
	{
		FCardRuntimeData Data;
		Data.CardName = CardName;
		Data.CardDescription = CardDescription;
		Data.CardType = CardType;
		Data.CardAttribute = CardAttribute;
		Data.CardRank = ECardRank::Rank1;
		Data.Effects = Effects;
		Data.CurrentState = ECardState::InDeck;
		return Data;
	}
};
