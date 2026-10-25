// 时律契约 - 卡牌控件
#pragma once

#include "CoreMinimal.h"
#include "Blueprint/UserWidget.h"
#include "CardWidget.generated.h"

class UCardBase;
class UImage;
class UTextBlock;
class UBorder;
class UButton;

// 委托声明
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FOnCardClicked, UCardWidget*, CardWidget);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FOnCardDragStart, UCardWidget*, CardWidget);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FOnCardDragEnd, UCardWidget*, CardWidget);

UCLASS()
class SHILVQIYUE_API UCardWidget : public UUserWidget
{
	GENERATED_BODY()

public:
	// 设置卡牌数据
	UFUNCTION(BlueprintCallable, Category = "卡牌UI")
	virtual void SetCardData(UCardBase* InCard);

	// 获取卡牌数据
	UFUNCTION(BlueprintPure, Category = "卡牌UI")
	UCardBase* GetCardData() const { return CardData; }

	// 设置选中状态
	UFUNCTION(BlueprintCallable, Category = "卡牌UI")
	virtual void SetSelected(bool bSelected);

	// 设置可合成高亮
	UFUNCTION(BlueprintCallable, Category = "卡牌UI")
	virtual void SetMergeHighlight(bool bHighlight);

	// 更新显示
	UFUNCTION(BlueprintCallable, Category = "卡牌UI")
	virtual void UpdateDisplay();

	// 卡牌点击事件
	UPROPERTY(BlueprintAssignable, Category = "卡牌UI事件")
	FOnCardClicked OnCardClicked;

	// 卡牌拖拽开始事件
	UPROPERTY(BlueprintAssignable, Category = "卡牌UI事件")
	FOnCardDragStart OnCardDragStart;

	// 卡牌拖拽结束事件
	UPROPERTY(BlueprintAssignable, Category = "卡牌UI事件")
	FOnCardDragEnd OnCardDragEnd;

protected:
	virtual void NativeConstruct() override;
	virtual void NativeOnInitialized() override;

	// 卡牌数据
	UPROPERTY(BlueprintReadOnly, Category = "卡牌UI")
	UCardBase* CardData = nullptr;

	// 是否选中
	UPROPERTY(BlueprintReadOnly, Category = "卡牌UI")
	bool bIsSelected = false;

	// 是否可合成高亮
	UPROPERTY(BlueprintReadOnly, Category = "卡牌UI")
	bool bIsMergeHighlighted = false;

	// UI组件（需要在蓝图中绑定）
	UPROPERTY(meta = (BindWidget))
	UImage* CardIcon = nullptr;

	UPROPERTY(meta = (BindWidget))
	UTextBlock* CardNameText = nullptr;

	UPROPERTY(meta = (BindWidget))
	UTextBlock* CardDescriptionText = nullptr;

	UPROPERTY(meta = (BindWidget))
	UTextBlock* RankText = nullptr;

	UPROPERTY(meta = (BindWidget))
	UBorder* CardBorder = nullptr;

	UPROPERTY(meta = (BindWidget))
	UButton* CardButton = nullptr;

	// 卡牌点击处理
	UFUNCTION()
	virtual void OnCardButtonClicked();

	// 获取卡牌类型颜色
	FLinearColor GetCardTypeColor() const;

	// 获取阶位显示文本
	FText GetRankDisplayText() const;
};
