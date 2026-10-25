// 时律契约 - 战斗管理器实现
#include "Core/BattleManager.h"
#include "CardSystem/CardHand.h"
#include "CardSystem/CardBase.h"
#include "Character/CharacterBase.h"
#include "Character/PlayerCharacter.h"
#include "Character/EnemyCharacter.h"
#include "UI/BattleHUD.h"
#include "Data/CardDataAsset.h"

ABattleManager::ABattleManager()
{
	PrimaryActorTick.bCanEverTick = false;
}

void ABattleManager::BeginPlay()
{
	Super::BeginPlay();

	// 创建手牌管理器
	CardHand = NewObject<UCardHand>(this, UCardHand::StaticClass());
	if (CardHand)
	{
		CardHand->InitializeHand(8);
	}
}

void ABattleManager::StartBattle()
{
	UE_LOG(LogBattle, Log, TEXT("=== 战斗开始 ==="));
	UE_LOG(LogBattle, Log, TEXT("玩家角色数: %d, 敌人角色数: %d"), 
		PlayerCharacters.Num(), EnemyCharacters.Num());

	CurrentTurn = 0;
	CurrentPhase = EBattlePhase::BattleStart;

	// 绑定角色死亡事件
	for (ACharacterBase* Character : PlayerCharacters)
	{
		if (Character)
		{
			Character->OnCharacterDied.AddDynamic(this, &ABattleManager::OnCharacterDied);
		}
	}
	for (ACharacterBase* Character : EnemyCharacters)
	{
		if (Character)
		{
			Character->OnCharacterDied.AddDynamic(this, &ABattleManager::OnCharacterDied);
		}
	}

	OnBattleStarted.Broadcast(this);

	// 进入发牌阶段，开始第一回合
	ProcessDrawPhase();
}

void ABattleManager::EndBattle(EBattleResult Result)
{
	UE_LOG(LogBattle, Log, TEXT("=== 战斗结束，结果: %d ==="), (int32)Result);
	SetBattlePhase(EBattlePhase::BattleEnd);
	OnBattleEnded.Broadcast(Result);
}

void ABattleManager::SetBattlePhase(EBattlePhase NewPhase)
{
	if (CurrentPhase == NewPhase) return;

	EBattlePhase OldPhase = CurrentPhase;
	CurrentPhase = NewPhase;

	UE_LOG(LogBattle, Log, TEXT("战斗阶段变化: %d -> %d"), (int32)OldPhase, (int32)NewPhase);
	OnBattlePhaseChanged.Broadcast(OldPhase, NewPhase);
}

void ABattleManager::ProcessDrawPhase()
{
	SetBattlePhase(EBattlePhase::DrawPhase);
	CurrentTurn++;

	UE_LOG(LogBattle, Log, TEXT("--- 第 %d 回合，发牌阶段 ---"), CurrentTurn);

	// 补充手牌至8张
	DrawCardsForAllCharacters();

	OnTurnStarted.Broadcast(CurrentTurn);

	// 进入玩家回合
	StartPlayerTurn();
}

void ABattleManager::StartPlayerTurn()
{
	SetBattlePhase(EBattlePhase::PlayerTurn);

	// 重置行动点（=存活玩家角色数）
	MaxActionPoints = GetAlivePlayerCharacters().Num();
	CurrentActionPoints = MaxActionPoints;

	UE_LOG(LogBattle, Log, TEXT("玩家回合开始，行动点: %d/%d"), CurrentActionPoints, MaxActionPoints);
	OnActionPointsChanged.Broadcast(CurrentActionPoints);
}

void ABattleManager::EndPlayerTurn()
{
	UE_LOG(LogBattle, Log, TEXT("玩家回合结束"));

	// 检查胜负
	EBattleResult Result = CheckBattleResult();
	if (Result != EBattleResult::None)
	{
		EndBattle(Result);
		return;
	}

	// 进入敌人回合
	StartEnemyTurn();
}

