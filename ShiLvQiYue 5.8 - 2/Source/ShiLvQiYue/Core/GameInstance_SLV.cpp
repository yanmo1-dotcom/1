// 时律契约 - 游戏实例实现
#include "Core/GameInstance_SLV.h"

UGameInstance_SLV::UGameInstance_SLV()
{
}

void UGameInstance_SLV::Init()
{
	Super::Init();
	UE_LOG(LogSLV, Log, TEXT("游戏实例初始化"));

	// 初始解锁角色（示例）
	UnlockedCharacters.Add(TEXT("Lin"));		// 凛 - 输出
	UnlockedCharacters.Add(TEXT("ZhiYe"));		// 织夜 - 辅助
	UnlockedCharacters.Add(TEXT("HuPo"));		// 琥珀 - 治疗
}

void UGameInstance_SLV::Shutdown()
{
	UE_LOG(LogSLV, Log, TEXT("游戏实例关闭"));
	Super::Shutdown();
}

void UGameInstance_SLV::UnlockCharacter(FName CharacterID)
{
	if (!UnlockedCharacters.Contains(CharacterID))
	{
		UnlockedCharacters.Add(CharacterID);
		UE_LOG(LogSLV, Log, TEXT("解锁新角色: %s"), *CharacterID.ToString());
	}
}

void UGameInstance_SLV::SaveGame()
{
	UE_LOG(LogSLV, Log, TEXT("保存游戏 - 章节: %d, 已解锁角色: %d"), 
		CurrentChapter, UnlockedCharacters.Num());
	// TODO: 实现完整的存档系统
}

void UGameInstance_SLV::LoadGame()
{
	UE_LOG(LogSLV, Log, TEXT("加载游戏"));
	// TODO: 实现完整的读档系统
}
