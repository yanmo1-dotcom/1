// 时律契约 - 卡牌基类
#pragma once

#include "CoreMinimal.h"
#include "UObject/NoExportTypes.h"
#include "CardData.h"
#include "CardBase.generated.h"

class ACharacterBase;
class ABattleManager;

// 动态多播委托声明
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FOnCardSelected, UCardBase*, SelectedCard);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_TwoParams(FOnCardPlayed, UCardBase*, PlayedCard, ACharacterBase*, Target);

UCLASS(BlueprintType, Blueprintable)
class SHILVQIYUE_API UCardBase : public UObject
{
	GENERATED_BODY()

public:
	UCardBase();

	// 初始化卡牌
	UFUNCTION(BlueprintCallable, Category = "卡牌")
	virtual void InitializeCard(const FCardRuntimeData& InCardData, ACharacterBase* InOwner);

	// 打出卡牌
	UFUNCTION(BlueprintCallable, Category = "卡牌")
	virtual bool PlayCard(ACharacterBase* TargetCharacter);

	// 能否打出
	UFUNCTION(BlueprintCallable, Category = "卡牌")
	virtual bool CanPlayCard() const;

	// 获取卡牌数据
	UFUNCTION(BlueprintPure, Category = "卡牌")
	const FCardRuntimeData& GetCardData() const { return CardData; }

	// 获取所属角色
	UFUNCTION(BlueprintPure, Category = "卡牌")
	ACharacterBase* GetOwnerCharacter() const { return OwnerCharacter; }

	// 设置卡牌状态
	UFUNCTION(BlueprintCallable, Category = "卡牌")
	void SetCardState(ECardState NewState);

	// 设置手牌位置
	UFUNCTION(BlueprintCallable, Category = "卡牌")
	void SetHandSlotIndex(int32 NewIndex);

	// 卡牌被选中事件
	UPROPERTY(BlueprintAssignable, Category = "卡牌事件")
	FOnCardSelected OnCardSelected;

	// 卡牌被打出事件
	UPROPERTY(BlueprintAssignable, Category = "卡牌事件")
	FOnCardPlayed OnCardPlayed;

protected:
	// 卡牌运行时数据
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌")
	FCardRuntimeData CardData;

	// 所属角色
	UPROPERTY(BlueprintReadOnly, Category = "卡牌")
	TWeakObjectPtr<ACharacterBase> OwnerCharacter;

	// 战斗管理器引用
	UPROPERTY(BlueprintReadOnly, Category = "卡牌")
	TWeakObjectPtr<ABattleManager> BattleManager;

	// 执行卡牌效果
	virtual void ExecuteEffects(ACharacterBase* TargetCharacter);

	// 计算伤害
	virtual float CalculateDamage(ACharacterBase* TargetCharacter, float BaseValue);

	// 计算治疗
	virtual float CalculateHealing(float BaseValue);
};
