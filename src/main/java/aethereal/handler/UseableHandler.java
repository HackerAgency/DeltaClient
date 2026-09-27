package aethereal.handler;

import aethereal.core.Delta;
import aethereal.core.EventTarget;
import aethereal.core.Interface;
import aethereal.event.HotbarEvent;
import aethereal.event.InputEvent;
import aethereal.event.TickEvent;
import aethereal.module.combat.MaceHelper;
import aethereal.module.combat.SwapSettings;
import aethereal.util.InventoryUtil;
import aethereal.util.Look;
import aethereal.util.Rotation;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.BundleItemSelectedC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;

import java.util.ArrayList;
import java.util.List;


public class UseableHandler extends BaseHandler implements Interface {
    private static final int DEFAULT_SWAP_DELAY_MS = 150;
    private final List<UseableTask> b = new ArrayList<>();

    public List<UseableTask> a() {
        return this.b;
    }

    @EventTarget
    public void onInputEvent(InputEvent event) {
        if (this.b.isEmpty()) {
            return;
        }
        SwapSettings swapSettings = Delta.getInstance().getModuleProcessor().t().bd();
        if (swapSettings == null || !swapSettings.m()) {
            return;
        }
        if (swapSettings.shouldDisableOnMove()) {
            event.setForward(0.0f);
            event.setStrafe(0.0f);
        }
        if (swapSettings.shouldDisableOnJump()) {
            event.setJump(false);
        }
    }

    @EventTarget
    public void onHotbarEvent(HotbarEvent event) {
        if (this.b.isEmpty()) {
            return;
        }
        int desiredSlot = getDesiredSelectedSlot(this.b.getFirst());
        if (desiredSlot != -1 && event.b() != desiredSlot) {
            event.a(true);
        }
    }

    @EventTarget
    public void onTickEvent(TickEvent event) {
        if (!this.b.isEmpty()) {
            SwapSettings swapSettings = Delta.getInstance().getModuleProcessor().t().bd();
            UseableTask task = this.b.getFirst();
            MaceHelper maceHelper = Delta.getInstance().getModuleProcessor().t().H();
            InventoryHandler inventoryHandler = Delta.getInstance().getModuleProcessor().v().getInventoryHandler();
            int hotbar = task.a().getItem() == Items.SPLASH_POTION ? InventoryUtil.b(task.a(), true) : InventoryUtil.a(task.a().getItem(), true);
            int inventory = task.a().getItem() == Items.SPLASH_POTION ? InventoryUtil.b(task.a(), false) : InventoryUtil.a(task.a().getItem(), false);
            if (task.d() == -1 && hotbar == -1 && inventory == -1) {
                this.b.remove(task);
                return;
            }
            int beforeUseDelayMs = swapSettings != null && swapSettings.m() ? swapSettings.getBeforeUseDelayMs() : DEFAULT_SWAP_DELAY_MS;
            int afterUseDelayMs = swapSettings != null && swapSettings.m() ? swapSettings.getAfterUseDelayMs() : DEFAULT_SWAP_DELAY_MS;
            
            if (task.d() == -1) {
                if (task.a().getItem() == Items.WIND_CHARGE && maceHelper.m() && maceHelper.pitchDown.c().booleanValue()) {
                    Delta.getInstance().getModuleProcessor().k().startAiming(new Rotation(Look.b(), 90.0f), 360.0f, 0, 5);
                    mc.player.setPitch(90.0f);
                }
                task.a(mc.player.getInventory().selectedSlot);
                task.c(0);
                task.a(System.currentTimeMillis());
                if (hotbar != -1) {
                    task.b(hotbar);
                    if (hotbar != mc.player.getInventory().selectedSlot) {
                        setSelectedSlot(hotbar);
                        return;
                    }
                    return;
                }
                if (inventory != -1) {
                    int bundle = InventoryUtil.a(mc.player.getInventory().getStack(inventory), task.a());
                    if (bundle != -1) {
                        mc.player.networkHandler.sendPacket(new BundleItemSelectedC2SPacket(inventory < 9 ? 36 + inventory : inventory, bundle));
                    }
                    task.b((bundle == -1 || !mc.player.getMainHandStack().isEmpty()) ? inventory : task.b());
                    Delta.getInstance().getModuleProcessor().v().getInventoryHandler().moveItem(inventory, mc.player.getInventory().selectedSlot, 1);
                    return;
                }
                return;
            }

            if (task.a().getItem() == Items.WIND_CHARGE && maceHelper.m() && maceHelper.pitchDown.c().booleanValue()) {
                Delta.getInstance().getModuleProcessor().k().startAiming(new Rotation(Look.b(), 90.0f), 360.0f, 0, 5);
                mc.player.setPitch(90.0f);
            }

            holdPreparedItem(task);

            if (!task.e()) {
                if (!hasDelayElapsed(task, beforeUseDelayMs)) {
                    return;
                }
                if (task.c() > 8 && !inventoryHandler.a().isEmpty()) {
                    return;
                }
                a(task);
                task.a(true);
                task.a(System.currentTimeMillis());
                if (afterUseDelayMs <= 0) {
                    restoreOriginalItem(task);
                    this.b.remove(task);
                    return;
                }
                return;
            }

            if (!hasDelayElapsed(task, afterUseDelayMs)) {
                return;
            }
            if (task.c() > 8 && !inventoryHandler.a().isEmpty()) {
                return;
            }
            restoreOriginalItem(task);
            this.b.remove(task);
        }
    }

