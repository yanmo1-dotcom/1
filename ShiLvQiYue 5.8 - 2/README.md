# 时律契约 (ShiLvQiYue) - UE5回合制二次元RPG

## 项目概述

《时律契约》是一款基于Unreal Engine 5开发的回合制二次元RPG游戏，核心玩法参考《重返未来：1999》的卡牌合成+激情值系统，结合UE5的3D演出能力，打造电影级战斗体验。

### 核心特色
- **卡牌合成系统**：相同卡牌相邻可合成升阶（1阶→2阶→3阶），高阶牌效果更强
- **激情值系统**：出牌/移牌/合牌累积激情值，满5点触发"至终仪式"大招
- **六属性克制**：星/岩/兽/木/灵/智，克制+30%伤害
- **3D电影级演出**：UE5 Sequencer + Niagara + Lumen，每个技能都有专属演出
- **回合制策略**：3首发+1替补，有限行动点，牌序规划是核心乐趣

## 技术栈

- **引擎**：Unreal Engine 5.3+
- **语言**：C++ + 蓝图
- **渲染**：Lumen全局光照 + Nanite虚拟几何体
- **UI**：UMG (Unreal Motion Graphics)
- **特效**：Niagara粒子系统
- **动画**：Sequencer过场动画
- **输入**：Enhanced Input系统

## 项目结构

```
ShiLvQiYue/
├── ShiLvQiYue.uproject          # UE5项目文件
├── Config/                        # 配置文件
│   ├── DefaultEngine.ini         # 引擎配置（渲染、地图等）
│   ├── DefaultGame.ini           # 游戏配置
│   └── DefaultInput.ini          # 输入配置
├── Content/                       # 内容目录（蓝图、资产、地图）
│   ├── Characters/                # 角色资产
│   ├── Cards/                     # 卡牌资产
│   ├── UI/                        # UI控件
│   ├── Maps/                      # 地图
│   └── Data/                      # 数据资产
└── Source/
    ├── ShiLvQiYue/                # 主模块
    │   ├── Core/                  # 核心系统
    │   │   ├── BattleManager      # 战斗管理器（核心）
    │   │   ├── GameInstance_SLV   # 游戏实例
    │   │   └── ...
    │   ├── CardSystem/            # 卡牌系统
    │   │   ├── CardBase           # 卡牌基类
    │   │   ├── CardData           # 卡牌数据结构
    │   │   ├── CardHand           # 手牌管理器（准备区8张）
    │   │   └── CardMergeComponent # 卡牌合成组件
    │   ├── Character/             # 角色系统
    │   │   ├── CharacterBase      # 角色基类
    │   │   ├── PlayerCharacter    # 玩家角色
    │   │   ├── EnemyCharacter     # 敌人角色
    │   │   └── PassionComponent   # 激情值组件
    │   ├── UI/                    # UI系统
    │   │   ├── BattleHUD          # 战斗HUD
    │   │   ├── CardWidget         # 卡牌控件
    │   │   └── CharacterStatusWidget # 角色状态控件
    │   ├── Data/                  # 数据资产
    │   │   ├── CardDataAsset      # 卡牌数据资产
    │   │   ├── CharacterDataAsset # 角色数据资产
    │   │   └── AttributeData      # 属性数据结构
    │   └── Enum/                  # 枚举定义
    │       ├── BattleEnums        # 战斗枚举
    │       └── CardEnums          # 卡牌枚举
    ├── ShiLvQiYue.Target.cs       # 游戏目标配置
    └── ShiLvQiYueEditor.Target.cs # 编辑器目标配置
```

## 核心系统说明

### 1. 战斗系统 (BattleManager)
战斗管理器是整个游戏的核心，负责：
- 战斗阶段管理（发牌→我方回合→敌方回合→状态结算→下一回合）
- 行动点管理（=存活玩家角色数，默认3点）
- 胜负判定
- 角色列表管理
- 手牌管理器持有

### 2. 卡牌系统 (CardSystem)
- **CardBase**：卡牌对象基类，包含卡牌数据、打出逻辑、伤害计算
- **CardHand**：手牌管理器，固定8个槽位，支持添加/移除/移动卡牌
- **CardMergeComponent**：卡牌合成组件，检测相邻相同卡牌并合成升阶
- **合成规则**：同角色+同技能+同阶位+相邻 → 合成升阶，2阶=1阶×1.8，3阶=1阶×2.8

### 3. 激情值系统 (PassionComponent)
- 每个角色独立拥有激情值，上限5点
- 获取方式：出牌+1，移牌+1，合牌+1
- 满5点后下回合发牌阶段抽取"至终仪式"大招牌
- 释放大招后激情值清零
- 大招牌占用手牌槽，不用会卡手

