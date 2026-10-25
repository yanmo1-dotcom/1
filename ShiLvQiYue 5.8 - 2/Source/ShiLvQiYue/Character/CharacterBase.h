// 时律契约 - 角色基类
#pragma once

#include "CoreMinimal.h"
#include "GameFramework/Actor.h"
#include "Data/AttributeData.h"
#include "Enum/BattleEnums.h"
#include "CharacterBase.generated.h"

class UPassionComponent;
class UCardBase;
class ABattleManager;

// 角色死亡委托
DECLARE_DYNAMIC_MULTICAST_DELEGATE_OneParam(FOnCharacterDied, class ACharacterBase*, Character);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_TwoParams(FOnCharacterDamaged, class ACharacterBase*, Character, float, Damage);
DECLARE_DYNAMIC_MULTICAST_DELEGATE_TwoParams(FOnCharacterHealed, class ACharacterBase*, Character, float, Healing);

UCLASS(Abstract, Blueprintable)
class SHILVQIYUE_API ACharacterBase : public AActor
{
	GENERATED_BODY()

public:
	ACharacterBase();

	// 初始化角色
	UFUNCTION(BlueprintCallable, Category = "角色")
	virtual void InitializeCharacter(const FBaseAttributes& InAttributes, EAttributeType InAttribute, ECharacterRole InRole);

	// 受到伤害
	UFUNCTION(BlueprintCallable, Category = "角色")
	virtual float TakeDamage(float DamageAmount, ACharacterBase* DamageSource);

	// 治疗
	UFUNCTION(BlueprintCallable, Category = "角色")
	virtual float Heal(float HealingAmount);

	// 死亡
	UFUNCTION(BlueprintCallable, Category = "角色")
	virtual void Die();

	// 复活
	UFUNCTION(BlueprintCallable, Category = "角色")
	virtual void Revive(float HealthPercent = 0.5f);

	// 是否死亡
	UFUNCTION(BlueprintPure, Category = "角色")
	bool IsDead() const { return bIsDead; }

	// 添加状态效果
	UFUNCTION(BlueprintCallable, Category = "状态效果")
	virtual void AddStatusEffect(const FStatusEffectInstance& Effect);

	// 移除状态效果
	UFUNCTION(BlueprintCallable, Category = "状态效果")
	virtual void RemoveStatusEffect(EStatusEffectType EffectType);

	// 获取所有状态效果
	UFUNCTION(BlueprintPure, Category = "状态效果")
	const TArray<FStatusEffectInstance>& GetAllStatusEffects() const { return ActiveStatusEffects; }

	// 是否有指定状态效果
	UFUNCTION(BlueprintPure, Category = "状态效果")
	bool HasStatusEffect(EStatusEffectType EffectType) const;

	// 回合结束时结算状态效果
	UFUNCTION(BlueprintCallable, Category = "状态效果")
	virtual void ProcessStatusEffectsAtTurnEnd();

	// 获取属性
	UFUNCTION(BlueprintPure, Category = "角色")
	const FBaseAttributes& GetAttributes() const { return Attributes; }

	// 获取角色名称
	UFUNCTION(BlueprintPure, Category = "角色")
	FText GetCharacterName() const { return CharacterName; }

	// 获取角色属性
	UFUNCTION(BlueprintPure, Category = "角色")
	EAttributeType GetCharacterAttribute() const { return CharacterAttribute; }

	// 获取弱点属性（敌人用，玩家角色返回None）
	UFUNCTION(BlueprintPure, Category = "角色")
	virtual EAttributeType GetWeakAttribute() const { return WeakAttribute; }

	// 获取角色定位
	UFUNCTION(BlueprintPure, Category = "角色")
	ECharacterRole GetCharacterRole() const { return CharacterRole; }

	// 获取阵营
	UFUNCTION(BlueprintPure, Category = "角色")
	EFaction GetFaction() const { return Faction; }

	// 获取激情组件
	UFUNCTION(BlueprintPure, Category = "激情值")
	UPassionComponent* GetPassionComponent() const { return PassionComponent; }

	// 增加激情值
	UFUNCTION(BlueprintCallable, Category = "激情值")
	int32 AddPassion(int32 Amount = 1);

	// 获取当前激情值
	UFUNCTION(BlueprintPure, Category = "激情值")
	int32 GetCurrentPassion() const;

	// 获取角色牌库（所有可用卡牌模板）
	UFUNCTION(BlueprintPure, Category = "卡牌")
	const TArray<class UCardDataAsset*>& GetCardDeck() const { return CardDeck; }

	// 设置角色牌库
	UFUNCTION(BlueprintCallable, Category = "卡牌")
	void SetCardDeck(const TArray<class UCardDataAsset*>& InDeck) { CardDeck = InDeck; }

	// 角色死亡事件
	UPROPERTY(BlueprintAssignable, Category = "角色事件")
	FOnCharacterDied OnCharacterDied;

	// 角色受伤事件
	UPROPERTY(BlueprintAssignable, Category = "角色事件")
	FOnCharacterDamaged OnCharacterDamaged;

	// 角色被治疗事件
	UPROPERTY(BlueprintAssignable, Category = "角色事件")
	FOnCharacterHealed OnCharacterHealed;

protected:
	virtual void BeginPlay() override;

	// 角色名称
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色")
	FText CharacterName;

	// 角色属性
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色")
	EAttributeType CharacterAttribute = EAttributeType::None;

	// 弱点属性（敌人用）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色")
	EAttributeType WeakAttribute = EAttributeType::None;

	// 角色定位
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色")
	ECharacterRole CharacterRole = ECharacterRole::MainDPS;

	// 阵营
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "角色")
	EFaction Faction = EFaction::Player;

	// 基础属性
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "属性")
	FBaseAttributes Attributes;

	// 是否死亡
	UPROPERTY(BlueprintReadOnly, Category = "角色")
	bool bIsDead = false;

	// 激情值组件
	UPROPERTY(VisibleAnywhere, BlueprintReadOnly, Category = "组件")
	UPassionComponent* PassionComponent;

	// 激活的状态效果列表
	UPROPERTY(BlueprintReadOnly, Category = "状态效果")
	TArray<FStatusEffectInstance> ActiveStatusEffects;

	// 角色牌库（卡牌模板）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "卡牌")
	TArray<class UCardDataAsset*> CardDeck;

	// 战斗管理器引用
	UPROPERTY(BlueprintReadOnly, Category = "战斗")
	TWeakObjectPtr<ABattleManager> BattleManager;

	// 应用护盾（吸收伤害）
	virtual float ApplyShield(float DamageAmount);

	// 计算实际伤害（考虑防御、减伤等）
	virtual float CalculateIncomingDamage(float BaseDamage);
};
