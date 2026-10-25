// 时律契约 - 手牌管理器（准备区8张牌）
#pragma once

#include "CoreMinimal.h"
#include "UObject/NoExportTypes.h"
#include "CardData.h"
#include "CardHand.generated.h"

class UCardBase;
class ACharacterBase;

// 委托声明
DECLARE_DYNAMIC_MULTICAST_DELEGATE(FOnHandChanged);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_TwoParams(FOnCardMoved, int32, FromIndex, int32, ToIndex);

UCLASS(BlueprintType, Blueprintable)
class SHILVQIYUE_API UCardHand : public UObject
{
	GENERATED_BODY()

public:
	UCardHand();

	// 初始化手牌
	UFUNCTION(BlueprintCallable, Category = "手牌")
	void InitializeHand(int32 InMaxHandSize = 8);

	// 抽牌（从牌库添加到手牌）
	UFUNCTION(BlueprintCallable, Category = "手牌")
	bool AddCard(UCardBase* NewCard);

	// 移除卡牌
	UFUNCTION(BlueprintCallable, Category = "手牌")
	bool RemoveCard(UCardBase* CardToRemove);

	// 移除指定位置的卡牌
	UFUNCTION(BlueprintCallable, Category = "手牌")
	UCardBase* RemoveCardAt(int32 SlotIndex);

	// 移动卡牌位置
	UFUNCTION(BlueprintCallable, Category = "手牌")
	bool MoveCard(int32 FromIndex, int32 ToIndex);

	// 获取指定位置的卡牌
	UFUNCTION(BlueprintPure, Category = "手牌")
	UCardBase* GetCardAt(int32 SlotIndex) const;

	// 获取卡牌位置
	UFUNCTION(BlueprintPure, Category = "手牌")
	int32 GetCardIndex(UCardBase* Card) const;

	// 获取当前手牌数量
	UFUNCTION(BlueprintPure, Category = "手牌")
	int32 GetCardCount() const;

	// 获取最大手牌数量
	UFUNCTION(BlueprintPure, Category = "手牌")
	int32 GetMaxHandSize() const { return MaxHandSize; }

	// 是否有空位
	UFUNCTION(BlueprintPure, Category = "手牌")
	bool HasEmptySlot() const;

	// 获取第一个空位索引
	UFUNCTION(BlueprintPure, Category = "手牌")
	int32 GetFirstEmptySlot() const;

	// 清空手牌
	UFUNCTION(BlueprintCallable, Category = "手牌")
	void ClearHand();

	// 获取所有卡牌
	UFUNCTION(BlueprintPure, Category = "手牌")
	const TArray<UCardBase*>& GetAllCards() const { return HandCards; }

	// 获取指定角色的卡牌
	UFUNCTION(BlueprintPure, Category = "手牌")
	TArray<UCardBase*> GetCardsByOwner(ACharacterBase* Owner) const;

	// 检查两张牌是否相邻
	UFUNCTION(BlueprintPure, Category = "手牌")
	bool AreCardsAdjacent(int32 IndexA, int32 IndexB) const;

	// 手牌变化事件
	UPROPERTY(BlueprintAssignable, Category = "手牌事件")
	FOnHandChanged OnHandChanged;

	// 卡牌移动事件
	UPROPERTY(BlueprintAssignable, Category = "手牌事件")
	FOnCardMoved OnCardMoved;

protected:
	// 手牌数组（固定大小，空位为nullptr）
	UPROPERTY(BlueprintReadOnly, Category = "手牌")
	TArray<UCardBase*> HandCards;

	// 最大手牌数量（默认8）
	UPROPERTY(BlueprintReadOnly, Category = "手牌")
	int32 MaxHandSize = 8;

	// 更新所有卡牌的位置索引
	void UpdateCardSlotIndices();
};
