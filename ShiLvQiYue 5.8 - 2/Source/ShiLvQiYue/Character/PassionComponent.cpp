// 时律契约 - 激情值组件实现
#include "Character/PassionComponent.h"

UPassionComponent::UPassionComponent()
{
	PrimaryComponentTick.bCanEverTick = false;
	CurrentPassion = 0;
	MaxPassion = 5;
}

void UPassionComponent::InitializePassion(int32 InMaxPassion)
{
	MaxPassion = InMaxPassion;
	CurrentPassion = 0;
	bHasUltimateCardDrawn = false;

	UE_LOG(LogCharacter, Log, TEXT("激情组件初始化，最大激情值: %d"), MaxPassion);
}

int32 UPassionComponent::AddPassion(int32 Amount)
{
	if (Amount <= 0 || IsPassionMaxed())
	{
		return CurrentPassion;
	}

	// 应用激情获取加成
	int32 ActualAmount = FMath::RoundToInt(Amount * (1.0f + PassionGainBonus));
	int32 OldValue = CurrentPassion;
	CurrentPassion = FMath::Min(CurrentPassion + ActualAmount, MaxPassion);
	int32 Delta = CurrentPassion - OldValue;

	UE_LOG(LogCharacter, Log, TEXT("激情值增加: %d -> %d (增加 %d)"), OldValue, CurrentPassion, Delta);

	OnPassionChanged.Broadcast(CurrentPassion, Delta);

	if (IsPassionMaxed() && !bHasUltimateCardDrawn)
	{
		UE_LOG(LogCharacter, Log, TEXT("激情值已满，下回合将抽取至终仪式"));
		OnPassionMaxed.Broadcast();
	}

	return CurrentPassion;
}

int32 UPassionComponent::RemovePassion(int32 Amount)
{
	if (Amount <= 0) return CurrentPassion;

	int32 OldValue = CurrentPassion;
	CurrentPassion = FMath::Max(CurrentPassion - Amount, 0);
	int32 Delta = CurrentPassion - OldValue;

	UE_LOG(LogCharacter, Log, TEXT("激情值减少: %d -> %d (减少 %d)"), OldValue, CurrentPassion, -Delta);

	OnPassionChanged.Broadcast(CurrentPassion, Delta);
	return CurrentPassion;
}

void UPassionComponent::ResetPassion()
{
	int32 OldValue = CurrentPassion;
	CurrentPassion = 0;
	bHasUltimateCardDrawn = false;

	UE_LOG(LogCharacter, Log, TEXT("激情值重置: %d -> 0 (释放至终仪式)"), OldValue);

	OnPassionChanged.Broadcast(0, -OldValue);
	OnPassionReset.Broadcast();
}
