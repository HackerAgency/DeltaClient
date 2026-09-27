package aethereal.module;
import aethereal.annotation.Aliases;
import aethereal.type.AutoTotemActivationMode;
import aethereal.type.AutoTotemConsideration;
import aethereal.type.AutoTotemThreat;
import aethereal.util.BlockUtil;
import aethereal.ui.setting.BooleanSetting;
import aethereal.render.ColorStack;
import aethereal.type.CombatPauseManager;
import aethereal.util.DrawEngine;
import aethereal.math.ElytraSimulation;
import aethereal.Expensive;
import aethereal.util.ExplosionDamageUtil;
import aethereal.render.Fonts;
import aethereal.util.GrimDelayHandler;
import aethereal.type.InventoryScope;
import aethereal.util.IteratorUtil;
import aethereal.ui.setting.KeybindSetting;
import aethereal.util.KeyboardUtil;
import aethereal.Lang;
import aethereal.type.Mc;
import aethereal.ui.setting.ModeSetting;
import aethereal.ui.ModuleTab;
import aethereal.type.MovementInputState;
import aethereal.ui.setting.MultiSelectSetting;
import aethereal.ui.setting.NumberSetting;
import aethereal.util.PlayerSnapshotManager;
import aethereal.event.PlayerTickEvent;
import aethereal.math.PredictedCollision;
import aethereal.event.Render2DEvent;
import aethereal.render.ScreenResolution;
import aethereal.type.SettingUnit;
import aethereal.type.ShapeType;
import aethereal.math.SimulatedPlayer;
import aethereal.model.SlotSearchResult2;
import aethereal.math.Stopwatch;
import aethereal.util.SwapUtil;
import aethereal.render.ThemePalette;
import aethereal.type.TrajectoryCalculator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.text.TextVisitFactory;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

@Aliases(aliases = {"Auto Totem", "Totem Swap", "Totem", "Automatic Totem", "Auto Offhand Totem", "Totem Switch"})
public class AutoTotemModule extends Module {
    public final ModeSetting<AutoTotemActivationMode> activationMode;
    public final NumberSetting healthThreshold;

    public final BooleanSetting showCounter;

    public final BooleanSetting countEnchanted;
    public final MultiSelectSetting<AutoTotemConsideration> considerations;

    public final MultiSelectSetting<AutoTotemThreat> threats;
    public final BooleanSetting noSwapWhileEating;

    public final KeybindSetting activationKey;
    public final Mc mc;
    public ItemStack savedOffhand;

    public Item savedOffhandItem;
    public int totemCount;
    public int enchantedTotemCount;
    public final Stopwatch swapTimer;

