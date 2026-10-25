// 时律契约 - 战斗管理器（核心）
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "Enum/BattleEnums.h"
#include "BattleManager.generated.h"

class UCardHand;
class UCardBase;
class ACharacterBase;
class APlayerCharacter;
class AEnemyCharacter;
class UBattleHUD;

// 战斗阶段变化委托
DECLARE_DYNAMIC_MULTICAST_DELEGATE_TwoParams(FOnBattlePhaseChanged, EBattlePhase, OldPhase, EBattlePhase, NewPhase);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FOnBattleStarted, class ABattleManager*, BattleManager);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FOnBattleEnded, EBattleResult, Result);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FOnTurnStarted, int32, TurnNumber);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FOnActionPointsChanged, int32, NewActionPoints);

UCLASS(Blueprintable)
class SHILVQIYUE_API ABattleManager : public AActor
{
	GENERATED_BODY()

public:
	ABattleManager();

	// 开始战斗
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual void StartBattle();

	// 结束战斗
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual void EndBattle(EBattleResult Result);

	// 切换战斗阶段
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual void SetBattlePhase(EBattlePhase NewPhase);

	// 发牌阶段
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual void ProcessDrawPhase();

	// 开始玩家回合
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual void StartPlayerTurn();

	// 结束玩家回合
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual void EndPlayerTurn();

	// 开始敌人回合
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual void StartEnemyTurn();

	// 结束敌人回合
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual void EndEnemyTurn();

	// 状态结算阶段
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual void ProcessStatusPhase();

	// 打出卡牌
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual bool PlayCard(UCardBase* Card, ACharacterBase* Target);

	// 消耗行动点
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual bool ConsumeActionPoint(int32 Amount = 1);

	// 检查胜负
	UFUNCTION(BlueprintCallable, Category = "战斗")
	virtual EBattleResult CheckBattleResult();

	// 获取当前战斗阶段
	UFUNCTION(BlueprintPure, Category = "战斗")
	EBattlePhase GetCurrentPhase() const { return CurrentPhase; }

	// 获取当前回合数
	UFUNCTION(BlueprintPure, Category = "战斗")
	int32 GetCurrentTurn() const { return CurrentTurn; }

	// 获取当前行动点
	UFUNCTION(BlueprintPure, Category = "战斗")
	int32 GetCurrentActionPoints() const { return CurrentActionPoints; }

	// 获取最大行动点
	UFUNCTION(BlueprintPure, Category = "战斗")
	int32 GetMaxActionPoints() const { return MaxActionPoints; }

	// 获取手牌管理器
	UFUNCTION(BlueprintPure, Category = "卡牌")
	UCardHand* GetCardHand() const { return CardHand; }

	// 获取玩家角色列表
	UFUNCTION(BlueprintPure, Category = "角色")
	const TArray<ACharacterBase*>& GetPlayerCharacters() const { return PlayerCharacters; }

	// 获取敌人角色列表
	UFUNCTION(BlueprintPure, Category = "角色")
	const TArray<ACharacterBase*>& GetEnemyCharacters() const { return EnemyCharacters; }

	// 获取所有存活的玩家角色
	UFUNCTION(BlueprintPure, Category = "角色")
	TArray<ACharacterBase*> GetAlivePlayerCharacters() const;

	// 获取所有存活的敌人角色
	UFUNCTION(BlueprintPure, Category = "角色")
	TArray<ACharacterBase*> GetAliveEnemyCharacters() const;

	// 添加玩家角色
	UFUNCTION(BlueprintCallable, Category = "角色")
	void AddPlayerCharacter(ACharacterBase* Character);

	// 添加敌人角色
	UFUNCTION(BlueprintCallable, Category = "角色")
	void AddEnemyCharacter(ACharacterBase* Character);

	// 战斗开始事件
	UPROPERTY(BlueprintAssignable, Category = "战斗事件")
	FOnBattleStarted OnBattleStarted;

	// 战斗结束事件
	UPROPERTY(BlueprintAssignable, Category = "战斗事件")
	FOnBattleEnded OnBattleEnded;

	// 战斗阶段变化事件
	UPROPERTY(BlueprintAssignable, Category = "战斗事件")
	FOnBattlePhaseChanged OnBattlePhaseChanged;

	// 回合开始事件
	UPROPERTY(BlueprintAssignable, Category = "战斗事件")
	FOnTurnStarted OnTurnStarted;

	// 行动点变化事件
	UPROPERTY(BlueprintAssignable, Category = "战斗事件")
	FOnActionPointsChanged OnActionPointsChanged;

protected:
	virtual void BeginPlay() override;

	// 当前战斗阶段
	UPROPERTY(BlueprintReadOnly, Category = "战斗")
	EBattlePhase CurrentPhase = EBattlePhase::None;

	// 当前回合数
	UPROPERTY(BlueprintReadOnly, Category = "战斗")
	int32 CurrentTurn = 0;

	// 当前行动点
	UPROPERTY(BlueprintReadOnly, Category = "战斗")
	int32 CurrentActionPoints = 3;

	// 最大行动点（=上场玩家角色数）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "战斗")
	int32 MaxActionPoints = 3;

	// 手牌管理器
	UPROPERTY(BlueprintReadOnly, Category = "卡牌")
	UCardHand* CardHand;

	// 玩家角色列表
	UPROPERTY(BlueprintReadOnly, Category = "角色")
	TArray<ACharacterBase*> PlayerCharacters;

	// 敌人角色列表
	UPROPERTY(BlueprintReadOnly, Category = "角色")
	TArray<ACharacterBase*> EnemyCharacters;

	// 战斗HUD
	UPROPERTY(BlueprintReadOnly, Category = "UI")
	UBattleHUD* BattleHUD;

	// 从牌库抽牌
	virtual UCardBase* DrawCardFromDeck(ACharacterBase* Owner);

	// 为所有角色发牌
	virtual void DrawCardsForAllCharacters();

	// 检查是否所有玩家都行动完毕
	virtual bool IsPlayerTurnComplete() const;

	// 角色死亡处理
	UFUNCTION()
	virtual void OnCharacterDied(ACharacterBase* Character);
};
