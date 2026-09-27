package aethereal.module;
import aethereal.Expensive;
import aethereal.util.FriendManager;
import aethereal.util.GrimDelayHandler;
import aethereal.type.InventoryScope;
import aethereal.net.InventoryService;
import aethereal.util.InventoryTask;
import aethereal.ui.setting.KeybindSetting;
import aethereal.util.KeyboardUtil;
import aethereal.Lang;
import aethereal.type.Mc;
import aethereal.ui.setting.ModeSetting;
import aethereal.ui.ModuleTab;
import aethereal.ui.setting.NumberSetting;
import aethereal.math.PearlTrajectory;
import aethereal.util.PlayerSnapshotManager;
import aethereal.math.Rotation;
import aethereal.math.RotationMath;
import aethereal.event.RotationUpdateEvent;
import aethereal.type.SettingUnit;
import aethereal.math.SimulatedPlayer;
import aethereal.model.SlotSearchResult2;
import aethereal.math.Stopwatch;
import aethereal.type.TargetPearlMode;
import aethereal.type.TotemActivationMode;
import aethereal.model.TrajectoryPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

public class TargetPearlModule extends Module {
    public final ModeSetting<TotemActivationMode> activationMode;
    public final KeybindSetting keybind;
    public final ModeSetting<TargetPearlMode> targetMode;
    public final NumberSetting triggerDistance;
    public List<UUID> handledPearls;
    public final Stopwatch stopwatch;
    public final Mc mc;

    public TargetPearlModule() {
        super(ModuleTab.COMBAT, "Target Pearl");
        this.activationMode = new ModeSetting(Lang.COMBAT_AUTOTOTEM_ACTIVATION_MODE, Lang.COMBAT_AUTOTOTEM_ACTIVATION_MODE_DESC).values(TotemActivationMode.class);
        this.keybind = new KeybindSetting(Lang.COMBAT_AUTOTOTEM_KEY, Lang.COMBAT_AUTOTOTEM_KEY_DESC).visible(() -> {
            return Boolean.valueOf(this.activationMode.isSelected(TotemActivationMode.BUTTON));
        });
        this.targetMode = new ModeSetting(Lang.TARGETPEARL_TARGET, Lang.TARGETPEARL_TARGET_DESC).values(TargetPearlMode.class);
        this.triggerDistance = new NumberSetting(Lang.TARGETPEARL_TRIGGER_DISTANCE, Lang.TARGETPEARL_TRIGGER_DISTANCE_DESC).currentValue(6.0f).range(3.0f, 15.0f).unit(SettingUnit.BLOCKS).step(1.0f);
        this.handledPearls = new ArrayList();
        this.stopwatch = new Stopwatch();
        this.mc = Mc.INSTANCE;
        addSettings(this.activationMode, this.keybind, this.targetMode, this.triggerDistance);
        register(RotationUpdateEvent.class, class345Var -> {
            if (isState() && this.mc.isWorldLoaded() && class345Var.isPre()) {
                InventoryService class011VarInventoryService = Expensive.INSTANCE.inventoryService();
                ClientPlayerEntity player = this.mc.getPlayer();
                LivingEntity livingEntityLastTarget = ((AttackAuraModule) Expensive.INSTANCE.moduleRepository().get(AttackAuraModule.class)).lastTarget();
                Predicate<ItemStack> predicate = itemStack -> {
                    return itemStack.getItem() == Items.ENDER_PEARL;
                };
                SlotSearchResult2 class329VarOrElse = class011VarInventoryService.searcher().findItem(predicate, InventoryScope.HOTBAR, InventoryScope.INVENTORY).orElse(null);
                if (class329VarOrElse == null || !class329VarOrElse.found() || player.getItemCooldownManager().isCoolingDown(class329VarOrElse.stack()) || !this.stopwatch.hasElapsed(1000L)) {
                    return;
                }
                if (!this.activationMode.isSelected(TotemActivationMode.BUTTON) || KeyboardUtil.isKeyPressed(this.keybind.getKey())) {
                    List<PearlTrajectory> list = PlayerSnapshotManager.INSTANCE.getProjectiles().stream().filter(class371Var -> {
                        return class371Var.entity() instanceof EnderPearlEntity;
                    }).map(class371Var2 -> {
                        return new PearlTrajectory((EnderPearlEntity) class371Var2.entity(), (List<TrajectoryPoint>) class371Var2.steps());
                    }).toList();
                    if (list.stream().anyMatch(class872Var -> {
                        return Objects.equals(class872Var.pearl.getOwner(), this.mc.getPlayer());
                    })) {
                        this.stopwatch.reset();
                        return;
                    }
                    Vec3d eyePos = SimulatedPlayer.simulateLocalPlayer(GrimDelayHandler.getTaskRunnableTime()).getEyePos();
                    EnderPearlEntity enderPearlEntity = new EnderPearlEntity(this.mc.getWorld(), this.mc.getPlayer(), class329VarOrElse.stack());
                    ProjectilePredictionModule class564Var = (ProjectilePredictionModule) Expensive.INSTANCE.moduleRepository().get(ProjectilePredictionModule.class);
                    list.stream().filter(class872Var2 -> {
                        return (this.handledPearls.contains(class872Var2.pearl.getUuid()) || class872Var2.pearl.getOwner() == null || FriendManager.isFriend(class872Var2.pearl.getOwner().getName().getString()) || (!this.targetMode.isSelected(TargetPearlMode.ALL) && (livingEntityLastTarget == null || !livingEntityLastTarget.equals(class872Var2.pearl.getOwner())))) ? false : true;
                    }).min(Comparator.comparingDouble(class872Var3 -> {
                        return Rotation.playerRotation().angleTo(RotationMath.INSTANCE.fromVec3d(((TrajectoryPoint) class872Var3.steps.getLast()).pos()));
                    })).ifPresent(class872Var4 -> {
                        EnderPearlEntity enderPearlEntity2 = class872Var4.pearl;
                        Vec3d vec3dPos = ((TrajectoryPoint) class872Var4.steps.getLast()).pos();
                        if (player.getPos().distanceTo(vec3dPos) <= this.triggerDistance.currentValue()) {
                            this.handledPearls.add(enderPearlEntity2.getUuid());
                        } else {
                            Rotation class007VarFromVec3d = RotationMath.INSTANCE.fromVec3d(vec3dPos.subtract(eyePos));
                            IntStream.rangeClosed(-89, 89).mapToObj(i -> {
                                return new Rotation(class007VarFromVec3d.getYaw(), i);
                            }).filter(class007Var -> {
                                return class564Var.checkTrajectory(eyePos, class007Var.getDirectionVector(), enderPearlEntity, 1.5d, true).getPos().distanceTo(vec3dPos) <= 1.0d;
                            }).max(Comparator.comparingDouble((v0) -> {
                                return v0.getPitch();
                            })).ifPresent(class007Var2 -> {
                                class011VarInventoryService.addTask(InventoryTask.create(predicate, class329VarOrElse, true, false, true).withRotation(class007Var2), this);
                                this.handledPearls.add(enderPearlEntity2.getUuid());
                                this.stopwatch.reset();
                            });
                        }
                    });
                }
            }
        });
    }
}
