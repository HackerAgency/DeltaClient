package aethereal.module;
import aethereal.annotation.Aliases;
import aethereal.event.ArmRenderEvent;
import aethereal.ui.setting.BooleanSetting;
import aethereal.event.EquipAnimationEvent;
import aethereal.event.EquipAnimationEvent2;
import aethereal.ui.setting.ExpandableSetting;
import aethereal.Expensive;
import aethereal.event.HandSwingEvent;
import aethereal.event.HeldItemRenderEvent;
import aethereal.Lang;
import aethereal.type.Mc;
import aethereal.ui.setting.ModeSetting;
import aethereal.ui.ModuleTab;
import aethereal.ui.setting.NumberSetting;
import aethereal.type.SwingAnimationType;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

@Aliases(aliases = {"Arm Tweaks", "Hand Tweaks", "Swing Animation", "View Model"})
public class ArmTweaksModule extends Module {
    public final ExpandableSetting swingAnimationGroup;
    public final BooleanSetting onlyWhenAura;
    public final ModeSetting<SwingAnimationType> swingAnimationType;
    public final NumberSetting swingPower;
    public final NumberSetting swingSpeed;
    public final NumberSetting swingInclination;
    public final ExpandableSetting viewModelGroup;
    public final BooleanSetting moveBothHands;
    public final NumberSetting translateX;
    public final NumberSetting translateY;
    public final NumberSetting translateZ;
    public final NumberSetting rightHandTranslateX;
    public final NumberSetting leftHandTranslateX;
    public final NumberSetting rightHandTranslateY;
    public final NumberSetting leftHandTranslateY;
    public final NumberSetting rightHandTranslateZ;
    public final NumberSetting leftHandTranslateZ;
    public final Mc mc;

