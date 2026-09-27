package aethereal.module;
import aethereal.type.AutoPotionType;
import aethereal.ui.setting.BooleanSetting;
import aethereal.Expensive;
import aethereal.util.GrimDelayHandler;
import aethereal.type.InventoryScope;
import aethereal.Lang;
import aethereal.type.Mc;
import aethereal.ui.ModuleTab;
import aethereal.ui.setting.MultiSelectSetting;
import aethereal.util.PlayerActionUtil;
import aethereal.event.PlayerTickEvent;
import aethereal.util.PvPModeDetector;
import aethereal.math.Rotation;
import aethereal.model.ScopedSlot;
import aethereal.math.SimulatedPlayer;
import aethereal.model.SlotSearchResult2;
import aethereal.util.SwapUtil;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SplashPotionItem;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

public class AutoPotionModule extends Module {
    public final Mc mc;
    public final MultiSelectSetting<AutoPotionType> potions;
    public final BooleanSetting onlyPvp;
    public final Map<AutoPotionType, Long> lastThrowTimes;
    public final Map<AutoPotionType, SlotSearchResult2> foundPotions;

    public AutoPotionModule() {
        super(ModuleTab.PLAYER, "Auto Potion");
        this.mc = Mc.INSTANCE;
        this.potions = new MultiSelectSetting(Lang.AUTOPOTION_POTIONS, Lang.AUTOPOTION_POTIONS_DESC).values(AutoPotionType.class);
        this.onlyPvp = new BooleanSetting(Lang.AUTOPOTION_ONLY_PVP, Lang.AUTOPOTION_ONLY_PVP_DESC);
        this.lastThrowTimes = new HashMap();
        this.foundPotions = new EnumMap(AutoPotionType.class);
        addSettings(this.potions, this.onlyPvp);
        register(PlayerTickEvent.class, this::onPlayerTick);
    }

    public void onPlayerTick(PlayerTickEvent class130Var) {
        if (isState() && this.mc.isWorldLoaded()) {
            if (!this.onlyPvp.isValue() || PvPModeDetector.isPvPMode()) {
                ClientPlayerEntity player = this.mc.getPlayer();
                for (AutoPotionType class574Var : AutoPotionType.values()) {
                    if (!isPotionEnabled(class574Var)) {
                        this.foundPotions.remove(class574Var);
                    } else if (player.hasStatusEffect(class574Var.getEffect())) {
                        this.foundPotions.remove(class574Var);
                    } else if (!this.lastThrowTimes.containsKey(class574Var) || System.currentTimeMillis() - this.lastThrowTimes.get(class574Var).longValue() >= 600) {
                        findSplashPotionByType(class574Var, player).ifPresentOrElse(class329Var -> {
                            this.foundPotions.put(class574Var, class329Var);
                        }, () -> {
                            this.foundPotions.remove(class574Var);
                        });
                    }
                }
                if (this.foundPotions.isEmpty()) {
                    return;
                }
                Vec3d vec3dMethod008 = findThrowTarget(player, this.mc.getWorld());
                Rotation class007VarLookingAt = vec3dMethod008 != null ? Rotation.lookingAt(vec3dMethod008, player.getEyePos()) : new Rotation(Rotation.playerRotation().getYaw(), 90.0f);
                if (canThrowPotion() && !player.isUsingItem() && GrimDelayHandler.script.isFinished()) {
                    SwapUtil.swapAction(class007VarLookingAt, true, () -> {
                        Iterator<Map.Entry<AutoPotionType, SlotSearchResult2>> it = this.foundPotions.entrySet().iterator();
                        while (it.hasNext()) {
                            Map.Entry<AutoPotionType, SlotSearchResult2> next = it.next();
                            AutoPotionType key = next.getKey();
                            SlotSearchResult2 value = next.getValue();
                            this.lastThrowTimes.put(key, Long.valueOf(System.currentTimeMillis()));
                            ClientPlayerEntity player2 = this.mc.getPlayer();
                            if (player2.hasStatusEffect(key.getEffect())) {
                                it.remove();
                            } else {
                                ScopedSlot class246VarSlotReference = value.slotReference();
                                PlayerActionUtil.INSTANCE.clickSlot(player2.currentScreenHandler.syncId, class246VarSlotReference.increasedSlot(), player2.getInventory().selectedSlot, SlotActionType.SWAP, true);
                                PlayerActionUtil.INSTANCE.interactItem(Hand.MAIN_HAND, class007VarLookingAt, false);
                                PlayerActionUtil.INSTANCE.clickSlot(player2.currentScreenHandler.syncId, class246VarSlotReference.increasedSlot(), player2.getInventory().selectedSlot, SlotActionType.SWAP, true);
                                it.remove();
                            }
                        }
                        PlayerActionUtil.INSTANCE.updateSlots(true);
                    });
                }
            }
        }
    }

    public boolean isPotionEnabled(AutoPotionType class574Var) {
        return this.potions.isSelected(class574Var);
    }

    public boolean canThrowPotion() {
        return (this.mc.getPlayer().isOnGround() || SimulatedPlayer.simulateLocalPlayer(1).onGround || this.mc.getPlayer().isOnGround()) && this.mc.getPlayer().age > 100;
    }

    public Optional<SlotSearchResult2> findSplashPotionByType(AutoPotionType class574Var, ClientPlayerEntity clientPlayerEntity) {
        return Expensive.INSTANCE.inventoryService().searcher().findItem(itemStack -> {
            return isSplashPotionOfType(class574Var, itemStack);
        }, InventoryScope.HOTBAR, InventoryScope.INVENTORY);
    }

    public boolean isSplashPotionOfType(AutoPotionType class574Var, ItemStack itemStack) {
        PotionContentsComponent potionContentsComponent;
        if (!(itemStack.getItem() instanceof SplashPotionItem) || (potionContentsComponent = (PotionContentsComponent) itemStack.get(DataComponentTypes.POTION_CONTENTS)) == null || potionContentsComponent.getEffects() == null) {
            return false;
        }
        return StreamSupport.stream(potionContentsComponent.getEffects().spliterator(), false).anyMatch(statusEffectInstance -> {
            return statusEffectInstance.getEffectType().value() == class574Var.getEffect().value();
        });
    }

    public Vec3d findThrowTarget(ClientPlayerEntity clientPlayerEntity, ClientWorld clientWorld) {
        Iterable<net.minecraft.util.shape.VoxelShape> collisions = clientWorld.getCollisions(clientPlayerEntity, clientPlayerEntity.getBoundingBox().expand(0.0d, 0.5d, 0.0d));
        if (this.foundPotions.isEmpty()) {
            return null;
        }
        return (Vec3d) StreamSupport.stream(collisions.spliterator(), false).min(Comparator.comparingDouble(voxelShape -> {
            return voxelShape.getBoundingBox().getCenter().squaredDistanceTo(clientPlayerEntity.getPos());
        })).map(voxelShape2 -> {
            return voxelShape2.getBoundingBox().getCenter();
        }).orElse(null);
    }
}