void ABattleManager::StartEnemyTurn()
{
	SetBattlePhase(EBattlePhase::EnemyTurn);
	UE_LOG(LogBattle, Log, TEXT("敌人回合开始"));

	// 所有存活敌人依次行动
	for (ACharacterBase* Character : EnemyCharacters)
	{
		if (Character && !Character->IsDead())
		{
			AEnemyCharacter* Enemy = Cast<AEnemyCharacter>(Character);
			if (Enemy)
			{
				Enemy->ExecuteAIAction();
			}
		}
	}

	EndEnemyTurn();
}

void ABattleManager::EndEnemyTurn()
{
	UE_LOG(LogBattle, Log, TEXT("敌人回合结束"));

	// 检查胜负
	EBattleResult Result = CheckBattleResult();
	if (Result != EBattleResult::None)
	{
		EndBattle(Result);
		return;
	}

	// 状态结算
	ProcessStatusPhase();
}

void ABattleManager::ProcessStatusPhase()
{
	SetBattlePhase(EBattlePhase::StatusPhase);
	UE_LOG(LogBattle, Log, TEXT("状态结算阶段"));

	// 所有角色结算状态效果
	for (ACharacterBase* Character : PlayerCharacters)
	{
		if (Character && !Character->IsDead())
		{
			Character->ProcessStatusEffectsAtTurnEnd();
		}
	}
	for (ACharacterBase* Character : EnemyCharacters)
	{
		if (Character && !Character->IsDead())
		{
			Character->ProcessStatusEffectsAtTurnEnd();
		}
	}

	// 检查胜负
	EBattleResult Result = CheckBattleResult();
	if (Result != EBattleResult::None)
	{
		EndBattle(Result);
		return;
	}

	// 进入下一回合发牌阶段
	ProcessDrawPhase();
}

bool ABattleManager::PlayCard(UCardBase* Card, ACharacterBase* Target)
{
	if (!Card || CurrentPhase != EBattlePhase::PlayerTurn)
	{
		UE_LOG(LogBattle, Warning, TEXT("无法打出卡牌：阶段不正确或卡牌无效"));
		return false;
	}

	if (CurrentActionPoints <= 0)
	{
		UE_LOG(LogBattle, Warning, TEXT("无法打出卡牌：行动点不足"));
		return false;
	}

	// 打出卡牌
	bool bSuccess = Card->PlayCard(Target);
	if (!bSuccess)
	{
		return false;
	}

	// 从手牌中移除
	if (CardHand)
	{
		CardHand->RemoveCard(Card);
	}

	// 消耗行动点
	ConsumeActionPoint(1);

	// 出牌给所属角色+1激情
	if (Card->GetOwnerCharacter())
	{
		Card->GetOwnerCharacter()->AddPassion(1);
	}

	// 检查胜负
	EBattleResult Result = CheckBattleResult();
	if (Result != EBattleResult::None)
	{
		EndBattle(Result);
	}

	return true;
}

bool ABattleManager::ConsumeActionPoint(int32 Amount)
{
	if (CurrentActionPoints < Amount)
	{
		return false;
	}

	CurrentActionPoints -= Amount;
	UE_LOG(LogBattle, Log, TEXT("消耗行动点: %d，剩余: %d/%d"), Amount, CurrentActionPoints, MaxActionPoints);
	OnActionPointsChanged.Broadcast(CurrentActionPoints);

	// 行动点耗尽自动结束回合
	if (CurrentActionPoints <= 0)
	{
		UE_LOG(LogBattle, Log, TEXT("行动点耗尽，自动结束玩家回合"));
		EndPlayerTurn();
	}

	return true;
}

