package aethereal.module.combat;

import aethereal.core.Category;
import aethereal.core.Module;
import aethereal.core.ModuleRegister;
import aethereal.setting.BooleanSetting;
import aethereal.setting.SliderSetting;

@ModuleRegister(name = "Swap Settings", description = "Настройки автоматического свапа предметов", category = Category.Combat)
public class SwapSettings extends Module {
    private final SliderSetting beforeUseDelay = new SliderSetting("Задержка до использования (мс)", 150.0f, 0.0f, 150.0f, 10.0f);
    private final SliderSetting afterUseDelay = new SliderSetting("Задержка после использования (мс)", 150.0f, 0.0f, 150.0f, 10.0f);
    private final BooleanSetting disableOnMove = new BooleanSetting("Отключать при ходьбе/беге", true);
    private final BooleanSetting disableOnJump = new BooleanSetting("Отключать при прыжках", true);
    private final BooleanSetting disableOnAura = new BooleanSetting("Отключать при работе ауры", true);

    public SwapSettings() {
        a(this.beforeUseDelay, this.afterUseDelay, this.disableOnMove, this.disableOnJump, this.disableOnAura);
    }

    public int getBeforeUseDelayMs() {
        return this.beforeUseDelay.c().intValue();
    }

    public int getAfterUseDelayMs() {
        return this.afterUseDelay.c().intValue();
    }

    public boolean shouldDisableOnMove() {
        return this.disableOnMove.c().booleanValue();
    }

    public boolean shouldDisableOnJump() {
        return this.disableOnJump.c().booleanValue();
    }

    public boolean shouldDisableOnAura() {
        return this.disableOnAura.c().booleanValue();
    }
}
