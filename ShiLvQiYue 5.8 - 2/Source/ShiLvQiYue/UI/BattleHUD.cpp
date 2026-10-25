// 时律契约 - 战斗HUD实现
#include "UI/BattleHUD.h"
#include "Core/BattleManager.h"
#include "CardSystem/CardHand.h"
#include "CardSystem/CardBase.h"
#include "UI/CardWidget.h"
#include "UI/CharacterStatusWidget.h"
#include "Character/CharacterBase.h"
#include "Components/TextBlock.h"
#include "Components/Button.h"
#include "Components/WrapBox.h"
#include "Components/HorizontalBox.h"
#include "Components/CanvasPanel.h"

void UBattleHUD::NativeConstruct()
{
	Super::NativeConstruct();

	if (EndTurnButton)
	{
		EndTurnButton->OnClicked.AddDynamic(this, &UBattleHUD::OnEndTurnClicked);
	}

	if (BattleResultPanel)
	{
		BattleResultPanel->SetVisibility(ESlateVisibility::Collapsed);
	}
}

void UBattleHUD::SetBattleManager(ABattleManager* InBattleManager)
{
	BattleManager = InBattleManager;
	if (!BattleManager) return;

	// 绑定事件
	BattleManager->OnBattlePhaseChanged.AddDynamic(this, &UBattleHUD::OnBattlePhaseChanged);
	BattleManager->OnActionPointsChanged.AddDynamic(this, &UBattleHUD::OnActionPointsChanged);
	BattleManager->OnTurnStarted.AddDynamic(this, &UBattleHUD::OnTurnStarted);
	BattleManager->OnBattleEnded.AddDynamic(this, &UBattleHUD::OnBattleEnded);

	// 绑定手牌变化
	if (BattleManager->GetCardHand())
	{
		BattleManager->GetCardHand()->OnHandChanged.AddDynamic(this, &UBattleHUD::OnHandChanged);
	}

	UpdateAllDisplay();
}

void UBattleHUD::UpdateAllDisplay()
{
	UpdateHandDisplay();
	UpdateCharacterStatus();
	UpdateActionPoints();
	UpdateTurnNumber();
	UpdateBattlePhase();
}

void UBattleHUD::UpdateHandDisplay()
{
	if (!HandCardsBox || !BattleManager || !BattleManager->GetCardHand()) return;

	// 清空现有卡牌
	HandCardsBox->ClearChildren();

	// 创建新卡牌控件
	UCardHand* CardHand = BattleManager->GetCardHand();
	for (int32 i = 0; i < CardHand->GetMaxHandSize(); i++)
	{
		UCardBase* Card = CardHand->GetCardAt(i);
		if (Card)
		{
			UCardWidget* CardWidget = CreateCardWidget(Card);
			if (CardWidget)
			{
				HandCardsBox->AddChildToWrapBox(CardWidget);
			}
		}
	}
}

void UBattleHUD::UpdateCharacterStatus()
{
	if (!PlayerStatusBox || !EnemyStatusBox || !BattleManager) return;

	// 清空
	PlayerStatusBox->ClearChildren();
	EnemyStatusBox->ClearChildren();

	// 创建玩家角色状态
	for (ACharacterBase* Character : BattleManager->GetPlayerCharacters())
	{
		if (Character)
		{
			UCharacterStatusWidget* StatusWidget = CreateCharacterStatusWidget(Character);
			if (StatusWidget)
			{
				PlayerStatusBox->AddChildToHorizontalBox(StatusWidget);
			}
		}
	}

	// 创建敌人角色状态
	for (ACharacterBase* Character : BattleManager->GetEnemyCharacters())
	{
		if (Character)
		{
			UCharacterStatusWidget* StatusWidget = CreateCharacterStatusWidget(Character);
			if (StatusWidget)
			{
				EnemyStatusBox->AddChildToHorizontalBox(StatusWidget);
			}
		}
	}
}

void UBattleHUD::UpdateActionPoints()
{
	if (!ActionPointsText || !BattleManager) return;
	ActionPointsText->SetText(FText::FromString(
		FString::Printf(TEXT("行动点: %d/%d"), 
			BattleManager->GetCurrentActionPoints(), 
			BattleManager->GetMaxActionPoints())));
}