EBattleResult ABattleManager::CheckBattleResult()
{
	// 检查玩家是否全灭
	bool bAllPlayersDead = true;
	for (ACharacterBase* Character : PlayerCharacters)
	{
		if (Character && !Character->IsDead())
		{
			bAllPlayersDead = false;
			break;
		}
	}

	if (bAllPlayersDead)
	{
		UE_LOG(LogBattle, Log, TEXT("所有玩家角色死亡，战斗失败"));
		return EBattleResult::Defeat;
	}

	// 检查敌人是否全灭
	bool bAllEnemiesDead = true;
	for (ACharacterBase* Character : EnemyCharacters)
	{
		if (Character && !Character->IsDead())
		{
			bAllEnemiesDead = false;
			break;
		}
	}

	if (bAllEnemiesDead)
	{
		UE_LOG(LogBattle, Log, TEXT("所有敌人死亡，战斗胜利"));
		return EBattleResult::Victory;
	}

	return EBattleResult::None;
}

TArray<ACharacterBase*> ABattleManager::GetAlivePlayerCharacters() const
{
	TArray<ACharacterBase*> Result;
	for (ACharacterBase* Character : PlayerCharacters)
	{
		if (Character && !Character->IsDead())
		{
			Result.Add(Character);
		}
	}
	return Result;
}

TArray<ACharacterBase*> ABattleManager::GetAliveEnemyCharacters() const
{
	TArray<ACharacterBase*> Result;
	for (ACharacterBase* Character : EnemyCharacters)
	{
		if (Character && !Character->IsDead())
		{
			Result.Add(Character);
		}
	}
	return Result;
}

void ABattleManager::AddPlayerCharacter(ACharacterBase* Character)
{
	if (Character)
	{
		PlayerCharacters.Add(Character);
		UE_LOG(LogBattle, Log, TEXT("添加玩家角色: %s"), *Character->GetCharacterName().ToString());
	}
}

void ABattleManager::AddEnemyCharacter(ACharacterBase* Character)
{
	if (Character)
	{
		EnemyCharacters.Add(Character);
		UE_LOG(LogBattle, Log, TEXT("添加敌人角色: %s"), *Character->GetCharacterName().ToString());
	}
}

UCardBase* ABattleManager::DrawCardFromDeck(ACharacterBase* Owner)
{
	if (!Owner || Owner->GetCardDeck().Num() == 0)
	{
		return nullptr;
	}

	// 从牌库随机抽一张
	int32 RandomIndex = FMath::RandRange(0, Owner->GetCardDeck().Num() - 1);
	UCardDataAsset* CardData = Owner->GetCardDeck()[RandomIndex];
	if (!CardData)
	{
		return nullptr;
	}

	// 创建卡牌实例
	UCardBase* NewCard = NewObject<UCardBase>(this, UCardBase::StaticClass());
	if (NewCard)
	{
		FCardRuntimeData RuntimeData = CardData->GetRuntimeData();
		RuntimeData.OwnerCharacterID = Owner->GetFName(); // 简化处理
		NewCard->InitializeCard(RuntimeData, Owner);
	}

	return NewCard;
}

void ABattleManager::DrawCardsForAllCharacters()
{
	if (!CardHand) return;

	// 补充手牌至8张
	while (CardHand->GetCardCount() < 8)
	{
		// 随机选择一个存活的玩家角色抽牌
		TArray<ACharacterBase*> AlivePlayers = GetAlivePlayerCharacters();
		if (AlivePlayers.Num() == 0) break;

		int32 RandomIndex = FMath::RandRange(0, AlivePlayers.Num() - 1);
		UCardBase* NewCard = DrawCardFromDeck(AlivePlayers[RandomIndex]);
		if (NewCard)
		{
			CardHand->AddCard(NewCard);
		}
		else
		{
			break;
		}
	}

	UE_LOG(LogBattle, Log, TEXT("发牌完成，当前手牌数: %d/8"), CardHand->GetCardCount());
}

bool ABattleManager::IsPlayerTurnComplete() const
{
	return CurrentActionPoints <= 0;
}

void ABattleManager::OnCharacterDied(ACharacterBase* Character)
{
	if (!Character) return;

	UE_LOG(LogBattle, Log, TEXT("角色死亡事件: %s"), *Character->GetCharacterName().ToString());

	// 检查胜负
	EBattleResult Result = CheckBattleResult();
	if (Result != EBattleResult::None)
	{
		EndBattle(Result);
	}
}
