// 时律契约 - 卡牌合成组件实现
#include "CardSystem/CardMergeComponent.h"
#include "CardSystem/CardHand.h"
#include "CardSystem/CardBase.h"
#include "Character/CharacterBase.h"

UCardMergeComponent::UCardMergeComponent()
{
	PrimaryComponentTick.bCanEverTick = false;
}

void UCardMergeComponent::InitializeMergeComponent(UCardHand* InCardHand)
{
	CardHand = InCardHand;
	UE_LOG(LogCard, Log, TEXT("卡牌合成组件初始化完成"));
}

bool UCardMergeComponent::CanMergeCards(UCardBase* CardA, UCardBase* CardB) const
{
	if (!CardA || !CardB || !CardHand.IsValid())
	{
		return false;
	}

	// 必须都在手牌中
	if (CardA->GetCardData().CurrentState != ECardState::InHand ||
		CardB->GetCardData().CurrentState != ECardState::InHand)
	{
		return false;
	}

	// 必须可以合成（不是最高阶，不是大招）
	if (!CardA->GetCardData().CanMerge() || !CardB->GetCardData().CanMerge())
	{
		return false;
	}

	// 必须是相同的牌
	if (!AreCardsSame(CardA, CardB))
	{
		return false;
	}

	// 必须相邻
	int32 IndexA = CardHand->GetCardIndex(CardA);
	int32 IndexB = CardHand->GetCardIndex(CardB);
	if (!CardHand->AreCardsAdjacent(IndexA, IndexB))
	{
		return false;
	}

	return true;
}

bool UCardMergeComponent::CanMergeCardsAt(int32 IndexA, int32 IndexB) const
{
	if (!CardHand.IsValid()) return false;
	UCardBase* CardA = CardHand->GetCardAt(IndexA);
	UCardBase* CardB = CardHand->GetCardAt(IndexB);
	return CanMergeCards(CardA, CardB);
}

EMergeResult UCardMergeComponent::MergeCards(UCardBase* CardA, UCardBase* CardB)
{
	if (!CardHand.IsValid())
	{
		OnMergeFailed.Broadcast(EMergeResult::Fail_Invalid);
		return EMergeResult::Fail_Invalid;
	}

	if (!CanMergeCards(CardA, CardB))
	{
		EMergeResult Reason = EMergeResult::Fail_NotSame;
		if (!AreCardsSame(CardA, CardB)) Reason = EMergeResult::Fail_NotSame;
		else if (!CardA->GetCardData().CanMerge()) Reason = EMergeResult::Fail_MaxRank;
		
		OnMergeFailed.Broadcast(Reason);
		return Reason;
	}

	int32 IndexA = CardHand->GetCardIndex(CardA);
	int32 IndexB = CardHand->GetCardIndex(CardB);
	int32 TargetIndex = FMath::Min(IndexA, IndexB);

	// 创建合成后的高阶卡牌
	UCardBase* MergedCard = CreateMergedCard(CardA, CardB);
	if (!MergedCard)
	{
		OnMergeFailed.Broadcast(EMergeResult::Fail_Invalid);
		return EMergeResult::Fail_Invalid;
	}

	// 移除原卡牌
	CardHand->RemoveCard(CardA);
	CardHand->RemoveCard(CardB);

	// 将新卡牌放到目标位置
	// 先移到空位，再移到目标位置
	int32 EmptySlot = CardHand->GetFirstEmptySlot();
	if (EmptySlot != INDEX_NONE)
	{
		// 直接设置到目标位置（通过MoveCard）
		CardHand->AddCard(MergedCard);
		int32 NewIndex = CardHand->GetCardIndex(MergedCard);
		if (NewIndex != TargetIndex)
		{
			CardHand->MoveCard(NewIndex, TargetIndex);
		}
	}

	// 合成给所属角色+1激情
	if (MergedCard->GetOwnerCharacter())
	{
		MergedCard->GetOwnerCharacter()->AddPassion(1);
	}

	UE_LOG(LogCard, Log, TEXT("卡牌合成成功: %s + %s -> %s (阶位提升)"), 
		*CardA->GetCardData().CardName.ToString(),
		*CardB->GetCardData().CardName.ToString(),
		*MergedCard->GetCardData().CardName.ToString());

	OnMergeSuccess.Broadcast(MergedCard, TargetIndex);
	return EMergeResult::Success;
}

