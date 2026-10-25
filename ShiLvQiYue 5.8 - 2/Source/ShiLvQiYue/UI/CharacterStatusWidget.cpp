// 时律契约 - 角色状态控件实现
#include "UI/CharacterStatusWidget.h"
#include "Character/CharacterBase.h"
#include "Character/PassionComponent.h"
#include "Components/ProgressBar.h"
#include "Components/TextBlock.h"
#include "Components/HorizontalBox.h"
#include "Components/Image.h"

void UCharacterStatusWidget::NativeConstruct()
{
	Super::NativeConstruct();
}

void UCharacterStatusWidget::SetCharacter(ACharacterBase* InCharacter)
{
	Character = InCharacter;
	if (!Character) return;

	// 绑定事件
	Character->OnCharacterDamaged.AddDynamic(this, &UCharacterStatusWidget::OnCharacterDamaged);
	Character->OnCharacterHealed.AddDynamic(this, &UCharacterStatusWidget::OnCharacterHealed);
	Character->OnCharacterDied.AddDynamic(this, &UCharacterStatusWidget::OnCharacterDied);

	UpdateDisplay();
}

void UCharacterStatusWidget::UpdateDisplay()
{
	UpdateHP();
	UpdatePassion();
	UpdateStatusEffects();

	if (CharacterNameText && Character)
	{
		CharacterNameText->SetText(Character->GetCharacterName());
	}
}

void UCharacterStatusWidget::UpdateHP()
{
	if (!Character || !HPBar || !HPText) return;

	const FBaseAttributes& Attrs = Character->GetAttributes();
	float HPPercent = Attrs.MaxHP > 0 ? Attrs.CurrentHP / Attrs.MaxHP : 0.0f;
	HPBar->SetPercent(HPPercent);
	HPText->SetText(FText::FromString(
		FString::Printf(TEXT("%.0f / %.0f"), Attrs.CurrentHP, Attrs.MaxHP)));

	// HP低时变红
	if (HPPercent < 0.3f)
	{
		HPBar->SetFillColorAndOpacity(FLinearColor(0.9f, 0.2f, 0.2f));
	}
	else if (HPPercent < 0.6f)
	{
		HPBar->SetFillColorAndOpacity(FLinearColor(0.9f, 0.7f, 0.2f));
	}
	else
	{
		HPBar->SetFillColorAndOpacity(FLinearColor(0.2f, 0.8f, 0.3f));
	}
}

void UCharacterStatusWidget::UpdatePassion()
{
	if (!Character || !PassionPointsBox) return;

	PassionPointsBox->ClearChildren();

	UPassionComponent* PassionComp = Character->GetPassionComponent();
	if (!PassionComp) return;

	int32 CurrentPassion = PassionComp->GetCurrentPassion();
	int32 MaxPassion = PassionComp->GetMaxPassion();

	// 创建激情点
	for (int32 i = 0; i < MaxPassion; i++)
	{
		UUserWidget* PointWidget = CreateWidget<UUserWidget>(GetWorld(), PassionPointWidgetClass);
		if (PointWidget)
		{
			// TODO: 设置激情点填充状态
			PassionPointsBox->AddChildToHorizontalBox(PointWidget);
		}
	}
}

void UCharacterStatusWidget::UpdateStatusEffects()
{
	if (!Character || !StatusEffectsBox) return;

	StatusEffectsBox->ClearChildren();

	const TArray<FStatusEffectInstance>& Effects = Character->GetAllStatusEffects();
	for (const FStatusEffectInstance& Effect : Effects)
	{
		UUserWidget* EffectWidget = CreateWidget<UUserWidget>(GetWorld(), StatusEffectIconWidgetClass);
		if (EffectWidget)
		{
			// TODO: 设置状态效果图标和持续回合
			StatusEffectsBox->AddChildToHorizontalBox(EffectWidget);
		}
	}
}

void UCharacterStatusWidget::OnCharacterDamaged(ACharacterBase* DamagedCharacter, float Damage)
{
	if (DamagedCharacter == Character)
	{
		UpdateHP();
	}
}

void UCharacterStatusWidget::OnCharacterHealed(ACharacterBase* HealedCharacter, float Healing)
{
	if (HealedCharacter == Character)
	{
		UpdateHP();
	}
}

void UCharacterStatusWidget::OnCharacterDied(ACharacterBase* DeadCharacter)
{
	if (DeadCharacter == Character)
	{
		UpdateHP();
		// TODO: 显示死亡状态
	}
}
