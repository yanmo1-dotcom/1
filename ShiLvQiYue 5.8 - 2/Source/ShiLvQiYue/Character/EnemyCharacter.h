// 时律契约 - 敌人角色
#pragma once

#include "CoreMinimal.h"
#include "Character/CharacterBase.h"
#include "EnemyCharacter.generated.h"

// 敌人AI行为类型
UENUM(BlueprintType)
enum class EEnemyAIBahavior : uint8
{
	NormalAttack	UMETA(DisplayName = "普通攻击"),
	Skill			UMETA(DisplayName = "技能"),
	Charge			UMETA(DisplayName = "蓄力"),
	Defend			UMETA(DisplayName = "防御"),
	Heal			UMETA(DisplayName = "治疗"),
	Buff			UMETA(DisplayName = "增益"),
	None			UMETA(DisplayName = "无")
};

UCLASS(Blueprintable)
class SHILVQIYUE_API AEnemyCharacter : public ACharacterBase
{
	GENERATED_BODY()

public:
	AEnemyCharacter();

	// 初始化敌人
	UFUNCTION(BlueprintCallable, Category = "敌人")
	virtual void InitializeEnemy(const FBaseAttributes& InAttributes, EAttributeType InAttribute, EAttributeType InWeakAttribute);

	// 执行AI行动
	UFUNCTION(BlueprintCallable, Category = "敌人AI")
	virtual void ExecuteAIAction();

	// 获取当前蓄力值
	UFUNCTION(BlueprintPure, Category = "敌人")
	int32 GetCurrentCharge() const { return CurrentCharge; }

	// 获取最大蓄力值
	UFUNCTION(BlueprintPure, Category = "敌人")
	int32 GetMaxCharge() const { return MaxCharge; }

	// 增加蓄力
	UFUNCTION(BlueprintCallable, Category = "敌人")
	void AddCharge(int32 Amount = 1);

	// 重置蓄力
	UFUNCTION(BlueprintCallable, Category = "敌人")
	void ResetCharge();

	// 是否蓄力满（下回合释放大招）
	UFUNCTION(BlueprintPure, Category = "敌人")
	bool IsChargeFull() const { return CurrentCharge >= MaxCharge; }

	// 获取下一个AI行为
	UFUNCTION(BlueprintPure, Category = "敌人AI")
	virtual EEnemyAIBahavior GetNextAIAction() const;

protected:
	// 当前蓄力值
	UPROPERTY(BlueprintReadOnly, Category = "敌人")
	int32 CurrentCharge = 0;

	// 最大蓄力值（达到后释放强力技能）
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "敌人")
	int32 MaxCharge = 3;

	// 普通攻击伤害
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "敌人")
	float NormalAttackDamage = 100.0f;

	// 蓄力技能伤害
	UPROPERTY(EditAnywhere, BlueprintReadWrite, Category = "敌人")
	float ChargeSkillDamage = 300.0f;

	// 执行普通攻击
	virtual void ExecuteNormalAttack();

	// 执行蓄力技能
	virtual void ExecuteChargeSkill();

	// 选择攻击目标
	virtual ACharacterBase* SelectAttackTarget() const;
};