void UBattleHUD::UpdateTurnNumber()
{
	if (!TurnNumberText || !BattleManager) return;
	TurnNumberText->SetText(FText::FromString(
		FString::Printf(TEXT("第 %d 回合"), BattleManager->GetCurrentTurn())));
}

void UBattleHUD::UpdateBattlePhase()
{
	if (!BattlePhaseText || !BattleManager) return;

	FText PhaseText;
	switch (BattleManager->GetCurrentPhase())
	{
	case EBattlePhase::DrawPhase:
		PhaseText = FText::FromString(TEXT("发牌阶段"));
		break;
	case EBattlePhase::PlayerTurn:
		PhaseText = FText::FromString(TEXT("我方回合"));
		break;
	case EBattlePhase::EnemyTurn:
		PhaseText = FText::FromString(TEXT("敌方回合"));
		break;
	case EBattlePhase::StatusPhase:
		PhaseText = FText::FromString(TEXT("状态结算"));
		break;
	default:
		PhaseText = FText::FromString(TEXT("准备中"));
		break;
	}
	BattlePhaseText->SetText(PhaseText);
}

void UBattleHUD::ShowBattleResult(EBattleResult Result)
{
	if (!BattleResultPanel || !ResultText) return;

	BattleResultPanel->SetVisibility(ESlateVisibility::Visible);
	switch (Result)
	{
	case EBattleResult::Victory:
		ResultText->SetText(FText::FromString(TEXT("战斗胜利！")));
		ResultText->SetColorAndOpacity(FSlateColor(FLinearColor(0.2f, 0.8f, 0.2f)));
		break;
	case EBattleResult::Defeat:
		ResultText->SetText(FText::FromString(TEXT("战斗失败...")));
		ResultText->SetColorAndOpacity(FSlateColor(FLinearColor(0.8f, 0.2f, 0.2f)));
		break;
	default:
		ResultText->SetText(FText::FromString(TEXT("平局")));
		break;
	}
}

void UBattleHUD::OnCardSelected(UCardWidget* SelectedCardWidget)
{
	// 取消之前选中
	if (SelectedCard && SelectedCard != SelectedCardWidget)
	{
		SelectedCard->SetSelected(false);
	}

	SelectedCard = SelectedCardWidget;
	if (SelectedCard)
	{
		SelectedCard->SetSelected(true);
	}
}

void UBattleHUD::OnEndTurnClicked()
{
	if (BattleManager && BattleManager->GetCurrentPhase() == EBattlePhase::PlayerTurn)
	{
		BattleManager->EndPlayerTurn();
	}
}

void UBattleHUD::OnBattlePhaseChanged(EBattlePhase OldPhase, EBattlePhase NewPhase)
{
	UpdateBattlePhase();

	// 玩家回合时启用结束回合按钮
	if (EndTurnButton)
	{
		EndTurnButton->SetIsEnabled(NewPhase == EBattlePhase::PlayerTurn);
	}
}

void UBattleHUD::OnActionPointsChanged(int32 NewActionPoints)
{
	UpdateActionPoints();
}

void UBattleHUD::OnTurnStarted(int32 TurnNumber)
{
	UpdateTurnNumber();
}

void UBattleHUD::OnBattleEnded(EBattleResult Result)
{
	ShowBattleResult(Result);
}

void UBattleHUD::OnHandChanged()
{
	UpdateHandDisplay();
}

UCardWidget* UBattleHUD::CreateCardWidget(UCardBase* CardData)
{
	if (!CardWidgetClass || !CardData) return nullptr;

	UCardWidget* Widget = CreateWidget<UCardWidget>(GetWorld(), CardWidgetClass);
	if (Widget)
	{
		Widget->SetCardData(CardData);
		Widget->OnCardClicked.AddDynamic(this, &UBattleHUD::OnCardSelected);
	}
	return Widget;
}

UCharacterStatusWidget* UBattleHUD::CreateCharacterStatusWidget(ACharacterBase* Character)
{
	if (!CharacterStatusWidgetClass || !Character) return nullptr;

	UCharacterStatusWidget* Widget = CreateWidget<UCharacterStatusWidget>(GetWorld(), CharacterStatusWidgetClass);
	if (Widget)
	{
		Widget->SetCharacter(Character);
	}
	return Widget;
}
