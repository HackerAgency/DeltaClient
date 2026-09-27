package aethereal.module;
import aethereal.annotation.Aliases;
import aethereal.ui.setting.BooleanSetting;
import aethereal.type.EventPriority;
import aethereal.Lang;
import aethereal.type.Mc;
import aethereal.ui.ModuleTab;
import aethereal.event.MovementUpdateEvent;
import aethereal.event.MovementUpdateSource;
import aethereal.event.OverlayEffectEvent;
import aethereal.event.SprintStopEvent;

import net.minecraft.entity.effect.StatusEffects;

@Aliases(aliases = {"Auto Sprint", "Sprint", "Auto Run", "Always Sprint", "Automatic Sprint", "Toggle Sprint"})
public class SprintModule extends Module {
    Mc mc;
    BooleanSetting keepSprintSetting;
    BooleanSetting ignoreHungerSetting;
    BooleanSetting ignoreBlindnessSetting;

    public SprintModule() {
        super(ModuleTab.MOVEMENT, "Sprint");
        this.mc = Mc.INSTANCE;
        this.keepSprintSetting = new BooleanSetting(Lang.SPRINT_KEEP_SPRINT, Lang.SPRINT_KEEP_SPRINT_DESC);
        this.ignoreHungerSetting = new BooleanSetting(Lang.SPRINT_IGNORE_HUNGER);
        this.ignoreBlindnessSetting = new BooleanSetting(Lang.SPRINT_IGNORE_BLINDNESS);
        addSettings(this.keepSprintSetting, this.ignoreHungerSetting, this.ignoreBlindnessSetting);
        register(OverlayEffectEvent.class, class069Var -> {
            if (isState() && this.mc.isWorldLoaded()) {
                if ((this.ignoreHungerSetting.isValue() && class069Var.getType().isHunger()) || (this.ignoreBlindnessSetting.isValue() && class069Var.getType().isBlidness())) {
                    class069Var.cancel();
                }
            }
        });
        register(MovementUpdateEvent.class, class308Var -> {
            if (isState() && this.mc.isWorldLoaded() && class308Var.getDirectionalInput().isMoving()) {
                if (this.ignoreHungerSetting.isValue() || hasEnoughFood()) {
                    if (this.ignoreBlindnessSetting.isValue() || !hasBlindness()) {
                        boolean z = class308Var.getSource() == MovementUpdateSource.MOVEMENT_TICK;
                        if ((class308Var.getSource() == MovementUpdateSource.INPUT) || z) {
                            class308Var.setSprint(true);
                        }
                    }
                }
            }
        }, EventPriority.LOW);
        register(SprintStopEvent.class, class337Var -> {
            if (isState() && this.mc.isWorldLoaded() && this.keepSprintSetting.isValue()) {
                class337Var.cancel();
            }
        });
    }

    public boolean hasEnoughFood() {
        return this.mc.getPlayer().getHungerManager().getFoodLevel() > 6;
    }

    public boolean hasBlindness() {
        return this.mc.getPlayer().hasStatusEffect(StatusEffects.BLINDNESS);
    }
}