    public ArmTweaksModule() {
        super(ModuleTab.RENDER, "Arm Tweaks");
        this.swingAnimationGroup = new ExpandableSetting(Lang.ARM_TWEAKS_SWING_ANIMATION, Lang.ARM_TWEAKS_SWING_ANIMATION_DESC);
        this.onlyWhenAura = new BooleanSetting(Lang.ARM_TWEAKS_SWING_ANIMATION_ONLY_AURA);
        this.swingAnimationType = new ModeSetting(Lang.ARM_TWEAKS_SWING_ANIMATION_TYPE).values(SwingAnimationType.class);
        this.swingPower = new NumberSetting(Lang.ARM_TWEAKS_SWING_ANIMATION_POWER).currentValue(15.0f).range(1.0f, 15.0f).step(0.05f);
        this.swingSpeed = new NumberSetting(Lang.ARM_TWEAKS_SWING_ANIMATION_SPEED).currentValue(2.0f).range(1.0f, 10.0f).step(1.0f);
        this.swingInclination = new NumberSetting(Lang.ARM_TWEAKS_SWING_ANIMATION_INCLINATION).currentValue(25.0f).range(10.0f, 70.0f).step(1.0f).visible(() -> {
            return Boolean.valueOf(this.swingAnimationType.isSelected(SwingAnimationType.TYPE_2));
        });
        this.viewModelGroup = new ExpandableSetting(Lang.ARM_TWEAKS_VIEW_MODEL, Lang.ARM_TWEAKS_VIEW_MODEL_DESC);
        this.moveBothHands = new BooleanSetting(Lang.ARM_TWEAKS_VIEW_MODEL_MOVE_BOTH_HANDS).setValue(true);
        NumberSetting class613VarStep = new NumberSetting(Lang.ARM_TWEAKS_VIEW_MODEL_TRANSLATE_X, Lang.ARM_TWEAKS_VIEW_MODEL_TRANSLATE_X_DESC).currentValue(0.0f).range(-2.0f, 2.0f).step(0.1f);
        BooleanSetting class665Var = this.moveBothHands;
        Objects.requireNonNull(class665Var);
        this.translateX = class613VarStep.visible(class665Var::isValue);
        NumberSetting class613VarStep2 = new NumberSetting(Lang.ARM_TWEAKS_VIEW_MODEL_TRANSLATE_Y, Lang.ARM_TWEAKS_VIEW_MODEL_TRANSLATE_Y_DESC).currentValue(0.0f).range(-2.0f, 2.0f).step(0.1f);
        BooleanSetting class665Var2 = this.moveBothHands;
        Objects.requireNonNull(class665Var2);
        this.translateY = class613VarStep2.visible(class665Var2::isValue);
        NumberSetting class613VarStep3 = new NumberSetting(Lang.ARM_TWEAKS_VIEW_MODEL_TRANSLATE_Z, Lang.ARM_TWEAKS_VIEW_MODEL_TRANSLATE_Z_DESC).currentValue(0.0f).range(-2.0f, 2.0f).step(0.1f);
        BooleanSetting class665Var3 = this.moveBothHands;
        Objects.requireNonNull(class665Var3);
        this.translateZ = class613VarStep3.visible(class665Var3::isValue);
        this.rightHandTranslateX = new NumberSetting(Lang.ARM_TWEAKS_VIEW_MODEL_RIGHT_HAND_TRANSLATE_X).currentValue(0.0f).range(-2.0f, 2.0f).step(0.1f).visible(() -> {
            return Boolean.valueOf(!this.moveBothHands.isValue());
        });
        this.leftHandTranslateX = new NumberSetting(Lang.ARM_TWEAKS_VIEW_MODEL_LEFT_HAND_TRANSLATE_X).currentValue(0.0f).range(-2.0f, 2.0f).step(0.1f).visible(() -> {
            return Boolean.valueOf(!this.moveBothHands.isValue());
        });
        this.rightHandTranslateY = new NumberSetting(Lang.ARM_TWEAKS_VIEW_MODEL_RIGHT_HAND_TRANSLATE_Y).currentValue(0.0f).range(-2.0f, 2.0f).step(0.1f).visible(() -> {
            return Boolean.valueOf(!this.moveBothHands.isValue());
        });
        this.leftHandTranslateY = new NumberSetting(Lang.ARM_TWEAKS_VIEW_MODEL_LEFT_HAND_TRANSLATE_Y).currentValue(0.0f).range(-2.0f, 2.0f).step(0.1f).visible(() -> {
            return Boolean.valueOf(!this.moveBothHands.isValue());
        });
        this.rightHandTranslateZ = new NumberSetting(Lang.ARM_TWEAKS_VIEW_MODEL_RIGHT_HAND_TRANSLATE_Z).currentValue(0.0f).range(-2.0f, 2.0f).step(0.1f).visible(() -> {
            return Boolean.valueOf(!this.moveBothHands.isValue());
        });
        this.leftHandTranslateZ = new NumberSetting(Lang.ARM_TWEAKS_VIEW_MODEL_LEFT_HAND_TRANSLATE_Z).currentValue(0.0f).range(-2.0f, 2.0f).step(0.1f).visible(() -> {
            return Boolean.valueOf(!this.moveBothHands.isValue());
        });
        this.mc = Mc.INSTANCE;
        this.swingAnimationGroup.setSubSettings(List.of(this.swingAnimationType, this.onlyWhenAura, this.swingPower, this.swingSpeed, this.swingInclination));
        this.viewModelGroup.setSubSettings(List.of(this.moveBothHands, this.translateX, this.translateY, this.translateZ, this.rightHandTranslateX, this.rightHandTranslateY, this.rightHandTranslateZ, this.leftHandTranslateX, this.leftHandTranslateY, this.leftHandTranslateZ));
        addSettings(this.swingAnimationGroup, this.viewModelGroup);
        register(HeldItemRenderEvent.class, class318Var -> {
            if (this.viewModelGroup.isValue() && isState() && this.mc.isWorldLoaded()) {
                MatrixStack matrixStackMatrices = class318Var.matrices();
                float fCurrentValue = this.translateX.currentValue();
                float fCurrentValue2 = this.translateY.currentValue();
                float fCurrentValue3 = this.translateZ.currentValue();
                Hand hand = class318Var.hand();
                if ((class318Var.stack().getItem() instanceof CrossbowItem) || (class318Var.stack().getItem() instanceof BowItem)) {
                    return;
                }
                if (!this.moveBothHands.isValue()) {
                    boolean z = getArmForHand(hand) == Arm.RIGHT;
                    fCurrentValue = z ? this.rightHandTranslateX.currentValue() : this.leftHandTranslateX.currentValue();
                    fCurrentValue2 = z ? this.rightHandTranslateY.currentValue() : this.leftHandTranslateY.currentValue();
                    fCurrentValue3 = z ? this.rightHandTranslateZ.currentValue() : this.leftHandTranslateZ.currentValue();
                } else if (hand != Hand.MAIN_HAND) {
                    fCurrentValue = -fCurrentValue;
                }
                matrixStackMatrices.translate(fCurrentValue, fCurrentValue2, fCurrentValue3);
            }
        });
        register(EquipAnimationEvent2.class, class287Var -> {
            if (shouldModifySwing()) {
                class287Var.cancel();
            }
        });
        register(EquipAnimationEvent.class, class176Var -> {
            if (shouldModifySwing()) {
                class176Var.cancel();
            }
        });
        register(HandSwingEvent.class, class087Var -> {
            if (shouldModifySwing()) {
                int iMax = Math.max(1, 12 - ((int) Math.max(1.0f, Math.min(this.swingSpeed.currentValue(), 10.0f))));
                if (StatusEffectUtil.hasHaste(this.mc.getPlayer())) {
                    iMax -= 1 + StatusEffectUtil.getHasteAmplifier(this.mc.getPlayer());
                }
                if (this.mc.getPlayer().hasStatusEffect(StatusEffects.MINING_FATIGUE)) {
                    iMax += (1 + this.mc.getPlayer().getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier()) * 2;
                }
                class087Var.swingSpeed(Math.max(1, iMax));
                class087Var.cancel();
            }
        });
        register(ArmRenderEvent.class, class360Var -> {
            if (shouldModifySwing()) {
                MatrixStack matrixStack = class360Var.matrixStack();
                if (class360Var.arm() == Arm.RIGHT && this.swingAnimationGroup.isValue()) {
                    float fSwingProgress = class360Var.swingProgress();
                    float fSin = (float) Math.sin(((double) fSwingProgress) * 1.5707963267948966d * 2.0d);
                    float fMethod004 = normalizeRange(this.swingPower.currentValue(), 1.0f, 15.0f) * getSwingMultiplier((SwingAnimationType) this.swingAnimationType.currentValue());
                    switch (((SwingAnimationType) this.swingAnimationType.currentValue()).ordinal()) {
                        case 0:
                            float fSin2 = MathHelper.sin(fSwingProgress * fSwingProgress * 3.1415927f);
                            float fSin3 = MathHelper.sin(MathHelper.sqrt(fSwingProgress) * 3.1415927f);
                            matrixStack.translate(0.56f, -0.52f, -0.72f);
                            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0f + (fSin2 * (-20.0f) * fMethod004)));
                            matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(fSin3 * (-20.0f) * fMethod004));
                            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(fSin3 * (-80.0f) * fMethod004));
                            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-45.0f));
                            class360Var.cancel();
                            break;
                        case 1:
                            matrixStack.translate(0.56f, -0.32f, -0.72f);
                            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.swingInclination.currentValue()));
                            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(45.0f));
                            matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-85.0f));
                            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-90.0f) - ((80.0f * fMethod004) * fSin)));
                            class360Var.cancel();
                            break;
                        case 2:
                            matrixStack.translate(0.56f, -0.32f, -0.72f);
                            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f - ((10.0f * fMethod004) * fSin)));
                            matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-65.0f));
                            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-110.0f) + (10.0f * fMethod004 * fSin)));
                            class360Var.cancel();
                            break;
                        case 3:
                            float f = 0.3f * fSin * fMethod004;
                            matrixStack.translate(0.56f, -0.32f, -0.72f);
                            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f - ((20.0f * fMethod004) * fSin)));
                            matrixStack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-45.0f));
                            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-80.0f) + (80.0f * fMethod004 * fSin)));
                            class360Var.cancel();
                            break;
                        case 4:
                            matrixStack.translate(0.56f, -0.32f, -0.72f);
                            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-15.0f));
                            matrixStack.translate(0.0f, 0.0f, -0.1f);
                            matrixStack.translate(0.0f, 0.4f * fSin * fMethod004, (-0.4f) * fSin * fMethod004);
                            matrixStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(5.0f * fSin * fMethod004));
                            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-10.0f) * fSin * fMethod004));
                            matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-80.0f));
                            class360Var.cancel();
                            break;
                    }
                }
            }
        });
    }

    public boolean shouldModifySwing() {
        if (!isState() || !this.swingAnimationGroup.isValue() || !this.mc.isWorldLoaded()) {
            return false;
        }
        if (!this.onlyWhenAura.isValue()) {
            return true;
        }
        AttackAuraModule class878Var = (AttackAuraModule) Expensive.INSTANCE.moduleRepository().get(AttackAuraModule.class);
        return (class878Var == null || !class878Var.isState() || class878Var.target() == null) ? false : true;
    }

    public static float normalizeRange(float f, float f2, float f3) {
        if (f3 <= f2) {
            return 0.0f;
        }
        return MathHelper.clamp((f - f2) / (f3 - f2), 0.0f, 1.0f);
    }

    public static float getSwingMultiplier(SwingAnimationType class534Var) throws MatchException {
        switch (class534Var.ordinal()) {
            case 0:
                return 1.0f;
            case 1:
                return 0.95f;
            case 2:
                return 3.0f;
            case 3:
                return 0.7f;
            case 4:
                return 1.25f;
            default:
                throw new MatchException((String) null, (Throwable) null);
        }
    }

    public Arm getArmForHand(Hand hand) {
        Arm mainArm = this.mc.getPlayer().getMainArm();
        if (hand == Hand.MAIN_HAND) {
            return mainArm;
        }
        return mainArm == Arm.RIGHT ? Arm.LEFT : Arm.RIGHT;
    }
}
