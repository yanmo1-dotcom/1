// 时律契约 - 角色数据资产
#pragma once

#include "CoreMinimal.h"
#include "Engine/DataAsset.h"
#include "Data/AttributeData.h"
#include "Enum/BattleEnums.h"
#include "CharacterDataAsset.generated.h"

class UCardDataAsset;

UCLASS()
class SHILVQIYUE_API UCharacterDataAsset : public UDataAsset
{
	GENERATED_BODY()

public:
	// 角色ID
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色基础")
	FName CharacterID;

	// 角色名称
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色基础")
	FText CharacterName;

	// 角色称号
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色基础")
	FText CharacterTitle;

	// 角色描述
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色基础")
	FText CharacterDescription;

	// 角色属性
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色基础")
	TEnumAsByte<enum EAttributeType> CharacterAttribute = EAttributeType::None;

	// 角色定位
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色基础")
	TEnumAsByte<enum ECharacterRole> CharacterRole = ECharacterRole::MainDPS;

	// 稀有度
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色基础")
	int32 Rarity = 3; // 3-6星

	// 基础属性
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性")
	FBaseAttributes BaseAttributes;

	// 角色牌库（卡牌模板）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌")
	TArray<UCardDataAsset*> CardDeck;

	// 至终仪式（大招）卡牌
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌")
	UCardDataAsset* UltimateCard = nullptr;

	// 被动特性描述
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "技能")
	FText PassiveDescription;

	// 角色立绘
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "美术")
	TSoftObjectPtr<UTexture2D> CharacterPortrait;

	// 角色头像
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "美术")
	TSoftObjectPtr<UTexture2D> CharacterAvatar;

	// 角色3D模型
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "美术")
	TSoftClassPtr<class ACharacterBase> CharacterClass;

	// 获取属性（根据等级缩放）
	UFUNCTION(BlueprintPure, Category = "角色数据")
	FBaseAttributes GetScaledAttributes(int32 Level = 1) const
	{
		FBaseAttributes Scaled = BaseAttributes;
		float LevelMultiplier = 1.0f + (Level - 1) * 0.1f; // 每级+10%
		Scaled.MaxHP *= LevelMultiplier;
		Scaled.CurrentHP = Scaled.MaxHP;
		Scaled.AttackPower *= LevelMultiplier;
		Scaled.DefensePower *= LevelMultiplier;
		return Scaled;
	}
};
