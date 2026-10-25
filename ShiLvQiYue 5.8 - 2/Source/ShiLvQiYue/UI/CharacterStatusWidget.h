// 时律契约 - 角色状态控件
#pragma once

#include "CoreMinimal.h"
#include "Blueprint/UserWidget.h"
#include "CharacterStatusWidget.generated.h"

class ACharacterBase;
class UProgressBar;
class UTextBlock;
class UImage;
class UHorizontalBox;

UCLASS()
class SHILVQIYUE_API UCharacterStatusWidget : public UUserWidget
{
	GENERATED_BODY()

public:
	// 设置角色
	UFUNCTION(BlueprintCallable, Category = "角色状态UI")
	virtual void SetCharacter(ACharacterBase* InCharacter);

	// 获取角色
	UFUNCTION(BlueprintPure, Category = "角色状态UI")
	ACharacterBase* GetCharacter() const { return Character; }

	// 更新显示
	UFUNCTION(BlueprintCallable, Category = "角色状态UI")
	virtual void UpdateDisplay();

	// 更新HP
	UFUNCTION(BlueprintCallable, Category = "角色状态UI")
	virtual void UpdateHP();

	// 更新激情值
	UFUNCTION(BlueprintCallable, Category = "角色状态UI")
	virtual void UpdatePassion();

	// 更新状态效果
	UFUNCTION(BlueprintCallable, Category = "角色状态UI")
	virtual void UpdateStatusEffects();

protected:
	virtual void NativeConstruct() override;

	// 角色引用
	UPROPERTY(BlueprintReadOnly, Category = "角色状态UI")
	ACharacterBase* Character = nullptr;

	// UI组件（需要在蓝图中绑定）
	UPROPERTY(meta = (BindWidget))
	UTextBlock* CharacterNameText = nullptr;

	UPROPERTY(meta = (BindWidget))
	UProgressBar* HPBar = nullptr;

	UPROPERTY(meta = (BindWidget))
	UTextBlock* HPText = nullptr;

	UPROPERTY(meta = (BindWidget))
	UHorizontalBox* PassionPointsBox = nullptr;

	UPROPERTY(meta = (BindWidget))
	UHorizontalBox* StatusEffectsBox = nullptr;

	UPROPERTY(meta = (BindWidget))
	UImage* CharacterIcon = nullptr;

	UPROPERTY(meta = (BindWidget))
	UImage* AttributeIcon = nullptr;

	// 激情点控件类
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色状态UI")
	TSubclassOf<UUserWidget> PassionPointWidgetClass;

	// 状态效果图标控件类
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色状态UI")
	TSubclassOf<UUserWidget> StatusEffectIconWidgetClass;

	// 角色受伤处理
	UFUNCTION()
	virtual void OnCharacterDamaged(ACharacterBase* DamagedCharacter, float Damage);

	// 角色被治疗处理
	UFUNCTION()
	virtual void OnCharacterHealed(ACharacterBase* HealedCharacter, float Healing);

	// 角色死亡处理
	UFUNCTION()
	virtual void OnCharacterDied(ACharacterBase* DeadCharacter);
};