    private boolean hasDelayElapsed(UseableTask task, int delayMs) {
        return System.currentTimeMillis() - task.f() >= Math.max(0, delayMs);
    }

    public void setSelectedSlot(int slot) {
        mc.player.getInventory().selectedSlot = slot;
    }

    public void a(UseableTask task) {
        ((platform.inject.invokers.ClientPlayerInteractionManagerInvoker) mc.interactionManager).invokeSendSequencedPacket(mc.world, sequence -> {
            float pitch = mc.player.getPitch();
            MaceHelper maceHelper = Delta.getInstance().getModuleProcessor().t().H();
            if (task.a().getItem() == Items.WIND_CHARGE && maceHelper.m() && maceHelper.pitchDown.c().booleanValue()) {
                pitch = 90.0f;
            }
            return new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, sequence, mc.player.getYaw(), pitch);
        });
    }

    public void a(ItemStack itemStack) {
        this.b.add(new UseableTask(itemStack));
    }

    private void holdPreparedItem(UseableTask task) {
        int desiredSlot = getDesiredSelectedSlot(task);
        if (desiredSlot != -1 && mc.player.getInventory().selectedSlot != desiredSlot) {
            setSelectedSlot(desiredSlot);
        }
    }

    private int getDesiredSelectedSlot(UseableTask task) {
        if (task.c() == -1) {
            return -1;
        }
        return task.c() > 8 ? task.b() : task.c();
    }

    private void restoreOriginalItem(UseableTask task) {
        if (task.c() == -1) {
            return;
        }
        if (mc.player.getInventory().getStack(task.c()).contains(DataComponentTypes.BUNDLE_CONTENTS)) {
            mc.player.getInventory().setStack(task.b(), ItemStack.EMPTY);
            Delta.getInstance().getModuleProcessor().v().getInventoryHandler().moveItem(task.c(), 36 + task.b(), 1);
        } else if (task.c() > 8) {
            Delta.getInstance().getModuleProcessor().v().getInventoryHandler().moveItem(task.b(), task.c(), 1);
        } else if (task.b() != mc.player.getInventory().selectedSlot) {
            setSelectedSlot(task.b());
        }
    }

    public static final class UseableTask {
        private final ItemStack itemStack;
        private int selectedSlot = -1;
        private int itemSlot = -1;
        private int stage = -1;
        private long stageStartedAtMs;
        private boolean used;

        public UseableTask(ItemStack itemStack) {
            this.itemStack = itemStack;
        }

        public void a(int selectedSlot) {
            this.selectedSlot = selectedSlot;
        }

        public void b(int itemSlot) {
            this.itemSlot = itemSlot;
        }

        public void c(int ticks) {
            this.stage = ticks;
        }

        public void a(boolean used) {
            this.used = used;
        }

        public void a(long stageStartedAtMs) {
            this.stageStartedAtMs = stageStartedAtMs;
        }

        public ItemStack a() {
            return this.itemStack;
        }

        public int b() {
            return this.selectedSlot;
        }

        public int c() {
            return this.itemSlot;
        }

        public int d() {
            return this.stage;
        }

        public boolean e() {
            return this.used;
        }

        public long f() {
            return this.stageStartedAtMs;
        }
    }
}
