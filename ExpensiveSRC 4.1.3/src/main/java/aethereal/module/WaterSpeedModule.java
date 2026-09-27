package aethereal.module;
import aethereal.annotation.Aliases;
import aethereal.type.Mc;
import aethereal.ui.ModuleTab;
import aethereal.net.PacketSender;
import aethereal.event.PlayerTickEvent;
import aethereal.util.SlotSyncHandler;
import aethereal.math.Stopwatch;

import java.util.concurrent.TimeUnit;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.math.BlockPos;

@Aliases(aliases = {"Water Speed", "Fast Swim", "Water Movement", "Swim Boost", "Aqua Speed", "Fast Water Travel", "Water Sprint", "Speed in Water"})
public class WaterSpeedModule extends Module {
    public WaterSpeedModule() {
        super(ModuleTab.MOVEMENT, "Water Speed");
        Stopwatch class314Var = new Stopwatch();
        Mc class815Var = Mc.INSTANCE;
        register(PlayerTickEvent.class, class130Var -> {
            float f;
            if (isState() && class815Var.isWorldLoaded() && class130Var.isPre()) {
                ClientPlayerEntity player = class815Var.getPlayer();
                ClientWorld world = class815Var.getWorld();
                GameOptions gameOptions = class815Var.getGameOptions();
                if (SlotSyncHandler.lastSprinting) {
                    return;
                }
                if ((world.getBlockState(new BlockPos((int) player.getPos().x, (int) (player.getPos().y + 0.2d), (int) player.getPos().z)).getBlock() != Blocks.WATER && gameOptions.jumpKey.isPressed()) || !player.isTouchingWater()) {
                    class314Var.reset();
                }
                if (player.isTouchingWater() && class314Var.hasElapsed(160L, TimeUnit.MILLISECONDS)) {
                    if (gameOptions.jumpKey.isPressed()) {
                        f = 0.05f;
                    } else if (gameOptions.sneakKey.isPressed()) {
                        f = -0.05f;
                    } else {
                        f = !player.isSprinting() ? 0.005f : 0.0f;
                    }
                    float f2 = f;
                    PacketSender.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.PRESS_SHIFT_KEY));
                    PacketSender.sendPacket(new ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.RELEASE_SHIFT_KEY));
                    float f3 = player.isSprinting() ? 1.025f : 1.156f;
                    player.setVelocity(player.getVelocity().x * ((double) f3), player.getVelocity().y + ((double) f2), player.getVelocity().z * ((double) f3));
                }
            }
        });
    }
}
