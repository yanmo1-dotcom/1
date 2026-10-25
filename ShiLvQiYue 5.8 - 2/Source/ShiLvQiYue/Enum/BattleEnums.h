// 时律契约 - 战斗系统枚举
#pragma once

#include "CoreMinimal.h"
#include "BattleEnums.generated.h"

// 属性类型（六属性克制系统）
UENUM(BlueprintType)
enum class EAttributeType : uint8
{
	Star		UMETA(DisplayName = "星"),
	Rock		UMETA(DisplayName = "岩"),
	Beast		UMETA(DisplayName = "兽"),
	Wood		UMETA(DisplayName = "木"),
	Spirit		UMETA(DisplayName = "灵"),
	Wisdom		UMETA(DisplayName = "智"),
	None		UMETA(DisplayName = "无")
};

// 战斗回合阶段
UENUM(BlueprintType)
enum class EBattlePhase : uint8
{
	None			UMETA(DisplayName = "无"),
	BattleStart		UMETA(DisplayName = "战斗开始"),
	DrawPhase		UMETA(DisplayName = "发牌阶段"),
	PlayerTurn		UMETA(DisplayName = "我方回合"),
	EnemyTurn		UMETA(DisplayName = "敌方回合"),
	StatusPhase		UMETA(DisplayName = "状态结算阶段"),
	BattleEnd		UMETA(DisplayName = "战斗结束")
};

// 战斗结果
UENUM(BlueprintType)
enum class EBattleResult : uint8
{
	None		UMETA(DisplayName = "无"),
	Victory		UMETA(DisplayName = "胜利"),
	Defeat		UMETA(DisplayName = "失败"),
	Draw		UMETA(DisplayName = "平局")
};

// 角色定位
UENUM(BlueprintType)
enum class ECharacterRole : uint8
{
	MainDPS		UMETA(DisplayName = "主输出"),
	SubDPS		UMETA(DisplayName = "副输出"),
	Support		UMETA(DisplayName = "辅助"),
	Healer		UMETA(DisplayName = "治疗"),
	Tank		UMETA(DisplayName = "坦克")
};

// 阵营
UENUM(BlueprintType)
enum class EFaction : uint8
{
	Player		UMETA(DisplayName = "我方"),
	Enemy		UMETA(DisplayName = "敌方"),
	Neutral		UMETA(DisplayName = "中立")
};

// 状态效果类型
UENUM(BlueprintType)
enum class EStatusEffectType : uint8
{
	// 增益
	Shield			UMETA(DisplayName = "护盾"),
	DamageUp		UMETA(DisplayName = "创伤加成"),
	CritRateUp		UMETA(DisplayName = "暴击率提升"),
	PassionUp		UMETA(DisplayName = "激情获取提升"),
	Regeneration	UMETA(DisplayName = "再生"),
	// 减益
	DefenseDown		UMETA(DisplayName = "防御降低"),
	Poison			UMETA(DisplayName = "中毒"),
	Burn			UMETA(DisplayName = "燃烧"),
	Silence			UMETA(DisplayName = "沉默"),
	Stun			UMETA(DisplayName = "眩晕"),
	PassionDown		UMETA(DisplayName = "激情降低"),
	None			UMETA(DisplayName = "无")
};

// 技能目标类型
UENUM(BlueprintType)
enum class ESkillTargetType : uint8
{
	SingleEnemy		UMETA(DisplayName = "单体敌方"),
	AllEnemies		UMETA(DisplayName = "全体敌方"),
	SingleAlly		UMETA(DisplayName = "单体友方"),
	AllAllies		UMETA(DisplayName = "全体友方"),
	Self			UMETA(DisplayName = "自身"),
	None			UMETA(DisplayName = "无")
};
