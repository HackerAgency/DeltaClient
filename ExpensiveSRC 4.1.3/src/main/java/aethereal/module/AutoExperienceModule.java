package aethereal.module;
import aethereal.annotation.Aliases;
import aethereal.type.ButtonAction;
import aethereal.Expensive;
import aethereal.type.InventoryScope;
import aethereal.net.InventoryService;
import aethereal.event.KeyInputEvent;
import aethereal.type.KeyPressState;
import aethereal.ui.setting.KeybindSetting;
import aethereal.Lang;
import aethereal.type.Mc;
import aethereal.ui.ModuleTab;
import aethereal.event.MouseButtonEvent;
import aethereal.type.NotificationType;
import aethereal.util.PlayerActionUtil;
import aethereal.event.PlayerTickEvent;
import aethereal.math.Rotation;
import aethereal.config.RotationConfig;
import aethereal.util.RotationManager;
import aethereal.model.SlotSearchResult2;
import aethereal.model.Translation;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

@Aliases(aliases = {"Auto Experience", "Auto Repair"})
public class AutoExperienceModule extends Module {
    public final KeybindSetting keybindSetting;
    public boolean throwing;
    public int savedSourceSlot;
    public int savedSelectedSlot;

    public AutoExperienceModule() {
        super(ModuleTab.MISC, "Auto Experience");
        this.keybindSetting = new KeybindSetting(Translation.clearText("Button"));
        this.throwing = false;
        this.savedSourceSlot = -1;
        this.savedSelectedSlot = -1;
        addSettings(this.keybindSetting);
        register(KeyInputEvent.class, class049Var -> {
            handleKey(class049Var.key(), class049Var.action() == KeyPressState.PRESS);
        });
        register(MouseButtonEvent.class, class107Var -> {
            handleKey(class107Var.button(), class107Var.action() == ButtonAction.PRESS);
        });
        register(PlayerTickEvent.class, class130Var -> {
            Mc class815Var = Mc.INSTANCE;
            if (isState() && class815Var.isWorldLoaded() && class130Var.isPre()) {
                if (class815Var.getCurrentScreen() != null) {
                    this.throwing = false;
                } else if (!class815Var.getPlayer().getItemCooldownManager().isCoolingDown(Items.EXPERIENCE_BOTTLE.getDefaultStack()) && this.throwing) {
                    throwExperience();
                }
            }
        });
    }

    public void handleKey(int i, boolean z) {
        if (i == this.keybindSetting.getKey()) {
            if (!z && this.savedSourceSlot != -1 && this.savedSelectedSlot != -1) {
                PlayerActionUtil.INSTANCE.windowClick(SlotActionType.SWAP, this.savedSourceSlot, this.savedSelectedSlot, true);
                this.savedSourceSlot = -1;
                this.savedSelectedSlot = -1;
            }
            this.throwing = z;
        }
    }

    public void throwExperience() {
        ClientPlayerEntity player = Mc.INSTANCE.getPlayer();
        InventoryService class011VarInventoryService = Expensive.INSTANCE.inventoryService();
        Predicate<ItemStack> predicate = itemStack -> {
            return itemStack.getItem() == Items.EXPERIENCE_BOTTLE;
        };
        class011VarInventoryService.searcher().findItem(predicate, InventoryScope.ALL).ifPresentOrElse(class329Var -> {
            throwFromSlot(class329Var, player, predicate, class011VarInventoryService);
        }, this::applyState);
    }

    public void throwFromSlot(SlotSearchResult2 class329Var, ClientPlayerEntity clientPlayerEntity, Predicate<ItemStack> predicate, InventoryService class011Var) {
        RotationManager.INSTANCE.scheduleRotation(new Rotation(clientPlayerEntity.getYaw(), 90.0f), RotationConfig.LINEAR, 1, this, 5);
        if (RotationManager.INSTANCE.getServerRotation().getPitch() > 89.0f) {
            if (class329Var.slotReference().scope() == InventoryScope.HOTBAR && !predicate.test(clientPlayerEntity.getStackInHand(Hand.MAIN_HAND))) {
                class011Var.hotbarSlotSwapper().swapTo(this, class329Var.slotReference(), 1);
            } else if (class329Var.slotReference().scope() == InventoryScope.INVENTORY) {
                PlayerActionUtil.INSTANCE.swapHand(class329Var.slotReference().slot(), Hand.MAIN_HAND);
                if (this.savedSourceSlot == -1) {
                    this.savedSourceSlot = class329Var.slotReference().slot();
                }
                if (this.savedSelectedSlot == -1) {
                    this.savedSelectedSlot = Mc.INSTANCE.getPlayer().getInventory().selectedSlot;
                }
            }
            for (Hand hand : Hand.values()) {
                if (predicate.test(clientPlayerEntity.getStackInHand(hand))) {
                    class011Var.itemInteractor().interactItem(clientPlayerEntity, hand, RotationManager.INSTANCE.getCurrentRotation());
                }
            }
        }
    }

    public void applyState() {
        Expensive.INSTANCE.notificationRepository().post(NotificationType.ERROR, (Text) Text.literal(Lang.NO_ITEM_FOUND.effective().replace("{item}", String.valueOf(Formatting.RED) + Items.EXPERIENCE_BOTTLE.getName().getString() + String.valueOf(Formatting.RESET))), 3L, TimeUnit.SECONDS);
        this.throwing = false;
    }
}
