// 时律契约 - 手牌管理器实现
#include "CardSystem/CardHand.h"
#include "CardSystem/CardBase.h"
#include "Character/CharacterBase.h"

UCardHand::UCardHand()
{
	MaxHandSize = 8;
}

void UCardHand::InitializeHand(int32 InMaxHandSize)
{
	MaxHandSize = InMaxHandSize;
	HandCards.SetNumZeroed(MaxHandSize);

	UE_LOG(LogCard, Log, TEXT("手牌管理器初始化，最大手牌数: %d"), MaxHandSize);
}

bool UCardHand::AddCard(UCardBase* NewCard)
{
	if (!NewCard || !HasEmptySlot())
	{
		UE_LOG(LogCard, Warning, TEXT("无法添加卡牌，手牌已满或卡牌无效"));
		return false;
	}

	int32 EmptySlot = GetFirstEmptySlot();
	HandCards[EmptySlot] = NewCard;
	NewCard->SetCardState(ECardState::InHand);
	NewCard->SetHandSlotIndex(EmptySlot);

	UE_LOG(LogCard, Log, TEXT("添加卡牌到位置 %d: %s"), 
		EmptySlot, *NewCard->GetCardData().CardName.ToString());

	OnHandChanged.Broadcast();
	return true;
}

bool UCardHand::RemoveCard(UCardBase* CardToRemove)
{
	if (!CardToRemove) return false;

	int32 Index = GetCardIndex(CardToRemove);
	if (Index == INDEX_NONE) return false;

	HandCards[Index] = nullptr;
	CardToRemove->SetCardState(ECardState::Discarded);
	CardToRemove->SetHandSlotIndex(-1);

	UE_LOG(LogCard, Log, TEXT("移除卡牌: %s"), *CardToRemove->GetCardData().CardName.ToString());

	OnHandChanged.Broadcast();
	return true;
}

UCardBase* UCardHand::RemoveCardAt(int32 SlotIndex)
{
	if (SlotIndex < 0 || SlotIndex >= MaxHandSize || !HandCards[SlotIndex])
	{
		return nullptr;
	}

	UCardBase* Card = HandCards[SlotIndex];
	HandCards[SlotIndex] = nullptr;
	Card->SetCardState(ECardState::Discarded);
	Card->SetHandSlotIndex(-1);

	OnHandChanged.Broadcast();
	return Card;
}

bool UCardHand::MoveCard(int32 FromIndex, int32 ToIndex)
{
	if (FromIndex < 0 || FromIndex >= MaxHandSize || 
		ToIndex < 0 || ToIndex >= MaxHandSize ||
		FromIndex == ToIndex || !HandCards[FromIndex])
	{
		return false;
	}

	// 交换位置
	UCardBase* Temp = HandCards[ToIndex];
	HandCards[ToIndex] = HandCards[FromIndex];
	HandCards[FromIndex] = Temp;

	UpdateCardSlotIndices();

	UE_LOG(LogCard, Log, TEXT("移动卡牌: %d -> %d"), FromIndex, ToIndex);

	OnCardMoved.Broadcast(FromIndex, ToIndex);
	OnHandChanged.Broadcast();
	return true;
}

UCardBase* UCardHand::GetCardAt(int32 SlotIndex) const
{
	if (SlotIndex < 0 || SlotIndex >= MaxHandSize)
	{
		return nullptr;
	}
	return HandCards[SlotIndex];
}

int32 UCardHand::GetCardIndex(UCardBase* Card) const
{
	if (!Card) return INDEX_NONE;
	return HandCards.Find(Card);
}

int32 UCardHand::GetCardCount() const
{
	int32 Count = 0;
	for (UCardBase* Card : HandCards)
	{
		if (Card) Count++;
	}
	return Count;
}

bool UCardHand::HasEmptySlot() const
{
	return GetCardCount() < MaxHandSize;
}

int32 UCardHand::GetFirstEmptySlot() const
{
	for (int32 i = 0; i < MaxHandSize; i++)
	{
		if (!HandCards[i]) return i;
	}
	return INDEX_NONE;
}

void UCardHand::ClearHand()
{
	for (int32 i = 0; i < MaxHandSize; i++)
	{
		if (HandCards[i])
		{
			HandCards[i]->SetCardState(ECardState::Discarded);
			HandCards[i]->SetHandSlotIndex(-1);
			HandCards[i] = nullptr;
		}
	}
	OnHandChanged.Broadcast();
}

TArray<UCardBase*> UCardHand::GetCardsByOwner(ACharacterBase* Owner) const
{
	TArray<UCardBase*> Result;
	for (UCardBase* Card : HandCards)
	{
		if (Card && Card->GetOwnerCharacter() == Owner)
		{
			Result.Add(Card);
		}
	}
	return Result;
}

bool UCardHand::AreCardsAdjacent(int32 IndexA, int32 IndexB) const
{
	return FMath::Abs(IndexA - IndexB) == 1;
}

void UCardHand::UpdateCardSlotIndices()
{
	for (int32 i = 0; i < MaxHandSize; i++)
	{
		if (HandCards[i])
		{
			HandCards[i]->SetHandSlotIndex(i);
		}
	}
}
