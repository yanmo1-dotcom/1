// 时律契约 - 激情值组件
#pragma once

#include "CoreMinimal.h"
#include "Components/ActorComponent.h"
#include "PassionComponent.generated.h"

// 激情值变化委托
DECLARE_DYNAMIC_MULTICAST_DELEGATE_TwoParams(FOnPassionChanged, int32, NewValue, int32, Delta);
DECLARE_DYNAMIC_MULTICAST_DELEGATE(FOnPassionMaxed);
DECLARE_DYNAMIC_MULTICAST_DELEGATE(FOnPassionReset);

UCLASS(ClassGroup = (Character), meta = (BlueprintSpawnableComponent))
class SHILVQIYUE_API UPassionComponent : public UActorComponent
{
	GENERATED_BODY()

public:
	UPassionComponent();

	// 初始化激情组件
	UFUNCTION(BlueprintCallable, Category = "激情值")
	void InitializePassion(int32 InMaxPassion = 5);

	// 增加激情值
	UFUNCTION(BlueprintCallable, Category = "激情值")
	int32 AddPassion(int32 Amount = 1);

	// 减少激情值
	UFUNCTION(BlueprintCallable, Category = "激情值")
	int32 RemovePassion(int32 Amount = 1);

	// 重置激情值（释放大招后）
	UFUNCTION(BlueprintCallable, Category = "激情值")
	void ResetPassion();

	// 获取当前激情值
	UFUNCTION(BlueprintPure, Category = "激情值")
	int32 GetCurrentPassion() const { return CurrentPassion; }

	// 获取最大激情值
	UFUNCTION(BlueprintPure, Category = "激情值")
	int32 GetMaxPassion() const { return MaxPassion; }

	// 是否充满激情
	UFUNCTION(BlueprintPure, Category = "激情值")
	bool IsPassionMaxed() const { return CurrentPassion >= MaxPassion; }

	// 是否已经抽了大招牌
	UFUNCTION(BlueprintPure, Category = "激情值")
	bool HasUltimateCardDrawn() const { return bHasUltimateCardDrawn; }

	// 设置大招牌已抽取
	UFUNCTION(BlueprintCallable, Category = "激情值")
	void SetUltimateCardDrawn(bool bDrawn) { bHasUltimateCardDrawn = bDrawn; }

	// 激情值变化事件
	UPROPERTY(BlueprintAssignable, Category = "激情事件")
	FOnPassionChanged OnPassionChanged;

	// 激情值满事件
	UPROPERTY(BlueprintAssignable, Category = "激情事件")
	FOnPassionMaxed OnPassionMaxed;

	// 激情值重置事件
	UPROPERTY(BlueprintAssignable, Category = "激情事件")
	FOnPassionReset OnPassionReset;

protected:
	// 当前激情值
	UPROPERTY(BlueprintReadOnly, Category = "激情值")
	int32 CurrentPassion = 0;

	// 最大激情值（默认5）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "激情值")
	int32 MaxPassion = 5;

	// 是否已抽取大招牌（充满后下回合抽）
	UPROPERTY(BlueprintReadOnly, Category = "激情值")
	bool bHasUltimateCardDrawn = false;

	// 激情获取加成（百分比，0表示无加成）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "激情值")
	float PassionGainBonus = 0.0f;
};
