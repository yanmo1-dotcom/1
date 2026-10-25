// 时律契约 - 战斗HUD
#pragma once

#include "CoreMinimal.h"
#include "Blueprint/UserWidget.h"
#include "Enum/BattleEnums.h"
#include "BattleHUD.generated.h"

class ABattleManager;
class UCardHand;
class UCardWidget;
class UCharacterStatusWidget;
class UTextBlock;
class UButton;
class UHorizontalBox;
class UWrapBox;
class UCanvasPanel;

UCLASS()
class SHILVQIYUE_API UBattleHUD : public UUserWidget
{
	GENERATED_BODY()

public:
	// 设置战斗管理器
	UFUNCTION(BlueprintCallable, Category = "战斗UI")
	virtual void SetBattleManager(ABattleManager* InBattleManager);

	// 更新所有显示
	UFUNCTION(BlueprintCallable, Category = "战斗UI")
	virtual void UpdateAllDisplay();

	// 更新手牌显示
	UFUNCTION(BlueprintCallable, Category = "战斗UI")
	virtual void UpdateHandDisplay();

	// 更新角色状态显示
	UFUNCTION(BlueprintCallable, Category = "战斗UI")
	virtual void UpdateCharacterStatus();

	// 更新行动点显示
	UFUNCTION(BlueprintCallable, Category = "战斗UI")
	virtual void UpdateActionPoints();

	// 更新回合数显示
	UFUNCTION(BlueprintCallable, Category = "战斗UI")
	virtual void UpdateTurnNumber();

	// 更新战斗阶段显示
	UFUNCTION(BlueprintCallable, Category = "战斗UI")
	virtual void UpdateBattlePhase();

	// 显示战斗结果
	UFUNCTION(BlueprintCallable, Category = "战斗UI")
	virtual void ShowBattleResult(EBattleResult Result);

	// 选中卡牌
	UFUNCTION(BlueprintCallable, Category = "战斗UI")
	virtual void OnCardSelected(UCardWidget* SelectedCard);

	// 结束回合按钮点击
	UFUNCTION(BlueprintCallable, Category = "战斗UI")
	virtual void OnEndTurnClicked();

protected:
	virtual void NativeConstruct() override;

	// 战斗管理器引用
	UPROPERTY(BlueprintReadOnly, Category = "战斗UI")
	ABattleManager* BattleManager = nullptr;

	// 当前选中的卡牌
	UPROPERTY(BlueprintReadOnly, Category = "战斗UI")
	UCardWidget* SelectedCard = nullptr;

	// UI组件（需要在蓝图中绑定）
	UPROPERTY(meta = (BindWidget))
	UWrapBox* HandCardsBox = nullptr;

	UPROPERTY(meta = (BindWidget))
	UHorizontalBox* PlayerStatusBox = nullptr;

	UPROPERTY(meta = (BindWidget))
	UHorizontalBox* EnemyStatusBox = nullptr;

	UPROPERTY(meta = (BindWidget))
	UTextBlock* ActionPointsText = nullptr;

	UPROPERTY(meta = (BindWidget))
	UTextBlock* TurnNumberText = nullptr;

	UPROPERTY(meta = (BindWidget))
	UTextBlock* BattlePhaseText = nullptr;

	UPROPERTY(meta = (BindWidget))
	UButton* EndTurnButton = nullptr;

	UPROPERTY(meta = (BindWidget))
	UCanvasPanel* BattleResultPanel = nullptr;

	UPROPERTY(meta = (BindWidget))
	UTextBlock* ResultText = nullptr;

	// 卡牌控件类（在蓝图中设置）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "战斗UI")
	TSubclassOf<UCardWidget> CardWidgetClass;

	// 角色状态控件类（在蓝图中设置）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "战斗UI")
	TSubclassOf<UCharacterStatusWidget> CharacterStatusWidgetClass;

	// 战斗阶段变化处理
	UFUNCTION()
	virtual void OnBattlePhaseChanged(EBattlePhase OldPhase, EBattlePhase NewPhase);

	// 行动点变化处理
	UFUNCTION()
	virtual void OnActionPointsChanged(int32 NewActionPoints);

	// 回合开始处理
	UFUNCTION()
	virtual void OnTurnStarted(int32 TurnNumber);

	// 战斗结束处理
	UFUNCTION()
	virtual void OnBattleEnded(EBattleResult Result);

	// 手牌变化处理
	UFUNCTION()
	virtual void OnHandChanged();

	// 创建卡牌控件
	virtual UCardWidget* CreateCardWidget(class UCardBase* CardData);

	// 创建角色状态控件
	virtual UCharacterStatusWidget* CreateCharacterStatusWidget(class ACharacterBase* Character);
};