    public AutoTotemModule() {
        super(ModuleTab.COMBAT, "Auto Totem");
        this.activationMode = new ModeSetting(Lang.COMBAT_AUTOTOTEM_ACTIVATION_MODE).values(AutoTotemActivationMode.class);
        this.healthThreshold = new NumberSetting(Lang.COMBAT_AUTOTOTEM_HEALTH, Lang.COMBAT_AUTOTOTEM_HEALTH_DESC).currentValue(3.5f).range(0.0f, 10.0f).step(0.05f).unit(SettingUnit.HITPOINTS).visible(() -> {
            return Boolean.valueOf(this.activationMode.isSelected(AutoTotemActivationMode.AUTO));
        });
        this.showCounter = new BooleanSetting(Lang.COMBAT_AUTOTOTEM_COUNTER);
        BooleanSetting class665Var = new BooleanSetting(Lang.COMBAT_AUTOTOTEM_COUNT_ENCHANTED);
        BooleanSetting class665Var2 = this.showCounter;
        Objects.requireNonNull(class665Var2);
        this.countEnchanted = class665Var.visible(class665Var2::isValue);
        this.considerations = new MultiSelectSetting(Lang.COMBAT_AUTOTOTEM_CONSIDER).values(AutoTotemConsideration.class).visible(() -> {
            return Boolean.valueOf(this.activationMode.isSelected(AutoTotemActivationMode.AUTO));
        });
        this.threats = new MultiSelectSetting(Lang.COMBAT_AUTOTOTEM_OPTIONS).values(AutoTotemThreat.class).visible(() -> {
            return Boolean.valueOf(this.activationMode.isSelected(AutoTotemActivationMode.AUTO));
        });
        this.noSwapWhileEating = new BooleanSetting(Lang.COMBAT_AUTOTOTEM_NO_SWAP_EATING, Lang.COMBAT_AUTOTOTEM_NO_SWAP_EATING_DESC).setValue(false);
        this.activationKey = new KeybindSetting(Lang.COMBAT_AUTOTOTEM_KEY, Lang.COMBAT_AUTOTOTEM_KEY_DESC).visible(() -> {
            return Boolean.valueOf(this.activationMode.isSelected(AutoTotemActivationMode.BUTTON));
        });
        this.mc = Mc.INSTANCE;
        this.savedOffhand = ItemStack.EMPTY;
        this.savedOffhandItem = Items.AIR;
        this.totemCount = 0;
        this.enchantedTotemCount = 0;
        this.swapTimer = new Stopwatch(false);
        addSettings(this.activationMode, this.healthThreshold, this.considerations, this.threats, this.noSwapWhileEating, this.showCounter, this.countEnchanted, this.activationKey);
        register(Render2DEvent.class, class311Var -> {
            if (isState() && Mc.INSTANCE.isWorldLoaded() && this.showCounter.isValue()) {
                if ((this.totemCount > 0 || (this.enchantedTotemCount > 0 && this.countEnchanted.isValue())) && class311Var.isPost()) {
                    DrawEngine class154VarDrawEngine = Expensive.INSTANCE.drawEngine();
                    ColorStack class115VarColorStack = class154VarDrawEngine.colorStack();
                    MatrixStack matrixStack = class311Var.matrixStack();
                    ThemePalette class764VarPalette = Expensive.INSTANCE.theme().palette();
                    float fScreenWidth = (ScreenResolution.resolution().screenWidth() / 2.0f) - (16 / 2.0f);
                    float fScreenHeight = (ScreenResolution.resolution().screenHeight() / 2.0f) - (16 / 2.0f);
                    class154VarDrawEngine.begin();
                    class154VarDrawEngine.itemStack(matrixStack.peek().getPositionMatrix(), Items.TOTEM_OF_UNDYING.getDefaultStack(), fScreenWidth + 35.0f, fScreenHeight, 16 / 32.0f, 1.0f);
                    class154VarDrawEngine.msdfFont(matrixStack.peek().getPositionMatrix(), Fonts.INTER_SEMIBOLD.get(), (this.totemCount + (this.countEnchanted.isValue() ? this.enchantedTotemCount : 0)) + "x", fScreenWidth + 35.0f + 16 + 3.0f, (fScreenHeight + (16 / 2.0f)) - (Fonts.INTER_SEMIBOLD.get().getHeight(12) / 2.0f), 12, 0.0f, class115VarColorStack.computeColor(class764VarPalette.text().tone(400).argb()));
                    class154VarDrawEngine.end();
                }
            }
        });
        register(PlayerTickEvent.class, class130Var -> {
            if (Mc.INSTANCE.isWorldLoaded() && isState() && this.mc.isWorldLoaded() && class130Var.isPre()) {
                ClientPlayerEntity player = this.mc.getPlayer();
                ClientWorld world = this.mc.getWorld();
                List<SlotSearchResult2> listMethod015 = findTotems(player);
                ArrayList<SlotSearchResult2> arrayList = new ArrayList();
                ArrayList<SlotSearchResult2> arrayList2 = new ArrayList();
                for (SlotSearchResult2 class329Var : listMethod015) {
                    if (class329Var.stack().hasEnchantments()) {
                        arrayList2.add(class329Var);
                    } else {
                        arrayList.add(class329Var);
                    }
                }
                this.totemCount = arrayList.stream().mapToInt(class329Var2 -> {
                    return class329Var2.stack().getCount();
                }).sum();
                this.enchantedTotemCount = arrayList2.stream().mapToInt(class329Var3 -> {
                    return class329Var3.stack().getCount();
                }).sum();
                SlotSearchResult2 class329VarMethod006 = selectTotem(arrayList, arrayList2);
                if (this.noSwapWhileEating.isValue() && player.isUsingItem() && player.getActiveItem().getComponents().contains(DataComponentTypes.FOOD)) {
                    return;
                }
                if (!shouldSwapTotem(world, player) || class329VarMethod006 == null || !this.swapTimer.hasElapsed(300L)) {
                    if (shouldRestoreOffhand(player) && GrimDelayHandler.script.isFinished() && this.swapTimer.hasElapsed(300L)) {
                        findSavedOffhandSlot().ifPresentOrElse(class329Var4 -> {
                            CombatPauseManager.INSTANCE.pauseAutoSwapForAtLeast(3);
                            SwapUtil.swapToOffhand(class329Var4.slotReference().increasedSlot());
                            this.savedOffhand = ItemStack.EMPTY;
                            this.savedOffhandItem = Items.AIR;
                            this.swapTimer.reset();
                        }, () -> {
                            this.savedOffhand = ItemStack.EMPTY;
                            this.savedOffhandItem = Items.AIR;
                        });
                        return;
                    }
                    return;
                }
                CombatPauseManager.INSTANCE.pauseAutoSwapForAtLeast(3);
                if (hasTotemInHand(player, arrayList.isEmpty()) || !GrimDelayHandler.script.isFinished()) {
                    return;
                }
                int iIncreasedSlot = class329VarMethod006.slotReference().increasedSlot();
                saveOffhand(player.getOffHandStack());
                SwapUtil.swapToOffhand(iIncreasedSlot);
                this.swapTimer.reset();
            }
        });
    }