EMergeResult UCardMergeComponent::MergeCardsAt(int32 IndexA, int32 IndexB)
{
	if (!CardHand.IsValid()) return EMergeResult::Fail_Invalid;
	UCardBase* CardA = CardHand->GetCardAt(IndexA);
	UCardBase* CardB = CardHand->GetCardAt(IndexB);
	return MergeCards(CardA, CardB);
}

TArray<FIntPoint> UCardMergeComponent::FindAllMergeablePairs() const
{
	TArray<TPair<int32, int32>> Result;
	if (!CardHand.IsValid()) return Result;

	int32 MaxSize = CardHand->GetMaxHandSize();
	for (int32 i = 0; i < MaxSize - 1; i++)
	{
		if (CanMergeCardsAt(i, i + 1))
		{
			Result.Add(FIntPoint(i, i + 1));
		}
	}
	return Result;
}

void UCardMergeComponent::AutoOrganizeCards()
{
	if (!CardHand.IsValid()) return;

	// 简单的整理逻辑：将相同角色的牌移到一起
	// 实际项目中可以更智能
	TArray<UCardBase*> AllCards = CardHand->GetAllCards();
	TArray<UCardBase*> ValidCards;
	for (UCardBase* Card : AllCards)
	{
		if (Card) ValidCards.Add(Card);
	}

	// 按角色ID排序
	ValidCards.Sort([](const UCardBase& A, const UCardBase& B) {
		return A.GetCardData().OwnerCharacterID.ToString() < B.GetCardData().OwnerCharacterID.ToString();
	});

	// 重新排列
	CardHand->ClearHand();
	for (UCardBase* Card : ValidCards)
	{
		Card->SetCardState(ECardState::InDeck);
		CardHand->AddCard(Card);
	}

	UE_LOG(LogCard, Log, TEXT("卡牌自动整理完成"));
}

UCardBase* UCardMergeComponent::CreateMergedCard(UCardBase* CardA, UCardBase* CardB)
{
	if (!CardA || !CardB) return nullptr;

	// 复制卡牌数据并提升阶位
	FCardRuntimeData NewData = CardA->GetCardData();
	switch (NewData.CardRank)
	{
	case ECardRank::Rank1:
		NewData.CardRank = ECardRank::Rank2;
		NewData.CardName = FText::FromString(NewData.CardName.ToString() + TEXT("·贰"));
		break;
	case ECardRank::Rank2:
		NewData.CardRank = ECardRank::Rank3;
		NewData.CardName = FText::FromString(NewData.CardName.ToString() + TEXT("·叁"));
		break;
	default:
		return nullptr;
	}

	// 创建新卡牌对象
	UCardBase* NewCard = NewObject<UCardBase>(this, UCardBase::StaticClass());
	if (NewCard)
	{
		NewCard->InitializeCard(NewData, CardA->GetOwnerCharacter());
	}
	return NewCard;
}

bool UCardMergeComponent::AreCardsSame(UCardBase* CardA, UCardBase* CardB) const
{
	if (!CardA || !CardB) return false;

	const FCardRuntimeData& DataA = CardA->GetCardData();
	const FCardRuntimeData& DataB = CardB->GetCardData();

	// 同角色、同名称、同阶位、同类型
	return DataA.OwnerCharacterID == DataB.OwnerCharacterID &&
		DataA.CardName.EqualTo(DataB.CardName) &&
		DataA.CardRank == DataB.CardRank &&
		DataA.CardType == DataB.CardType;
}
