// 时律契约 - 游戏实例
#pragma once

#include "CoreMinimal.h"
#include "Engine/GameInstance.h"
#include "GameInstance_SLV.generated.h"

UCLASS()
class SHILVQIYUE_API UGameInstance_SLV : public UGameInstance
{
	GENERATED_BODY()

public:
	UGameInstance_SLV();

	virtual void Init() override;
	virtual void Shutdown() override;

	// 获取当前章节
	UFUNCTION(BlueprintPure, Category = "游戏进度")
	int32 GetCurrentChapter() const { return CurrentChapter; }

	// 设置当前章节
	UFUNCTION(BlueprintCallable, Category = "游戏进度")
	void SetCurrentChapter(int32 Chapter) { CurrentChapter = Chapter; }

	// 获取已解锁角色ID列表
	UFUNCTION(BlueprintPure, Category = "角色")
	const TArray<FName>& GetUnlockedCharacters() const { return UnlockedCharacters; }

	// 解锁角色
	UFUNCTION(BlueprintCallable, Category = "角色")
	void UnlockCharacter(FName CharacterID);

	// 保存游戏
	UFUNCTION(BlueprintCallable, Category = "存档")
	virtual void SaveGame();

	// 加载游戏
	UFUNCTION(BlueprintCallable, Category = "存档")
	virtual void LoadGame();

protected:
	// 当前章节
	UPROPERTY(BlueprintReadOnly, Category = "游戏进度")
	int32 CurrentChapter = 1;

	// 已解锁角色ID列表
	UPROPERTY(BlueprintReadOnly, Category = "角色")
	TArray<FName> UnlockedCharacters;

	// 游戏设置
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "设置")
	float MasterVolume = 1.0f;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "设置")
	float MusicVolume = 0.8f;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "设置")
	float SFXVolume = 1.0f;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "设置")
	bool bAutoBattle = false;

	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "设置")
	float BattleSpeed = 1.0f; // 0.5x, 1.0x, 2.0x, 4.0x
};
