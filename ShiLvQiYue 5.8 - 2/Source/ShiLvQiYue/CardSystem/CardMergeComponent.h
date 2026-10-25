// 时律契约 - 卡牌合成组件
#pragma once

#include "CoreMinimal.h"
#include "Components/ActorComponent.h"
#include "Containers/Array.h"
#include "CardData.h"
#include "CardMergeComponent.generated.h"

class UCardHand;
class UCardBase;
class ACharacterBase;

// 委托声明
DECLARE_DYNAMIC_MULTICAST_DELEGATE_TwoParams(FOnMergeSuccess, UCardBase*, MergedCard, int32, SlotIndex);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FOnMergeFailed, EMergeResult, FailReason);

UCLASS(ClassGroup = (CardSystem), meta = (BlueprintSpawnableComponent))
class SHILVQIYUE_API UCardMergeComponent : public UActorComponent
{
	GENERATED_BODY()

public:
	UCardMergeComponent();

	// 初始化合成组件
	UFUNCTION(BlueprintCallable, Category = "卡牌合成")
	void InitializeMergeComponent(UCardHand* InCardHand);

	// 检查两张牌是否可以合成
	UFUNCTION(BlueprintPure, Category = "卡牌合成")
	bool CanMergeCards(UCardBase* CardA, UCardBase* CardB) const;

	// 检查指定位置的两张牌是否可以合成
	UFUNCTION(BlueprintPure, Category = "卡牌合成")
	bool CanMergeCardsAt(int32 IndexA, int32 IndexB) const;

	// 执行合成
	UFUNCTION(BlueprintCallable, Category = "卡牌合成")
	EMergeResult MergeCards(UCardBase* CardA, UCardBase* CardB);

	// 执行指定位置的合成
	UFUNCTION(BlueprintCallable, Category = "卡牌合成")
	EMergeResult MergeCardsAt(int32 IndexA, int32 IndexB);

	// 查找所有可合成的卡牌对
	UFUNCTION(BlueprintPure, Category = "卡牌合成")
	TArray<FIntPoint> FindAllMergeablePairs() const;

	// 自动整理（将相同的牌移到相邻位置，但不自动合成）
	UFUNCTION(BlueprintCallable, Category = "卡牌合成")
	void AutoOrganizeCards();

	// 合成成功事件
	UPROPERTY(BlueprintAssignable, Category = "合成事件")
	FOnMergeSuccess OnMergeSuccess;

	// 合成失败事件
	UPROPERTY(BlueprintAssignable, Category = "合成事件")
	FOnMergeFailed OnMergeFailed;

protected:
	// 手牌管理器引用
	UPROPERTY(BlueprintReadOnly, Category = "卡牌合成")
	TWeakObjectPtr<UCardHand> CardHand;

	// 创建合成后的高阶卡牌
	virtual UCardBase* CreateMergedCard(UCardBase* CardA, UCardBase* CardB);

	// 检查卡牌是否相同（同角色、同技能、同阶位）
	bool AreCardsSame(UCardBase* CardA, UCardBase* CardB) const;
};
