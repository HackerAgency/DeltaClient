package aethereal.util;
import aethereal.Expensive;
import aethereal.type.Mc;
import aethereal.math.Rotation;
import aethereal.type.RotationDispatchMode;
import aethereal.internal.RotationDispatchModeSwitchMap;
import aethereal.module.ScreenWalkModule;

import java.util.Objects;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

public final class SwapUtil {
    public static RotationDispatchMode resolvedMethod() {
        return resolve(((ScreenWalkModule) Expensive.INSTANCE.moduleRepository().get(ScreenWalkModule.class)).getSwapMethod());
    }

    public static RotationDispatchMode resolve(RotationDispatchMode class389Var) {
        if (class389Var != RotationDispatchMode.AUTO) {
            return class389Var;
        }
        switch (ServerUtil.server) {
            case "FunTime":
                if (ServerUtil.getAnarchy() < 1000 && ServerUtil.getProtocolVersion() > 767) {
                    return RotationDispatchMode.DELAYED;
                }
                break;
            case "DexLand":
            case "MineBlaze":
                if (ServerUtil.getProtocolVersion() > 767) {
                    return RotationDispatchMode.DELAYED;
                }
                break;
            case "HolyWorld":
                return RotationDispatchMode.SEQUENTIAL;
        }
        return RotationDispatchMode.GRIM;
    }

    public static void swapAndExecute(int i, Rotation class007Var, boolean z, Runnable runnable) {
        RotationDispatchMode class389VarResolvedMethod = resolvedMethod();
        ClientPlayerEntity player = Mc.INSTANCE.getPlayer();
        if (player == null || !GrimDelayHandler.script.isFinished()) {
            return;
        }
        int[] iArr = {player.getInventory().selectedSlot};
        switch (RotationDispatchModeSwitchMap.dispatchModeSwitchMap[class389VarResolvedMethod.ordinal()]) {
            case 1:
                GrimDelayHandler.addTask(class007Var, false, () -> {
                    PlayerActionUtil.INSTANCE.clickSlot(player.currentScreenHandler.syncId, i, player.getInventory().selectedSlot, SlotActionType.SWAP, true);
                    runnable.run();
                    PlayerActionUtil.INSTANCE.clickSlot(player.currentScreenHandler.syncId, i, player.getInventory().selectedSlot, SlotActionType.SWAP, true);
                    PlayerActionUtil.INSTANCE.updateSlots(true);
                });
                break;
            case 2:
                GrimDelayHandler.addTask(class007Var, z, () -> {
                    PlayerActionUtil.INSTANCE.clickSlot(player.currentScreenHandler.syncId, i, player.getInventory().selectedSlot, SlotActionType.SWAP, true);
                    runnable.run();
                    PlayerActionUtil.INSTANCE.clickSlot(player.currentScreenHandler.syncId, i, player.getInventory().selectedSlot, SlotActionType.SWAP, true);
                    PlayerActionUtil.INSTANCE.updateSlots(true);
                });
                break;
            case 3:
                boolean z2 = RotationManager.computeRotationDifference(class007Var, RotationManager.INSTANCE.getCurrentRotation()) > 1.0d;
                GrimDelayHandler.rotateToAngle(z2, class007Var);
                GrimDelayHandler.script.addTickStep(0, () -> {
                    GrimDelayHandler.disableMoveKeys();
                    GrimDelayHandler.rotateToAngle(z2, class007Var);
                }).addTickStep(1, () -> {
                    PlayerActionUtil.INSTANCE.swapHand(i, Hand.MAIN_HAND, false);
                    runnable.run();
                    iArr[0] = player.getInventory().selectedSlot;
                }).addTickStep(2, () -> {
                    PlayerActionUtil.INSTANCE.swapHand(i, iArr[0], true, true);
                    GrimDelayHandler.enableMoveKeys();
                });
                break;
            case 4:
                boolean z3 = RotationManager.computeRotationDifference(class007Var, RotationManager.INSTANCE.getCurrentRotation()) > 1.0d;
                GrimDelayHandler.rotateToAngle(z3, class007Var);
                ActionScheduler class265VarAddTickStep = GrimDelayHandler.script.addTickStep(0, () -> {
                    PlayerActionUtil.INSTANCE.swapHand(i, Hand.MAIN_HAND, false);
                    GrimDelayHandler.rotateToAngle(z3, class007Var);
                    iArr[0] = player.getInventory().selectedSlot;
                });
                Objects.requireNonNull(runnable);
                class265VarAddTickStep.addTickStep(1, runnable::run).addTickStep(2, () -> {
                    PlayerActionUtil.INSTANCE.swapHand(i, iArr[0], true, true);
                });
                break;
        }
    }

    public static boolean needsStop() {
        return resolvedMethod() != RotationDispatchMode.VANILLA;
    }

    public static void swapAction(Runnable runnable) {
        swapAction(RotationManager.INSTANCE.getCurrentRotation(), true, runnable);
    }

    public static void swapAction(Rotation class007Var, boolean z, Runnable runnable) {
        RotationDispatchMode class389VarResolvedMethod = resolvedMethod();
        if (GrimDelayHandler.script.isFinished()) {
            if (Objects.requireNonNull(class389VarResolvedMethod) == RotationDispatchMode.VANILLA) {
                GrimDelayHandler.addTask(class007Var, false, runnable);
            } else {
                GrimDelayHandler.addTask(class007Var, z, runnable);
            }
        }
    }

    public static void swapToOffhand(int i) {
        RotationDispatchMode class389VarResolvedMethod = resolvedMethod();
        if (Mc.INSTANCE.getPlayer() != null && GrimDelayHandler.script.isFinished()) {
            if (Objects.requireNonNull(class389VarResolvedMethod) != RotationDispatchMode.VANILLA) {
                GrimDelayHandler.addTask(true, () -> {
                    PlayerActionUtil.INSTANCE.windowClick(SlotActionType.SWAP, i, 40, false);
                    PlayerActionUtil.INSTANCE.updateSlots(true);
                });
            } else {
                PlayerActionUtil.INSTANCE.windowClick(SlotActionType.SWAP, i, 40, false);
                PlayerActionUtil.INSTANCE.updateSlots(true);
            }
        }
    }

    public SwapUtil() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
