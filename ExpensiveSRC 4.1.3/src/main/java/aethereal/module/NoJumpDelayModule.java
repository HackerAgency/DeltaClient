package aethereal.module;
import aethereal.annotation.Aliases;
import aethereal.type.Mc;
import aethereal.ui.ModuleTab;
import aethereal.event.PlayerTickEvent;

@Aliases(aliases = {"No Delay", "No Jump Delay", "Anti Jump Delay", "Anti Delay", "Fast Jump", "Quick Jump"})
public class NoJumpDelayModule extends Module {
    public NoJumpDelayModule() {
        super(ModuleTab.MOVEMENT, "No Jump Delay");
        register(PlayerTickEvent.class, class130Var -> {
            Mc class815Var = Mc.INSTANCE;
            if (isState() && class815Var.isWorldLoaded() && !class815Var.getPlayer().getAbilities().flying && class130Var.isPre()) {
                class815Var.getPlayer().jumpingCooldown = 0;
            }
        });
    }
}
