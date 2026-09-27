package aethereal.util;
import aethereal.module.AncientXRayModule;
import aethereal.module.AntiAFKModule;
import aethereal.module.AntiBotModule;
import aethereal.module.AntiServerRPModule;
import aethereal.module.ArmTweaksModule;
import aethereal.module.ArrowsModule;
import aethereal.module.AspectRatioModule;
import aethereal.module.AttackAuraModule;
import aethereal.module.AuctionHelperModule;
import aethereal.module.AuctionRelistModule;
import aethereal.module.AutoArmorModule;
import aethereal.module.AutoAuthModule;
import aethereal.module.AutoDuelModule;
import aethereal.module.AutoEatModule;
import aethereal.module.AutoExplosionModule;
import aethereal.module.AutoFishModule;
import aethereal.module.AutoGAppleModule;
import aethereal.module.AutoJumpModule;
import aethereal.module.AutoLeaveModule;
import aethereal.module.AutoPotionModule;
import aethereal.module.AutoRespawnModule;
import aethereal.module.AutoSwapModule;
import aethereal.module.AutoToolModule;
import aethereal.module.AutoTotemModule;
import aethereal.module.AutoTpLootModule;
import aethereal.module.AutoWebModule;
import aethereal.module.BetterChatModule;
import aethereal.module.BlinkModule;
import aethereal.module.CameraTweaksModule;
import aethereal.module.ChamsModule;
import aethereal.module.ClanInvestModule;
import aethereal.module.ClickFriendModule;
import aethereal.module.ClickPearlModule;
import aethereal.module.ContainerStealerModule;
import aethereal.module.CriticalsModule;
import aethereal.module.CrosshairModule;
import aethereal.module.DeathCoordinatesModule;
import aethereal.module.ESPModule;
import aethereal.module.EdgeJumpModule;
import aethereal.module.ElytraBoosterModule;
import aethereal.module.ElytraHelperModule;
import aethereal.module.EnderChestPlusModule;
import aethereal.module.FTHelperModule;
import aethereal.module.FastBowModule;
import aethereal.module.FastBreakModule;
import aethereal.module.FastUseModule;
import aethereal.module.FlightModule;
import aethereal.module.FreeCameraModule;
import aethereal.module.FreeLookModule;
import aethereal.module.FullBrightModule;
import aethereal.module.HWHelperModule;
import aethereal.module.HitBoxesModule;
import aethereal.module.HitSoundsModule;
import aethereal.module.ItemScrollerModule;
import aethereal.module.ItemSwapFixModule;
import aethereal.module.ItemTrackerModule;
import aethereal.module.JumpCircleModule;
import aethereal.module.MineHelperModule;
import aethereal.module.Module;
import aethereal.type.ModuleCategory;
import aethereal.module.MultiActionsModule;
import aethereal.module.NameProtectModule;
import aethereal.module.NoFallModule;
import aethereal.module.NoFriendDamageModule;
import aethereal.module.NoInteractModule;
import aethereal.module.NoJumpDelayModule;
import aethereal.module.NoPlayerTraceModule;
import aethereal.module.NoPushModule;
import aethereal.module.NoServerRotationModule;
import aethereal.module.NoSlowModule;
import aethereal.module.NoWebModule;
import aethereal.module.NukerModule;
import aethereal.module.ParticlesModule;
import aethereal.module.PopChamsModule;
import aethereal.module.ProjectilePredictionModule;
import aethereal.module.PvPSafeModule;
import aethereal.module.RWGriefJoinerModule;
import aethereal.module.RWHelperModule;
import aethereal.module.RemovalsModule;
import aethereal.module.RemoveEffectsModule;
import aethereal.module.ScreenWalkModule;
import aethereal.module.SeeInvisiblesModule;
import aethereal.module.SkeletonModule;
import aethereal.module.SoundsModule;
import aethereal.module.SpeedModule;
import aethereal.module.SprintModule;
import aethereal.module.TPAcceptModule;
import aethereal.module.TapeMouseModule;
import aethereal.module.TargetESPModule;
import aethereal.module.TargetPearlModule;
import aethereal.module.TriggerBotModule;
import aethereal.module.VelocityModule;
import aethereal.module.WaterSpeedModule;
import aethereal.module.WidgetsModule;
import aethereal.module.WorldTweaksModule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModuleProvider {
    public List<Supplier<Module>> getAll() {
        ArrayList arrayList = new ArrayList();
        arrayList.addAll(combatModules());
        arrayList.addAll(movementModules());
        arrayList.addAll(renderModules());
        arrayList.addAll(playerModules());
        arrayList.addAll(miscModules());
        return arrayList;
    }

    public List<Supplier<Module>> combatModules() {
        return List.of(new Supplier[]{withCategory(AntiBotModule::new, ModuleCategory.DEFENSE), withCategory(AutoGAppleModule::new, ModuleCategory.AUTOMATION), withCategory(AutoSwapModule::new, ModuleCategory.AUTOMATION), withCategory(AutoTotemModule::new, ModuleCategory.DEFENSE), withCategory(HitBoxesModule::new, ModuleCategory.ATTACK), withCategory(NoFriendDamageModule::new, ModuleCategory.DEFENSE), withCategory(NoPlayerTraceModule::new, ModuleCategory.CONVENIENCE), withCategory(TriggerBotModule::new, ModuleCategory.ATTACK), withCategory(FastBowModule::new, ModuleCategory.AUTOMATION), withCategory(AutoExplosionModule::new, ModuleCategory.AUTOMATION), withCategory(CriticalsModule::new, ModuleCategory.ATTACK), withCategory(VelocityModule::new, ModuleCategory.ATTACK), withCategory(AttackAuraModule::new, ModuleCategory.ATTACK), withCategory(TargetPearlModule::new, ModuleCategory.AUTOMATION), withCategory(AutoWebModule::new, ModuleCategory.AUTOMATION)});
    }

    public List<Supplier<Module>> movementModules() {
        return List.of(new Supplier[]{withCategory(SprintModule::new, ModuleCategory.AUTOMATION), withCategory(WaterSpeedModule::new, ModuleCategory.BOOST), withCategory(NoFallModule::new, ModuleCategory.EXPLOITS), withCategory(AutoJumpModule::new, ModuleCategory.AUTOMATION), withCategory(EdgeJumpModule::new, ModuleCategory.AUTOMATION), withCategory(FlightModule::new, ModuleCategory.EXPLOITS), withCategory(SpeedModule::new, ModuleCategory.BOOST), withCategory(NoWebModule::new, ModuleCategory.ANTI_LIMITS), withCategory(ElytraBoosterModule::new, ModuleCategory.BOOST), withCategory(NoSlowModule::new, ModuleCategory.ANTI_LIMITS), withCategory(NoJumpDelayModule::new, ModuleCategory.ANTI_LIMITS), withCategory(ScreenWalkModule::new, ModuleCategory.ANTI_LIMITS)});
    }

    public List<Supplier<Module>> renderModules() {
        return List.of(new Supplier[]{withCategory(SkeletonModule::new, ModuleCategory.VISUALIZATION), withCategory(TargetESPModule::new, ModuleCategory.VISUALIZATION), withCategory(RemovalsModule::new, ModuleCategory.WORLD), withCategory(AspectRatioModule::new, ModuleCategory.INTERFACE), withCategory(FullBrightModule::new, ModuleCategory.WORLD), withCategory(ArrowsModule::new, ModuleCategory.VISUALIZATION), withCategory(PopChamsModule::new, ModuleCategory.VISUALIZATION), withCategory(JumpCircleModule::new, ModuleCategory.VISUALIZATION), withCategory(ChamsModule::new, ModuleCategory.VISUALIZATION), withCategory(WidgetsModule::new, ModuleCategory.INTERFACE), withCategory(ESPModule::new, ModuleCategory.VISUALIZATION), withCategory(WorldTweaksModule::new, ModuleCategory.WORLD), withCategory(ArmTweaksModule::new, ModuleCategory.VISUALIZATION), withCategory(CrosshairModule::new, ModuleCategory.INTERFACE), withCategory(CameraTweaksModule::new, ModuleCategory.INTERFACE), withCategory(ParticlesModule::new, ModuleCategory.VISUALIZATION), withCategory(ProjectilePredictionModule::new, ModuleCategory.VISUALIZATION)});
    }

    public List<Supplier<Module>> playerModules() {
        return List.of(new Supplier[]{withCategory(ClickPearlModule::new, ModuleCategory.CONVENIENCE), withCategory(ItemScrollerModule::new, ModuleCategory.CONVENIENCE), withCategory(RemoveEffectsModule::new, ModuleCategory.CONVENIENCE), withCategory(ContainerStealerModule::new, ModuleCategory.AUTOMATION), withCategory(FastUseModule::new, ModuleCategory.AUTOMATION), withCategory(AutoPotionModule::new, ModuleCategory.AUTOMATION), withCategory(DeathCoordinatesModule::new, ModuleCategory.CONVENIENCE), withCategory(AutoRespawnModule::new, ModuleCategory.AUTOMATION), withCategory(FastBreakModule::new, ModuleCategory.ANTI_LIMITS), withCategory(AutoToolModule::new, ModuleCategory.AUTOMATION), withCategory(AutoEatModule::new, ModuleCategory.AUTOMATION), withCategory(AutoFishModule::new, ModuleCategory.AUTOMATION), withCategory(FreeLookModule::new, ModuleCategory.CONVENIENCE), withCategory(NoServerRotationModule::new, ModuleCategory.ANTI_LIMITS), withCategory(NoPushModule::new, ModuleCategory.ANTI_LIMITS), withCategory(FreeCameraModule::new, ModuleCategory.CONVENIENCE), withCategory(AutoArmorModule::new, ModuleCategory.AUTOMATION), withCategory(NoInteractModule::new, ModuleCategory.ANTI_LIMITS), withCategory(NukerModule::new, ModuleCategory.AUTOMATION)});
    }

    public List<Supplier<Module>> miscModules() {
        return List.of(new Supplier[]{withCategory(AntiAFKModule::new, ModuleCategory.AUTOMATION), withCategory(AutoLeaveModule::new, ModuleCategory.AUTOMATION), withCategory(ItemSwapFixModule::new, ModuleCategory.UTILITIES), withCategory(AntiServerRPModule::new, ModuleCategory.UTILITIES), withCategory(TPAcceptModule::new, ModuleCategory.AUTOMATION), withCategory(ClickFriendModule::new, ModuleCategory.CONVENIENCE), withCategory(AuctionRelistModule::new, ModuleCategory.AUTOMATION), withCategory(AutoAuthModule::new, ModuleCategory.AUTOMATION), withCategory(BetterChatModule::new, ModuleCategory.CONVENIENCE), withCategory(NameProtectModule::new, ModuleCategory.UTILITIES), withCategory(MultiActionsModule::new, ModuleCategory.UTILITIES), withCategory(TapeMouseModule::new, ModuleCategory.UTILITIES), withCategory(ItemTrackerModule::new, ModuleCategory.UTILITIES), withCategory(ElytraHelperModule::new, ModuleCategory.UTILITIES), withCategory(FTHelperModule::new, ModuleCategory.UTILITIES), withCategory(RWHelperModule::new, ModuleCategory.UTILITIES), withCategory(HWHelperModule::new, ModuleCategory.UTILITIES), withCategory(SoundsModule::new, ModuleCategory.CONVENIENCE), withCategory(RWGriefJoinerModule::new, ModuleCategory.AUTOMATION), withCategory(AncientXRayModule::new, ModuleCategory.EXPLOITS), withCategory(HitSoundsModule::new, ModuleCategory.CONVENIENCE), withCategory(BlinkModule::new, ModuleCategory.EXPLOITS), withCategory(AuctionHelperModule::new, ModuleCategory.UTILITIES), withCategory(EnderChestPlusModule::new, ModuleCategory.CONVENIENCE), withCategory(AutoTpLootModule::new, ModuleCategory.AUTOMATION), withCategory(SeeInvisiblesModule::new, ModuleCategory.UTILITIES), withCategory(ClanInvestModule::new, ModuleCategory.AUTOMATION), withCategory(AutoDuelModule::new, ModuleCategory.AUTOMATION), withCategory(MineHelperModule::new, ModuleCategory.UTILITIES), withCategory(PvPSafeModule::new, ModuleCategory.UTILITIES)});
    }

    public Supplier<Module> withCategory(Supplier<Module> supplier, ModuleCategory class672Var) {
        return () -> {
            Module class605Var = (Module) supplier.get();
            class605Var.setCategory(class672Var);
            return class605Var;
        };
    }
}