    public List<SlotSearchResult2> findTotems(ClientPlayerEntity clientPlayerEntity) {
        return Expensive.INSTANCE.inventoryService().searcher().findAllItems(itemStack -> {
            return itemStack.getItem() == Items.TOTEM_OF_UNDYING && !clientPlayerEntity.getItemCooldownManager().isCoolingDown(itemStack);
        }, InventoryScope.ALL);
    }

    public Optional<SlotSearchResult2> findSavedOffhandSlot() {
        return Expensive.INSTANCE.inventoryService().searcher().findItem(itemStack -> {
            return !itemStack.isEmpty() && itemStack.getItem() == this.savedOffhandItem && ItemStack.areItemsAndComponentsEqual(itemStack, this.savedOffhand);
        }, InventoryScope.HOTBAR, InventoryScope.INVENTORY);
    }

    @Nullable
    public static SlotSearchResult2 selectTotem(List<SlotSearchResult2> list, List<SlotSearchResult2> list2) {
        if (!list.isEmpty()) {
            return (SlotSearchResult2) list.getFirst();
        }
        if (list2.isEmpty()) {
            return null;
        }
        return (SlotSearchResult2) list2.getFirst();
    }

    public boolean hasTotemInHand(ClientPlayerEntity clientPlayerEntity, boolean z) {
        return Arrays.stream(Hand.values()).anyMatch(hand -> {
            ItemStack stackInHand = clientPlayerEntity.getStackInHand(hand);
            return stackInHand.getItem() == Items.TOTEM_OF_UNDYING && (z || !stackInHand.hasEnchantments());
        });
    }