### 4. 角色系统 (Character)
- **CharacterBase**：角色基类，包含属性、状态效果、伤害/治疗、死亡/复活
- **PlayerCharacter**：玩家角色，支持替补席、登场保护
- **EnemyCharacter**：敌人角色，包含AI行为、蓄力系统
- **状态效果**：护盾/增伤/减益/中毒/燃烧/眩晕/沉默等，回合结束时结算

### 5. 属性克制系统
| 属性 | 克制 | 被克制 |
|------|------|--------|
| 星 | 岩 | 木 |
| 岩 | 兽 | 星 |
| 兽 | 木 | 岩 |
| 木 | 星 | 兽 |
| 灵 | 智 | 智（互克） |
| 智 | 灵 | 灵（互克） |

克制伤害+30%，被克制伤害-20%。

## 快速开始

### 环境要求
- Unreal Engine 5.3 或更高版本
- Visual Studio 2022（含游戏开发用C++工作负载）
- Windows 10/11 64位

### 编译步骤
1. 右键点击 `ShiLvQiYue.uproject`，选择"Generate Visual Studio project files"
2. 打开生成的 `ShiLvQiYue.sln`
3. 选择 `Development Editor` 配置，`Win64` 平台
4. 按 `F5` 或点击"本地Windows调试器"编译并启动

### 首次运行
1. 编译完成后UE5编辑器会自动打开
2. 打开 `Content/Maps/BattleMap.umap`（需要创建）
3. 放置 `BP_BattleManager` 蓝图（需要基于ABattleManager创建）
4. 添加玩家角色和敌人角色
5. 点击"播放"按钮开始测试

## 开发路线图

### P0 - 核心战斗验证（当前阶段）
- [x] 项目框架搭建
- [x] 战斗管理器核心逻辑
- [x] 卡牌系统（手牌、合成）
- [x] 激情值系统
- [x] 角色基类和属性系统
- [x] 状态效果系统
- [x] UI框架（HUD、卡牌、状态）
- [x] 数据资产（卡牌、角色）
- [ ] 创建战斗地图和蓝图
- [ ] 创建2个可玩角色（输出+治疗）
- [ ] 创建1个Boss敌人
- [ ] 创建基础卡牌数据
- [ ] 完整可玩的1场Boss战

### P1 - 内容扩展
- [ ] 第3个角色（辅助）+ 替补系统
- [ ] 完整状态效果库
- [ ] 2场普通战斗+1场Boss战
- [ ] 角色养成基础（等级/技能升级）
- [ ] 战斗失败复盘
- [ ] 自动战斗+倍速

### P2 - 完整游戏
- [ ] 章节系统和剧情
- [ ] 完整角色养成（突破/心相/共鸣）
- [ ] 抽卡系统
- [ ] 主界面和章节地图
- [ ] 存档系统
- [ ] 音效和音乐

## 开发规范

### 命名规范
- 类名：PascalCase（如 `ABattleManager`、`UCardBase`）
- 变量：camelCase（如 `currentHP`、`maxActionPoints`）
- 成员变量：`b`前缀表示布尔（如 `bIsDead`）
- 枚举：`E`前缀（如 `EBattlePhase`、`ECardType`）
- 接口：`I`前缀
- 结构体：`F`前缀（如 `FCardRuntimeData`、`FBaseAttributes`）

### 代码规范
- 所有C++类必须继承自UE5基类（AActor、UObject等）
- 使用 `UPROPERTY()` 标记需要在蓝图中访问的变量
- 使用 `UFUNCTION()` 标记需要在蓝图中调用的函数
- 委托使用 `DECLARE_DYNAMIC_MULTICAST_DELEGATE` 宏
- 日志使用自定义日志类别：`LogSLV`、`LogBattle`、`LogCard`、`LogCharacter`

### 蓝图规范
- 蓝图类名：`BP_`前缀（如 `BP_BattleManager`、`BP_PlayerCharacter`）
- 控件蓝图：`WBP_`前缀（如 `WBP_BattleHUD`、`WBP_Card`）
- 数据资产：`DA_`前缀（如 `DA_Card_StarBlade`、`DA_Character_Lin`）
- 材质：`M_`前缀，材质实例：`MI_`前缀
- 粒子系统：`NS_`前缀

## 设计文档

完整的游戏设计文档（GDD）请参考飞书文档：
- [《时律契约》概念设计文档 v0.1](https://my.feishu.cn/docx/XXpEdPpZfohvaqxkt1Xc6E1rnZb)

## 参考作品

- 《重返未来：1999》- 卡牌合成+激情值核心机制
- 《崩坏：星穹铁道》- 3D回合制战斗演出
- 《女神异闻录5》- 回合制战斗风格化演出
- 《碧蓝幻想Versus》- 二次元角色渲染

## 团队

- 游戏设计：时律工作室
- 引擎：Unreal Engine 5
- 版本：0.1.0（概念验证阶段）

## 许可证

本项目仅供学习和研究使用。
