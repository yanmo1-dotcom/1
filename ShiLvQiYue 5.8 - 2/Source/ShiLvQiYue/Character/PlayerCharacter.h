// 时律契约 - 玩家角色
#pragma once

#include "CoreMinimal.h"
#include "Character/CharacterBase.h"
#include "PlayerCharacter.generated.h"

UCLASS(Blueprintable)
class SHILVQIYUE_API APlayerCharacter : public ACharacterBase
{
	GENERATED_BODY()

public:
	APlayerCharacter();

	// 初始化玩家角色
	UFUNCTION(BlueprintCallable, Category = "玩家角色")
	virtual void InitializePlayerCharacter(const FBaseAttributes& InAttributes, EAttributeType InAttribute, ECharacterRole InRole);

	// 是否在替补席
	UFUNCTION(BlueprintPure, Category = "玩家角色")
	bool IsInReserve() const { return bIsInReserve; }

	// 设置是否在替补席
	UFUNCTION(BlueprintCallable, Category = "玩家角色")
	void SetInReserve(bool bInReserve) { bIsInReserve = bInReserve; }

	// 是否有登场保护
	UFUNCTION(BlueprintPure, Category = "玩家角色")
	bool HasEntryProtection() const { return bHasEntryProtection; }

	// 激活登场保护
	UFUNCTION(BlueprintCallable, Category = "玩家角色")
	void ActivateEntryProtection();

	// 回合结束时处理登场保护
	UFUNCTION(BlueprintCallable, Category = "玩家角色")
	void ProcessEntryProtection();

protected:
	// 是否在替补席
	UPROPERTY(BlueprintReadOnly, Category = "玩家角色")
	bool bIsInReserve = false;

	// 是否有登场保护（替补上场第一回合减伤50%）
	UPROPERTY(BlueprintReadOnly, Category = "玩家角色")
	bool bHasEntryProtection = false;
};