    public boolean shouldSwapTotem(ClientWorld clientWorld, ClientPlayerEntity clientPlayerEntity) {
        if (this.activationMode.isSelected(AutoTotemActivationMode.BUTTON)) {
            return KeyboardUtil.isKeyPressed(this.activationKey.getKey());
        }
        float health = clientPlayerEntity.getHealth() + clientPlayerEntity.getAbsorptionAmount();
        float fCurrentValue = health - this.healthThreshold.currentValue();
        if (clientPlayerEntity.isCreative() || clientPlayerEntity.isSpectator() || clientPlayerEntity.isDead() || !clientPlayerEntity.isAlive()) {
            return false;
        }
        if ((hasBrokenArmor(clientPlayerEntity) && isPlayerNearby(clientWorld, clientPlayerEntity, 10.0d)) || isElytraThreat(clientPlayerEntity, health) || isMaceThreat(clientWorld, clientPlayerEntity)) {
            return true;
        }
        if ((this.threats.isSelected(AutoTotemThreat.TRIDENT) && IteratorUtil.toList(this.mc.getWorld().getEntities().iterator()).stream().anyMatch(entity -> {
            return (entity instanceof TridentEntity) && isTridentThreat((TridentEntity) entity);
        })) || fCurrentValue <= 0.0f) {
            return true;
        }
        float fMax = Math.max(computeExplosionDamage(clientPlayerEntity, clientWorld, fCurrentValue), computeProjectileDamage(clientPlayerEntity, clientWorld, fCurrentValue));
        if (fMax >= fCurrentValue) {
            return true;
        }
        return fMax + predictFallDamage(clientPlayerEntity) >= fCurrentValue && !isHoldingSphere(clientPlayerEntity);
    }

    public boolean isHoldingSphere(ClientPlayerEntity clientPlayerEntity) {
        Text text;
        if ((this.threats.isSelected(AutoTotemThreat.FALL) && clientPlayerEntity.fallDistance > 5.0f) || !this.considerations.isSelected(AutoTotemConsideration.SPHERES)) {
            return false;
        }
        ItemStack offHandStack = clientPlayerEntity.getOffHandStack();
        if (offHandStack.isEmpty()) {
            return false;
        }
        Item item = offHandStack.getItem();
        if (!offHandStack.contains(DataComponentTypes.CUSTOM_NAME) || (text = (Text) offHandStack.get(DataComponentTypes.CUSTOM_NAME)) == null) {
            return false;
        }
        String lowerCase = TextVisitFactory.removeFormattingCodes(text).toLowerCase();
        return item == Items.PLAYER_HEAD && (lowerCase.contains("шар") || lowerCase.contains("сфера") || lowerCase.contains("голова"));
    }

    public boolean isMaceThreat(ClientWorld clientWorld, ClientPlayerEntity clientPlayerEntity) {
        if (this.threats.isSelected(AutoTotemThreat.MACE)) {
            return clientWorld.getPlayers().stream().anyMatch(abstractClientPlayerEntity -> {
                if (abstractClientPlayerEntity.equals(clientPlayerEntity)) {
                    return false;
                }
                if (!(abstractClientPlayerEntity.getMainHandStack().getItem() == Items.MACE || abstractClientPlayerEntity.getOffHandStack().getItem() == Items.MACE)) {
                    return false;
                }
                if (abstractClientPlayerEntity.getVelocity().y < 0.0d && abstractClientPlayerEntity.fallDistance > 3.0f) {
                    return ((abstractClientPlayerEntity.getY() > clientPlayerEntity.getY() ? 1 : (abstractClientPlayerEntity.getY() == clientPlayerEntity.getY() ? 0 : -1)) > 0) && ((abstractClientPlayerEntity.distanceTo(clientPlayerEntity) > 7.0f ? 1 : (abstractClientPlayerEntity.distanceTo(clientPlayerEntity) == 7.0f ? 0 : -1)) <= 0);
                }
                return false;
            });
        }
        return false;
    }

    public boolean isElytraThreat(ClientPlayerEntity clientPlayerEntity, float f) {
        return this.considerations.isSelected(AutoTotemConsideration.ELYTRA) && ((ItemStack) clientPlayerEntity.getInventory().armor.get(2)).getItem() == Items.ELYTRA && f <= 14.0f;
    }

