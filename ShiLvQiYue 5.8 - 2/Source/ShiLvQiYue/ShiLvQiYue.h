// Copyright Epic Games, Inc. All Rights Reserved.

#pragma once

#include "CoreMinimal.h"
#include "Modules/ModuleManager.h"

DECLARE_LOG_CATEGORY_EXTERN(LogSLV, Log, All);
DECLARE_LOG_CATEGORY_EXTERN(LogBattle, Log, All);
DECLARE_LOG_CATEGORY_EXTERN(LogCard, Log, All);
DECLARE_LOG_CATEGORY_EXTERN(LogCharacter, Log, All);

class FShiLvQiYueModule : public IModuleInterface
{
public:
	virtual void StartupModule() override;
	virtual void ShutdownModule() override;
};
