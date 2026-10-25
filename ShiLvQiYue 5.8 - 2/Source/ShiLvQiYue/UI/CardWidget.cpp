// 时律契约 - 卡牌控件实现
#include "UI/CardWidget.h"
#include "CardSystem/CardBase.h"
#include "Components/Image.h"
#include "Components/TextBlock.h"
#include "Components/Border.h"
#include "Components/Button.h"

void UCardWidget::NativeConstruct()
{
	Super::NativeConstruct();

	if (CardButton)
	{
		CardButton->OnClicked.AddDynamic(this, &UCardWidget::OnCardButtonClicked);
	}
}

void UCardWidget::NativeOnInitialized()
{
	Super::NativeOnInitialized();
}

void UCardWidget::SetCardData(UCardBase* InCard)
{
	CardData = InCard;
	UpdateDisplay();
}

void UCardWidget::SetSelected(bool bSelected)
{
	bIsSelected = bSelected;
	if (CardBorder)
	{
		if (bSelected)
		{
			CardBorder->SetBrushColor(FLinearColor(1.0f, 0.8f, 0.0f, 1.0f)); // 金色选中
		}
		else
		{
			CardBorder->SetBrushColor(GetCardTypeColor());
		}
	}
}

void UCardWidget::SetMergeHighlight(bool bHighlight)
{
	bIsMergeHighlighted = bHighlight;
	if (CardBorder)
	{
		if (bHighlight)
		{
			CardBorder->SetBrushColor(FLinearColor(0.0f, 1.0f, 0.0f, 1.0f)); // 绿色可合成
		}
		else
		{
			CardBorder->SetBrushColor(GetCardTypeColor());
		}
	}
}

void UCardWidget::UpdateDisplay()
{
	if (!CardData) return;

	const FCardRuntimeData& Data = CardData->GetCardData();

	if (CardNameText)
	{
		CardNameText->SetText(Data.CardName);
	}

	if (CardDescriptionText)
	{
		CardDescriptionText->SetText(Data.CardDescription);
	}

	if (RankText)
	{
		RankText->SetText(GetRankDisplayText());
	}

	if (CardBorder)
	{
		CardBorder->SetBrushColor(GetCardTypeColor());
	}
}

void UCardWidget::OnCardButtonClicked()
{
	OnCardClicked.Broadcast(this);
}

FLinearColor UCardWidget::GetCardTypeColor() const
{
	if (!CardData) return FLinearColor::Gray;

	switch (CardData->GetCardData().CardType)
	{
	case ECardType::Attack:
		return FLinearColor(0.8f, 0.2f, 0.2f, 1.0f); // 红色
	case ECardType::Buff:
		return FLinearColor(0.2f, 0.4f, 0.8f, 1.0f); // 蓝色
	case ECardType::Debuff:
		return FLinearColor(0.6f, 0.2f, 0.8f, 1.0f); // 紫色
	case ECardType::Heal:
		return FLinearColor(0.2f, 0.7f, 0.3f, 1.0f); // 绿色
	case ECardType::Special:
		return FLinearColor(0.9f, 0.7f, 0.1f, 1.0f); // 金色
	case ECardType::Ultimate:
		return FLinearColor(1.0f, 0.5f, 0.0f, 1.0f); // 橙色
	default:
		return FLinearColor::Gray;
	}
}

FText UCardWidget::GetRankDisplayText() const
{
	if (!CardData) return FText::GetEmpty();

	switch (CardData->GetCardData().CardRank)
	{
	case ECardRank::Rank1:
		return FText::FromString(TEXT("★"));
	case ECardRank::Rank2:
		return FText::FromString(TEXT("★★"));
	case ECardRank::Rank3:
		return FText::FromString(TEXT("★★★"));
	default:
		return FText::GetEmpty();
	}
}