    public boolean hasBrokenArmor(ClientPlayerEntity clientPlayerEntity) {
        if (this.threats.isSelected(AutoTotemThreat.BROKEN_ARMOR)) {
            return clientPlayerEntity.getInventory().armor.stream().anyMatch((v0) -> {
                return v0.isEmpty();
            });
        }
        return false;
    }

    public boolean isPlayerNearby(ClientWorld clientWorld, ClientPlayerEntity clientPlayerEntity, double d) {
        return clientWorld.getPlayers().stream().anyMatch(abstractClientPlayerEntity -> {
            return !abstractClientPlayerEntity.equals(clientPlayerEntity) && ((double) abstractClientPlayerEntity.distanceTo(clientPlayerEntity)) <= d;
        });
    }

    public float computeExplosionDamage(ClientPlayerEntity clientPlayerEntity, ClientWorld clientWorld, float f) {
        if (!this.threats.isSelected(AutoTotemThreat.EXPLOSION)) {
            return 0.0f;
        }
        float fMax = 0.0f;
        Iterator it = clientWorld.getEntities().iterator();
        while (it.hasNext()) {
            fMax = Math.max(fMax, ExplosionDamageUtil.getExplosionDamageFromEntity(clientPlayerEntity, (Entity) it.next()));
            if (fMax >= f) {
                return fMax;
            }
        }
        return fMax;
    }

    public float predictFallDamage(ClientPlayerEntity clientPlayerEntity) {
        PredictedCollision class353VarFindCollision;
        BlockPos blockPos;
        if (!this.threats.isSelected(AutoTotemThreat.FALL) || clientPlayerEntity.isOnGround() || clientPlayerEntity.getVelocity().y >= 0.0d || (class353VarFindCollision = ElytraSimulation.fromPlayer(clientPlayerEntity).findCollision(20)) == null || (blockPos = class353VarFindCollision.pos) == null || BlockUtil.isFallDamageBlocking(clientPlayerEntity.getWorld(), blockPos)) {
            return 0.0f;
        }
        float effectiveDamage = ExplosionDamageUtil.getEffectiveDamage(clientPlayerEntity, clientPlayerEntity.getDamageSources().fall(), clientPlayerEntity.computeFallDamage(clientPlayerEntity.fallDistance, 1.0f), false);
        Registry orThrow = clientPlayerEntity.getWorld().getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        RegistryEntry entry = orThrow.getEntry((Enchantment) orThrow.get(Enchantments.FEATHER_FALLING));
        if (entry == null || entry.value() == null) {
            return 0.0f;
        }
        int equipmentLevel = EnchantmentHelper.getEquipmentLevel(entry, clientPlayerEntity);
        if (equipmentLevel > 0) {
            effectiveDamage *= 1.0f - (equipmentLevel * 0.15f);
        }
        StatusEffectInstance statusEffect = clientPlayerEntity.getStatusEffect(StatusEffects.RESISTANCE);
        if (statusEffect != null && statusEffect.getDuration() > 0) {
            effectiveDamage *= 1.0f - (0.2f * (statusEffect.getAmplifier() + 1));
        }
        return effectiveDamage;
    }

    public boolean shouldRestoreOffhand(ClientPlayerEntity clientPlayerEntity) {
        return (this.savedOffhandItem == Items.AIR || this.savedOffhand.isEmpty() || ItemStack.areItemsAndComponentsEqual(clientPlayerEntity.getOffHandStack(), this.savedOffhand)) ? false : true;
    }

    public void saveOffhand(ItemStack itemStack) {
        if (itemStack.isEmpty() || itemStack.getItem() == Items.AIR) {
            return;
        }
        if (this.savedOffhandItem == Items.AIR || this.savedOffhand.isEmpty()) {
            this.savedOffhand = itemStack.copy();
            this.savedOffhandItem = this.savedOffhand.getItem();
        }
    }

