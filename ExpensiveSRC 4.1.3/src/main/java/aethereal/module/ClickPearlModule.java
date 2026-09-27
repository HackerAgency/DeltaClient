package aethereal.module;
import aethereal.util.ActionScheduler;
import aethereal.annotation.Aliases;
import aethereal.Expensive;
import aethereal.util.GrimDelayHandler;
import aethereal.type.InventoryScope;
import aethereal.net.InventoryService;
import aethereal.util.InventoryTask;
import aethereal.ui.setting.KeybindSetting;
import aethereal.Lang;
import aethereal.type.Mc;
import aethereal.ui.ModuleTab;
import aethereal.type.NotificationType;
import aethereal.util.PlayerActionUtil;
import aethereal.util.SwapUtil;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Aliases(aliases = {"Click Pearl", "Middle Click Pearl", "MC Pearl", "HotKey Pearl"})
public class ClickPearlModule extends Module {
    public final KeybindSetting keybind;
    public final Mc mc;
    public final ActionScheduler actionScheduler;

    public ClickPearlModule() {
        super(ModuleTab.PLAYER, "Click Pearl");
        this.keybind = new KeybindSetting(Lang.CLICKPEARL_KEY, Lang.CLICKPEARL_KEY_DESC);
        this.mc = Mc.INSTANCE;
        this.actionScheduler = new ActionScheduler();
        addSettings(this.keybind);
        this.keybind.consumer(class664Var -> {
            if (isState() && this.mc.isWorldLoaded()) {
                if (this.mc.getPlayer().getItemCooldownManager().isCoolingDown(Items.ENDER_PEARL.getDefaultStack())) {
                    notifyCooldown(Items.ENDER_PEARL);
                } else if (GrimDelayHandler.script.isFinished()) {
                    throwPearl();
                }
            }
        });
    }

    public void notifyCooldown(Item item) {
        Expensive.INSTANCE.notificationRepository().post((Text) Text.literal(Lang.ITEM_ON_COOLDOWN.effective().replace("{item}", String.valueOf(Formatting.RED) + item.getName().getString() + String.valueOf(Formatting.RESET)).replace("{seconds}", String.valueOf(Formatting.RED) + String.format(Locale.US, "%.1f", Float.valueOf(PlayerActionUtil.INSTANCE.getRemainingCooldownSeconds(item))) + String.valueOf(Formatting.RESET))), item.getDefaultStack(), 2L, TimeUnit.SECONDS);
    }

    public void throwPearl() {
        InventoryService class011VarInventoryService = Expensive.INSTANCE.inventoryService();
        Predicate<ItemStack> predicate = itemStack -> {
            return itemStack.getItem() == Items.ENDER_PEARL;
        };
        class011VarInventoryService.searcher().findItem(predicate, InventoryScope.ALL).ifPresentOrElse(class329Var -> {
            class011VarInventoryService.addTask(InventoryTask.create(predicate, class329Var, SwapUtil.needsStop(), false, true), this);
        }, () -> {
            Expensive.INSTANCE.notificationRepository().post(NotificationType.ERROR, (Text) Text.literal(Lang.NO_ITEM_FOUND.effective().replace("{item}", String.valueOf(Formatting.RED) + Items.ENDER_PEARL.getName().getString() + String.valueOf(Formatting.RESET))), 3L, TimeUnit.SECONDS);
        });
    }
}
