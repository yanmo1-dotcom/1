// Copyright Epic Games, Inc. All Rights Reserved.

#include "ShiLvQiYue.h"

DEFINE_LOG_CATEGORY(LogSLV);
DEFINE_LOG_CATEGORY(LogBattle);
DEFINE_LOG_CATEGORY(LogCard);
DEFINE_LOG_CATEGORY(LogCharacter);

void FShiLvQiYueModule::StartupModule()
{
	UE_LOG(LogSLV, Log, TEXT("时律契约模块启动"));
}

void FShiLvQiYueModule::ShutdownModule()
{
	UE_LOG(LogSLV, Log, TEXT("时律契约模块关闭"));
}

IMPLEMENT_MODULE(FShiLvQiYueModule, ShiLvQiYue)
