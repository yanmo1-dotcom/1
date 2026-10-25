// Copyright Epic Games, Inc. All Rights Reserved.

using UnrealBuildTool;
using System.Collections.Generic;

public class ShiLvQiYueEditorTarget : TargetRules
{
	public ShiLvQiYueEditorTarget(TargetInfo Target) : base(Target)
	{
		Type = TargetType.Editor;
		DefaultBuildSettings = BuildSettingsVersion.V4;
		IncludeOrderVersion = EngineIncludeOrderVersion.Unreal5_8;
		bOverrideBuildEnvironment = true;
		ExtraModuleNames.Add("ShiLvQiYue");
	}
}
