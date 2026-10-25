// 时律契约 - 玩家角色实现
#include "Character/PlayerCharacter.h"

APlayerCharacter::APlayerCharacter()
{
	Faction = EFaction::Player;
}

void APlayerCharacter::InitializePlayerCharacter(const FBaseAttributes& InAttributes, EAttributeType InAttribute, ECharacterRole InRole)
{
	InitializeCharacter(InAttributes, InAttribute, InRole);
	bIsInReserve = false;
	bHasEntryProtection = false;
}

void APlayerCharacter::ActivateEntryProtection()
{
	bHasEntryProtection = true;
	UE_LOG(LogCharacter, Log, TEXT("%s 获得登场保护（受到伤害降低50%%）"), *CharacterName.ToString());
}

void APlayerCharacter::ProcessEntryProtection()
{
	if (bHasEntryProtection)
	{
		bHasEntryProtection = false;
		UE_LOG(LogCharacter, Log, TEXT("%s 的登场保护结束"), *CharacterName.ToString());
	}
}