    public float computeProjectileDamage(ClientPlayerEntity clientPlayerEntity, ClientWorld clientWorld, float f) {
        if (!this.threats.isSelected(AutoTotemThreat.PROJECTILES)) {
            return 0.0f;
        }
        float fMax = 0.0f;
        for (Entity entity : clientWorld.getEntities()) {
            if (entity instanceof ProjectileEntity) {
                ProjectileEntity projectileEntity = (ProjectileEntity) entity;
                if (projectileEntity.getOwner() == clientPlayerEntity) {
                    continue;
                } else {
                    Vec3d pos = projectileEntity.getPos();
                    Vec3d vec3dSubtract = clientPlayerEntity.getBoundingBox().getCenter().subtract(pos);
                    Vec3d velocity = projectileEntity.getVelocity();
                    if (velocity.dotProduct(vec3dSubtract) <= 0.0d) {
                        continue;
                    } else {
                        Vec3d vec3dAdd = pos.add(velocity.normalize().multiply(vec3dSubtract.length()));
                        if (!clientPlayerEntity.getBoundingBox().expand(0.25d).raycast(pos, vec3dAdd).isEmpty() && clientWorld.raycast(new RaycastContext(pos, vec3dAdd, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, projectileEntity)).getType() == HitResult.Type.MISS) {
                            fMax = Math.max(fMax, getProjectileDamage(projectileEntity, clientPlayerEntity));
                            if (fMax >= f) {
                                return fMax;
                            }
                        }
                    }
                }
            }
        }
        return fMax;
    }

    public float getProjectileDamage(ProjectileEntity projectileEntity, ClientPlayerEntity clientPlayerEntity) {
        DamageSource damageSourceArrow;
        if (!(projectileEntity instanceof PersistentProjectileEntity)) {
            return 0.0f;
        }
        PersistentProjectileEntity persistentProjectileEntity = (PersistentProjectileEntity) projectileEntity;
        float damage = (float) persistentProjectileEntity.getDamage();
        if (projectileEntity instanceof TridentEntity) {
            TridentEntity tridentEntity = (TridentEntity) projectileEntity;
            damageSourceArrow = clientPlayerEntity.getDamageSources().trident(tridentEntity, tridentEntity.getOwner());
        } else {
            damageSourceArrow = clientPlayerEntity.getDamageSources().arrow(persistentProjectileEntity, persistentProjectileEntity.getOwner());
        }
        return ExplosionDamageUtil.getEffectiveDamage(clientPlayerEntity, damageSourceArrow, damage, false);
    }

    public boolean isTridentThreat(TridentEntity tridentEntity) {
        ArrayList<net.minecraft.util.math.Box> arrayList = new ArrayList<net.minecraft.util.math.Box>();
        PlayerSnapshotManager.INSTANCE.getSnapshots(this.mc.getPlayer(), 6).forEach(class373Var -> {
            arrayList.add(class373Var.box);
        });
        SimulatedPlayer class136VarFromClientPlayer = SimulatedPlayer.fromClientPlayer(new MovementInputState(this.mc.getPlayer().input.playerInput));
        for (int i = 0; i < 7; i++) {
            class136VarFromClientPlayer.tick();
            arrayList.add(class136VarFromClientPlayer.boundingBox);
        }
        Vec3d pos = tridentEntity.getPos();
        return IntStream.range(1, 10).anyMatch(i2 -> {
            return arrayList.stream().anyMatch(box -> {
                return box.intersects(tridentEntity.getBoundingBox().offset(TrajectoryCalculator.INSTANCE.traceTrajectory(pos, tridentEntity.getVelocity(), tridentEntity, i2).getPos().subtract(pos)).expand(0.4d));
            });
        });
    }

    @Override
    public void deactivate() {
        this.savedOffhand = ItemStack.EMPTY;
        this.savedOffhandItem = Items.AIR;
        super.deactivate();
    }
}
