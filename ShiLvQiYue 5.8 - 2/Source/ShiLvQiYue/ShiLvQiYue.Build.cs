// Copyright Epic Games, Inc. All Rights Reserved.

using UnrealBuildTool;

public class ShiLvQiYue : ModuleRules
{
	public ShiLvQiYue(ReadOnlyTargetRules Target) : base(Target)
	{
		PCHUsage = PCHUsageMode.UseExplicitOrSharedPCHs;
	
		PublicDependencyModuleNames.AddRange(new string[] { 
			"Core", 
			"CoreUObject", 
			"Engine", 
			"InputCore",
			"EnhancedInput",
			"UMG",
			"Slate",
			"SlateCore",
			"Niagara"
		});

		// 添加所有子目录到包含路径
		PublicIncludePaths.AddRange(new string[] {
			ModuleDirectory,
			ModuleDirectory + "/Core",
			ModuleDirectory + "/CardSystem",
			ModuleDirectory + "/Character",
			ModuleDirectory + "/UI",
			ModuleDirectory + "/Data",
			ModuleDirectory + "/Enum"
		});

		PublicDefinitions.Add("ENABLE_LOG_CATEGORY=1");
		PublicDefinitions.Add("BATTLE_DEBUG=1");
	}
}