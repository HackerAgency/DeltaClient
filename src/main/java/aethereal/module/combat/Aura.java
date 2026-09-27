package aethereal.module.combat;

import aethereal.core.*;
import aethereal.core.Module;
import aethereal.event.AttackEvent;
import aethereal.event.InputEvent;
import aethereal.event.WillLandEvent;
import aethereal.module.combat.neuro.NeuroAuraExec;
import aethereal.module.combat.neuro.NeuroAuraLearn;
import aethereal.setting.BooleanSetting;
import aethereal.setting.ModeSetting;
import aethereal.setting.MultiModeSetting;
import aethereal.setting.SliderSetting;
import aethereal.ui.screen.AssistantScreen;
import aethereal.ui.screen.GUIScreen;
import aethereal.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.AmbientEntity;
import net.minecraft.entity.mob.FlyingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AllayEntity;
import net.minecraft.entity.passive.GolemEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@ModuleRegister(name = "Aura", description = "Автоматически атакует цели рядом с вами", category = Category.Combat)
public class Aura extends Module {

    final float[] timers = { -1.0f, -1.0f, -1.0f, -1.0f, 0.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f };
    final int[] savedSlots = { -1, -1 };
    private final ModeSetting rotationType = new ModeSetting("Выберите тип наведения", "ФанТайм", "ФанТайм",
            "ФанТайм ФОВ",
            "Легит",
            "Нейро");
    private final ModeSetting neuroMode = new ModeSetting("Нейро режим", "Запоминающий", "Запоминающий", "Выполняющий").a(() -> this.rotationType.l("Нейро"));
    private int neuroLearnTicks = 0;
    private int neuroLearnAttacks = 0;
    private final BooleanSetting neuroShake = new BooleanSetting("Нейро шейк", true).a(() -> this.rotationType.l("Нейро") && this.neuroMode.l("Выполняющий"));
    private final SliderSetting neuroSmooth = new SliderSetting("Нейро плавность", 0.76f, 0.20f, 0.95f, 0.01f).a(() -> this.rotationType.l("Нейро") && this.neuroMode.l("Выполняющий"));
    private final MultiModeSetting targetSettings = new MultiModeSetting("Цели для атаки",
            new BooleanSetting("Без брони", true),
            new BooleanSetting("Враждебные мобы", false), new BooleanSetting("Животные", false),
            new BooleanSetting("Друзья", false), new BooleanSetting("Игроки", true));
    private final SliderSetting attackDistance = new SliderSetting("Дистанция атаки", 3.0f, 0.1f, 6.0f, 0.1f);
    private final SliderSetting extraReach = new SliderSetting("Дополнительная дистанция", 0.5f, 0.1f, 3.0f, 0.1f);
    private final BooleanSetting onlyCrits = new BooleanSetting("Только критические удары", true);
    private final BooleanSetting adaptiveHits = new BooleanSetting("Адаптивные удары", true).a(() -> {
        return this.onlyCrits.c();
    });
    private final MultiModeSetting dontHitWhen = new MultiModeSetting("Не бить когда",
            new BooleanSetting("Используется предмет", true), new BooleanSetting("Открыт контейнер", true),
            new BooleanSetting("Враг за стеной", true));
    private final BooleanSetting shieldBreaking = new BooleanSetting("Пробитие щита", true);
    private final BooleanSetting smartSprint = new BooleanSetting("Умный спринт", false);
    private final BooleanSetting tpsSync = new BooleanSetting("TPS синхронизация", true);
    private final ModeSetting targetPriority = new ModeSetting("Приоритет цели", "Прицел", "Прицел", "Дистанция", "ХП");
    private final ModeSetting movementCorrection = new ModeSetting("Коррекция движения", "Фокус", "Фокус", "Свободно");
    private final ModeSetting targetVisualization = new ModeSetting("Визуализация цели", "Сферы", "Сферы", "Круг",
            "Тест");
    private final float[] pitchHistory = new float[30];
    public int attackCooldown = 0;
    private int hitCounter = 0;
    private final int[] funtimeAttackDelays = {10, 10, 10, 10, 12};
    private final int[] legitAttackDelays = {10, 10, 10, 13};
    private final SecureRandom secureRandom = new SecureRandom();
    private Rotation bezierStartRotation;
    private Rotation bezierTargetRotation;
    private float bezierControlOffsetX;
    private float bezierControlOffsetY;
    private float bezierProgress;
    private long bezierLastTimeNanos;
    boolean willLand;
    boolean randomDirection = false;
    private LivingEntity target;
    private LivingEntity lastTarget;
    private Box cachedHitbox;
    private Vec3d cachedPoint;
    private Rotation cachedRotation;
    private final NeuroAuraExec neuroExec = new NeuroAuraExec();
    private final NeuroAuraLearn neuroLearn = new NeuroAuraLearn();

    public Aura() {
        a(this.attackDistance, this.extraReach, this.rotationType, this.neuroMode, this.neuroShake, this.neuroSmooth,
                this.movementCorrection, this.targetVisualization,
                this.targetPriority, this.targetSettings, this.dontHitWhen, this.onlyCrits, this.adaptiveHits,
                this.shieldBreaking, this.smartSprint, this.tpsSync);
        this.neuroLearn.bindExec(this.neuroExec);
        this.neuroExec.attachLearn(this.neuroLearn);

        this.neuroMode.<ModeSetting>a((String newMode) -> {
            if ("Выполняющий".equalsIgnoreCase(newMode)) {
                // Switching to exec mode: flush learn data, reload exec model
                this.neuroLearn.flushSave(this);
                this.neuroExec.reloadModel();
                int steps = this.neuroExec.getModel().getStepCount();
                ChatUtil.sendMessage("§aНейро-модель перезагружена. §7Шагов обучения: §f" + steps);
            } else {
                // Switching to learn mode
                this.neuroLearnTicks = 0;
                this.neuroLearnAttacks = 0;
                ChatUtil.sendMessage("§eРежим запоминания. §7Начинайте бить мобов/игроков вручную.");
            }
        });
    }

    public ModeSetting getVisualizationMode() {
        return this.targetVisualization;
    }

    public LivingEntity getTarget() {
        return this.target;
    }

    public Box getCachedHitbox() {
        return this.cachedHitbox;
    }

    public Vec3d getCachedPoint() {
        return this.cachedPoint;
    }

    public Rotation getCachedRotation() {
        return this.cachedRotation;
    }

    public BooleanSetting getTpsSync() {
        return this.tpsSync;
    }

    public boolean neuroEnabled() {
        return this.rotationType.l("Нейро");
    }

    public boolean neuroExec() {
        return neuroEnabled() && this.neuroMode.l("Выполняющий");
    }

    public boolean neuroLearn() {
        return neuroEnabled() && this.neuroMode.l("Запоминающий");
    }

    @Override
    public void b() {
        if (this.timers[9] == -1.0f) {
            this.timers[9] = (int) MathUtil.a(9.0f, 13.0f);
        }
        super.b();
        this.timers[8] = 2.0f;
        this.timers[10] = 0.0f;
        this.timers[11] = 0.0f;
        Arrays.fill(this.pitchHistory, mc.player != null ? mc.player.getPitch() : 0.0f);
        this.target = null;
        this.lastTarget = null;
        this.cachedHitbox = null;
        this.cachedPoint = null;
        this.cachedRotation = null;
        this.neuroLearn.bindExec(this.neuroExec);
        this.neuroExec.attachLearn(this.neuroLearn);
        this.neuroExec.onActivate();
        this.neuroLearn.onActivate(this);
    }

    @Override
    public void c() {
        super.c();
        Delta.getInstance().getModuleProcessor().k().reset();
        this.timers[0] = -1.0f;
        this.timers[1] = 0.0f;
        this.timers[2] = 0.0f;
        this.timers[3] = 0.0f;
        this.timers[5] = -1.0f;
        this.timers[8] = 1.0f;
        this.timers[9] = -1.0f;
        this.timers[10] = 0.0f;
        this.timers[11] = 0.0f;
        this.attackCooldown = 0;
        this.hitCounter = 0;
        this.target = null;
        this.lastTarget = null;
        this.cachedHitbox = null;
        this.cachedPoint = null;
        this.cachedRotation = null;
        this.bezierProgress = 1.0f;
        this.bezierLastTimeNanos = System.nanoTime();
        this.neuroLearn.onDeactivate(this);
        this.neuroExec.onDeactivate();
    }

    @EventTarget
    public void onInput(InputEvent e) {
        if (this.target != null) {
            MoveUtil.a(e, !this.movementCorrection.l("Фокус") ? Look.b() : this.timers[1], 2);
        }
        if (this.timers[0] > 0.0f && this.target != null
                && AuraUtil.a(this.target, this.attackDistance.c().floatValue())) {
            e.setForward(0.0f);
            e.setStrafe(0.0f);
            float[] fArr = this.timers;
            fArr[0] = fArr[0] - 1.0f;
        }
    }

    @EventTarget
    public void onGlobalEvent(GlobalEvent e) {
        this.neuroExec.onTick();
        this.neuroLearn.onTick(this);
        MaceHelper maceHelper = Delta.getInstance().getModuleProcessor().t().H();
        boolean disableAuraRotation = maceHelper.isThrowingWindCharge();
        
        // Всегда ищем цель для автопереключения булавы
        if (!isValidTarget(this.target) || (MaceUtil.a() && !this.willLand
                && !mc.player.getItemCooldownManager().isCoolingDown(Items.MACE.getDefaultStack()))) {
            LivingEntity prev = this.target;
            boolean fresh = !isValidTarget(prev);
            this.target = (fresh && this.dontHitWhen.a("Враг за стеной").c().booleanValue())
                    ? findTargetWithParam(false).or(() -> {
                        return findTargetWithParam(true);
                    }).orElse(null)
                    : findTarget().orElse(null);
            if (this.target != prev && this.target != null) {
                if (neuroExec()) {
                    this.neuroExec.onTargetSelected(this.target);
                }
                this.timers[10] = 0.0f;
                this.timers[11] = 0.0f;
                Arrays.fill(this.pitchHistory, mc.player != null ? mc.player.getPitch() : 0.0f);
            }
        }
        
        if (disableAuraRotation) {
            // Блокируем только ротацию и атаки, но не поиск цели
            Delta.getInstance().getModuleProcessor().k().startAiming(new Rotation(Look.b(), 90.0f), 360.0f, 0, 5);
            restoreSelectedSlot();
            this.timers[8] = 1.0f;
            return;
        }

        SwapSettings swapSettings = Delta.getInstance().getModuleProcessor().t().bd();
        if (swapSettings != null && swapSettings.m() && swapSettings.shouldDisableOnAura()
                && !Delta.getInstance().getModuleProcessor().v().getUseableHandler().a().isEmpty()) {
            restoreSelectedSlot();
            this.timers[8] = 1.0f;
            return;
        }

        restoreSelectedSlot();
        if (this.target != null) {
            this.lastTarget = this.target;

            Vec3d eye = mc.player.getEyePos();
            double reach = this.attackDistance.c().floatValue();
            boolean throughWalls = this.rotationType.c().contains("ФанТайм")
                    || !this.dontHitWhen.a("Враг за стеной").c().booleanValue();
            Vec3d targetPosition = AuraUtil.a(eye, this.target, reach, throughWalls);
            float yawToTarget = targetPosition == Vec3d.ZERO ? Look.b()
                    : (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(targetPosition.z, targetPosition.x)) - 90.0d);
            float pitchToTarget = targetPosition == Vec3d.ZERO ? Look.c()
                    : (float) (-Math.toDegrees(Math.atan2(targetPosition.y, Math.hypot(targetPosition.x, targetPosition.z))));

            this.cachedHitbox = this.target.getBoundingBox();
            this.cachedPoint = targetPosition == Vec3d.ZERO ? this.target.getEyePos() : eye.add(targetPosition);
            this.cachedRotation = new Rotation(yawToTarget, pitchToTarget);

            if (neuroExec()) {
                boolean plannedAttack = canAttack();
                Vec3d execPoint = this.neuroExec.adjustPointForExec(this.target, this.cachedPoint, plannedAttack,
                        this.neuroSmooth.c().floatValue());
                if (execPoint != null) {
                    this.cachedPoint = execPoint;
                    this.cachedRotation = Rotation.a(eye, execPoint);
                }
            }

            if (neuroLearn()) {
                // В запоминающем режиме Aura НЕ наводится сама и НЕ бьет сама!
                // Игрок целится и бьет руками, а нейросеть обучается на движениях игрока.
                this.neuroLearn.learnStep(this, false);
                this.neuroLearnTicks++;
                if (this.neuroLearnTicks % 200 == 0) {
                    int steps = this.neuroExec.getModel().getStepCount();
                    ChatUtil.sendMessage("§bНейро обучение §7| Тики: §f" + this.neuroLearnTicks + " §7| Атаки: §f" + this.neuroLearnAttacks + " §7| Шаги NN: §f" + steps);
                }
                return;
            }

            performAttack();
            rotateToTarget();
            performAttack();
            return;
        } else if (neuroExec()) {
            Rotation shake = this.neuroExec.handleNoTargetShake(this.lastTarget, this.neuroShake.c().booleanValue());
            if (shake != null) {
                Delta.getInstance().getModuleProcessor().k().startAiming(shake, 180.0f, 1, 2);
            }
        }
        this.timers[8] = 1.0f;
    }

    @EventTarget
    public void onAttack(AttackEvent e) {
        if (e.b() instanceof LivingEntity livingTarget && neuroLearn()) {
            if (this.target == null || !isValidTarget(this.target)) {
                this.target = livingTarget;
            }
            this.cachedHitbox = this.target.getBoundingBox();
            Vec3d eye = mc.player.getEyePos();
            Vec3d targetPosition = this.target.getEyePos();
            
            // Используем мультипоинты для плавного движения внутри хитбокса
            boolean ignoreWalls = this.dontHitWhen.a("Враг за стеной") != null 
                    && this.dontHitWhen.a("Враг за стеной").c().booleanValue();
            List<Vec3d> multiPoints = AuraUtil.generateMultiPoints(this.target, 
                    this.attackDistance.c().floatValue(), ignoreWalls);
            Vec3d bestPoint = AuraUtil.findBestPoint(multiPoints, eye);
            if (bestPoint != null) {
                targetPosition = bestPoint;
            }
            
            if (this.rotationType.c().equals("ФанТайм ФОВ")) {
                targetPosition = this.target.getPos();
            }
            this.cachedPoint = targetPosition == Vec3d.ZERO ? this.target.getEyePos() : eye.add(targetPosition);
            float yawToTarget = targetPosition == Vec3d.ZERO ? Look.b()
                    : (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(targetPosition.z, targetPosition.x)) - 90.0d);
            float pitchToTarget = targetPosition == Vec3d.ZERO ? Look.c()
                    : (float) (-Math.toDegrees(Math.atan2(targetPosition.y, Math.hypot(targetPosition.x, targetPosition.z))));
            this.cachedRotation = new Rotation(yawToTarget, pitchToTarget);
            this.neuroLearn.learnStep(this, true);
            this.neuroLearnAttacks++;
            if (this.neuroLearnAttacks <= 5 || this.neuroLearnAttacks % 10 == 0) {
                ChatUtil.sendMessage("§aАтака #" + this.neuroLearnAttacks + " записана §7| NN шаги: §f" + this.neuroExec.getModel().getStepCount());
            }
        }
    }

    @EventTarget
    public void onWillLand(WillLandEvent e) {
        this.willLand = e.b() && !mc.player.isOnGround();
    }

    private int findAxeSlot(int from, int to) {
        for (int i = from; i < to; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    private void restoreSelectedSlot() {
        if (this.shieldBreaking.c().booleanValue() && this.target != null && this.target.isBlocking()) {
            return;
        }
        if (this.savedSlots[0] != -1) {
            mc.player.getInventory().selectedSlot = this.savedSlots[0];
            this.savedSlots[0] = -1;
        }
        if (this.savedSlots[1] != -1
                && Delta.getInstance().getModuleProcessor().v().getInventoryHandler().a().isEmpty()) {
            Delta.getInstance().getModuleProcessor().v().getInventoryHandler()
                    .moveItem(mc.player.getInventory().selectedSlot, this.savedSlots[1], 1);
            this.savedSlots[1] = -1;
        }
    }

    private void performAttack() {
        if (neuroLearn()) {
            return;
        }
        if (!AuraUtil.a(mc.player.getYaw(), mc.player.getPitch(), this.attackDistance.c().floatValue(), this.target,
                !this.dontHitWhen.a("Враг за стеной").c().booleanValue())) {
            return;
        }
        if (this.shieldBreaking.c().booleanValue() && this.target.isBlocking()) {
            if (mc.player.getMainHandStack().getItem() instanceof AxeItem) {
                mc.interactionManager.attackEntity(mc.player, this.target);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            int hotbar = findAxeSlot(0, 9);
            if (hotbar != -1) {
                if (mc.player.getInventory().selectedSlot != hotbar) {
                    if (this.savedSlots[0] == -1) {
                        this.savedSlots[0] = mc.player.getInventory().selectedSlot;
                    }
                    mc.player.getInventory().selectedSlot = hotbar;
                }
            } else {
                int inventory = findAxeSlot(9, 36);
                if (inventory != -1 && this.savedSlots[1] == -1
                        && Delta.getInstance().getModuleProcessor().v().getInventoryHandler().a().isEmpty()) {
                    this.savedSlots[1] = inventory;
                    Delta.getInstance().getModuleProcessor().v().getInventoryHandler().moveItem(inventory,
                            mc.player.getInventory().selectedSlot,
                            1);
                }
            }
        }
        if (canAttack()) {
            if (this.rotationType.l("Нейро") && this.neuroMode.l("Запоминающий")) {
                this.neuroExec.recordAttack(this.target);
            }
            boolean skip = false;
            if ((Delta.getInstance().getModuleProcessor().t().H().e || (mc.player.fallDistance > 2.0f
                    && Delta.getInstance().getModuleProcessor().t().H().c.c().booleanValue()))
                    && InventoryUtil.b(Items.MACE) != -1) {
                if (mc.player.fallDistance < 1.5f) {
                    return;
                }
                double landDist = MaceUtil.a(mc.player, mc.world).map(pos -> {
                    return Double.valueOf(pos.distanceTo(this.target.getPos()));
                }).orElse(Double.valueOf(33.0d)).doubleValue();
                boolean hitNow = landDist > 2.0d;
                if ((!this.willLand && !MaceUtil.b()
                        && Delta.getInstance().getModuleProcessor().t().H().b.c().booleanValue()
                        && !hitNow) || !MaceUtil.a() || mc.player.isGliding()) {
                    return;
                } else {
                    skip = true;
                }
            }
            if (((platform.inject.accessors.ClientPlayerEntityAccessor) mc.player).getWasSprinting()
                    && !mc.player.isTouchingWater() && !mc.player.isInLava() && !mc.player.isSwimming()
                    && !mc.player.isOnGround() && !skip) {
                if (!this.smartSprint.c().booleanValue()) {
                    ((platform.inject.accessors.ClientPlayerEntityAccessor) mc.player).setWasSprinting(false);
                    mc.player.setSprinting(false);
                    mc.player.networkHandler.sendPacket(
                            new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                    this.timers[0] = 1.0f;
                } else {
                    this.timers[0] = 1.0f;
                    if (((platform.inject.accessors.ClientPlayerEntityAccessor) mc.player).getWasSprinting()) {
                        return;
                    }
                }
            }
            if (mc.interactionManager != null) {
                this.timers[3] = 0.0f;
                mc.interactionManager.attackEntity(mc.player, this.target);
                mc.player.swingHand(Hand.MAIN_HAND);
                this.attackCooldown = 0;
                this.hitCounter++;
                this.timers[5] = MathUtil.a(8.0f, 10.0f);
                this.timers[9] = (int) MathUtil.a(9.0f, 13.0f);
                if (this.timers[2] == -1.0f) {
                    this.timers[4] = (int) MathUtil.a(30.0f, 35.0f);
                }
                float[] fArr = this.timers;
                fArr[2] = fArr[2] + 1.0f;
            }
        }
    }

    public boolean canAttack() {
        if (this.dontHitWhen.a("Используется предмет") != null
                && this.dontHitWhen.a("Используется предмет").c().booleanValue()
                && mc.player.isUsingItem() && mc.player.getItemUseTimeLeft() > 0 && this.attackCooldown >= 8) {
            this.attackCooldown = 8;
            return false;
        }
        if ((this.dontHitWhen.a("Открыт контейнер") != null && this.dontHitWhen.a("Открыт контейнер").c().booleanValue()
                && mc.currentScreen != null && !(mc.currentScreen instanceof GUIScreen)
                && !(mc.currentScreen instanceof AssistantScreen))
                || !AuraUtil.a(this.target, this.attackDistance.c().floatValue())) {
            return false;
        }
        boolean usingMace = Delta.getInstance().getModuleProcessor().t().H().e || mc.player.getMainHandStack().isOf(Items.MACE);
        if (usingMace) {
            // Игнорируем кулдаун при атаке булавой для 4-8 cps
            if (this.attackCooldown < 3) {
                return false;
            }
        } else if (mc.player.fallDistance > 1.5f) {
            if (mc.player.getItemCooldownManager().isCoolingDown(mc.player.getMainHandStack())
                    || this.attackCooldown <= 3) {
                return false;
            }
        } else if (MaceUtil.a()) {
            if (mc.player.getItemCooldownManager().isCoolingDown(mc.player.getMainHandStack())
                    || mc.player.getAttackCooldownProgress(0.5f) < 0.9f) {
                return false;
            }
        } else if (mc.player.getAttackCooldownProgress(0.5f) < 0.9f || this.attackCooldown < getAttackDelay()) {
            return false;
        }
        
        // Улучшенная логика критов
        if (this.onlyCrits.c().booleanValue()) {
            if (canCrit()) {
                return true;
            }
            return this.adaptiveHits.c().booleanValue() && mc.player.isOnGround()
                    && !mc.player.input.playerInput.jump();
        }
        
        return AuraUtil.c()
                || (this.adaptiveHits.c().booleanValue() && mc.player.isOnGround()
                        && !mc.player.input.playerInput.jump())
                || !AuraUtil.b();
    }

    private boolean canCrit() {
        // Проверки эффектов и состояния как в Expensive
        if (mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.LEVITATION)
                || mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)
                || mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOW_FALLING)) {
            return true;
        }
        if (mc.player.isTouchingWater() || mc.player.isInLava() || mc.player.isClimbing()
                || mc.player.getAbilities().flying || mc.player.isGliding()) {
            return true;
        }
        // Проверка падения
        if (mc.player.fallDistance > 0.0f && !mc.player.isOnGround()) {
            // Проверка скорости падения для момента крита
            double velocityY = mc.player.getVelocity().y;
            if (velocityY < -0.3) {
                return false; // Слишком быстрое падение
            }
            return true;
        }
        return false;
    }

    private boolean isEntityReachable(LivingEntity entity) {
        if (Delta.getInstance().getModuleProcessor().t().G().m() && mc.player.isGliding()) {
            return true;
        }
        return AuraUtil.a((Entity) entity, ((double) (this.attackDistance.c().floatValue()
                + this.extraReach.c().floatValue()))
                + (mc.player.getVelocity().length() * 3.0d)
                + ((double) ((InventoryUtil.b(Items.MACE) == -1 || ((double) mc.player.fallDistance) <= 1.5d) ? 0.0f
                        : 1.5f))
                + ((double) ((Delta.getInstance().getModuleProcessor().t().H().m() && InventoryUtil.b(Items.MACE) != -1
                        && MaceUtil.a(mc.player, mc.world).map(p -> {
                            return Boolean.valueOf(mc.player.getY() - p.getY() > 2.0d);
                        }).orElse(false).booleanValue()) ? 10 : 0)));
    }

    private Optional<LivingEntity> findTarget() {
        return findTargetWithParam(true);
    }

    private Optional<LivingEntity> findTargetWithParam(boolean allowBehindWalls) {
        Comparator<LivingEntity> comparatorComparingDouble;
        Comparator<LivingEntity> order;
        if (mc.world == null || mc.player == null) {
            return Optional.empty();
        }
        Vec3d eye = mc.player.getEyePos();
        double reach = this.attackDistance.c().floatValue() + this.extraReach.c().floatValue();
        if (MaceUtil.a()) {
            Vec3d landing = MaceUtil.a(mc.player, mc.world).orElse(null);
            Vec3d landingEye = landing != null ? landing.add(0.0d, mc.player.getStandingEyeHeight(), 0.0d) : null;
            order = Comparator
                    .comparing((LivingEntity e) -> Boolean.valueOf(
                            !AuraUtil.a(eye, e, reach) && (landingEye == null || !AuraUtil.a(landingEye, e, reach))))
                    .thenComparing((LivingEntity e2) -> Boolean.valueOf(mc.player.fallDistance > 1.0f && !hasArmor(e2)))
                    .thenComparingDouble((LivingEntity v0) -> AuraUtil.a(v0));
        } else {
            switch (this.targetPriority.c()) {
                case "Дистанция":
                    comparatorComparingDouble = Comparator.comparingDouble((v0) -> {
                        return AuraUtil.a(v0);
                    });
                    break;
                case "ХП":
                    comparatorComparingDouble = Comparator.comparingDouble((v0) -> {
                        return v0.getHealth();
                    });
                    break;
                default:
                    comparatorComparingDouble = Comparator.comparingDouble(e3 -> {
                        return Math.acos(MathHelper.clamp(Vec3d.fromPolar(mc.player.getPitch(), mc.player.getYaw())
                                .dotProduct(e3.getBoundingBox().getCenter().subtract(eye).normalize()), -1.0d, 1.0d));
                    });
                    break;
            }
            order = comparatorComparingDouble;
        }
        Stream<LivingEntity> stream2 = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
                .filter(LivingEntity.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(e4 -> e4 != mc.player && e4.isAlive())
                .filter(this::isEntityReachable)
                .filter(this::isValidTarget);
        if (!allowBehindWalls) {
            stream2 = stream2.filter(e5 -> {
                return AuraUtil.a(eye, e5, reach);
            });
        }
        return stream2.min(order);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == null || !entity.isAlive() || !isEntityReachable(entity)) {
            return false;
        }
        if (entity instanceof PlayerEntity) {
            boolean isFriend = Delta.getInstance().getModuleProcessor().e().d(entity.getName().getString());
            boolean naked = Stream.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
                    .noneMatch(slot -> {
                        return entity.getEquippedStack(slot).getItem() instanceof ArmorItem;
                    });
            if (!isSettingEnabled("Игроки")) {
                return false;
            }
            if (isFriend) {
                return isSettingEnabled("Друзья");
            }
            return !naked || isSettingEnabled("Без брони");
        }
        if ((entity instanceof HostileEntity) || (entity instanceof SlimeEntity) || (entity instanceof FlyingEntity)
                || (entity instanceof EnderDragonEntity)) {
            return isSettingEnabled("Враждебные мобы");
        }
        if ((entity instanceof PassiveEntity) || (entity instanceof GolemEntity) || (entity instanceof AllayEntity)
                || (entity instanceof AmbientEntity)) {
            return isSettingEnabled("Животные");
        }
        return false;
    }

    private boolean isSettingEnabled(String name) {
        BooleanSetting setting = this.targetSettings.a(name);
        return setting != null && setting.c().booleanValue();
    }

    private boolean hasArmor(LivingEntity entity) {
        return Stream.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
                .anyMatch(s -> {
                    return entity.getEquippedStack(s).getItem() instanceof ArmorItem;
                });
    }

    private int getAttackDelay() {
        int i = this.hitCounter;
        if (this.rotationType.c().contains("ФанТайм")) {
            return this.funtimeAttackDelays[i % this.funtimeAttackDelays.length];
        } else if (this.rotationType.l("Легит")) {
            return this.legitAttackDelays[i % this.legitAttackDelays.length];
        }
        return 10;
    }

    private float randomLerp(float min, float max) {
        return MathHelper.lerp(secureRandom.nextFloat(), min, max);
    }

    private Rotation cubicBezier(Rotation start, Rotation target, float progress, float offsetX1, float offsetY1, float offsetX2, float offsetY2) {
        float t = MathHelper.clamp(progress, 0.0f, 1.0f);
        float oneMinusT = 1.0f - t;
        float t2 = t * t;
        float oneMinusT2 = oneMinusT * oneMinusT;
        
        float yawDiff = MathHelper.wrapDegrees(target.c() - start.c());
        float pitchDiff = target.d() - start.d(); // Pitch не заворачивается через 180 градусов
        
        // Две контрольные точки для кубической кривой Безье
        float control1Yaw = start.c() + (yawDiff * 0.25f) + offsetX1;
        float control1Pitch = start.d() + (pitchDiff * 0.25f) + offsetY1;
        float control2Yaw = start.c() + (yawDiff * 0.75f) + offsetX2;
        float control2Pitch = start.d() + (pitchDiff * 0.75f) + offsetY2;
        
        // Кубическая кривая Безье: B(t) = (1-t)^3*P0 + 3(1-t)^2*t*P1 + 3(1-t)*t^2*P2 + t^3*P3
        float bezierYaw = (oneMinusT2 * oneMinusT * start.c()) 
                + (3.0f * oneMinusT2 * t * control1Yaw) 
                + (3.0f * oneMinusT * t2 * control2Yaw) 
                + (t2 * t * target.c());
        float bezierPitch = (oneMinusT2 * oneMinusT * start.d()) 
                + (3.0f * oneMinusT2 * t * control1Pitch) 
                + (3.0f * oneMinusT * t2 * control2Pitch) 
                + (t2 * t * target.d());
        
        // Клипим pitch в допустимые пределы (-90 до 90)
        bezierPitch = MathHelper.clamp(bezierPitch, -90.0f, 90.0f);
        
        return new Rotation(bezierYaw, bezierPitch);
    }

    private void rotateToTarget() {
        if (neuroLearn()) {
            return;
        }
        MaceHelper maceHelper = Delta.getInstance().getModuleProcessor().t().H();
        if (maceHelper.isThrowingWindCharge()) {
            Delta.getInstance().getModuleProcessor().k().startAiming(new Rotation(Look.b(), 90.0f), 360.0f, 0, 5);
            return;
        }
        Vec3d eye = mc.player.getEyePos();
        LivingEntity target = this.target;
        double reach = this.attackDistance.c().floatValue();
        boolean throughWalls = this.rotationType.c().contains("ФанТайм")
                || !this.dontHitWhen.a("Враг за стеной").c().booleanValue();
        
        // Используем мультипоинты для плавного движения внутри хитбокса с выходом за пределы
        // Проверяем дистанцию атаки чтобы не опускать голову когда цель далеко
        Vec3d targetPosition;
        double distanceToTarget = mc.player.getEyePos().distanceTo(target.getEyePos());
        if (distanceToTarget <= reach * 1.5) {
            List<Vec3d> multiPoints = AuraUtil.generateMultiPoints(target, (float) reach, throughWalls);
            Vec3d bestPoint = AuraUtil.findBestPoint(multiPoints, eye);
            targetPosition = bestPoint != null ? bestPoint : AuraUtil.a(eye, target, reach, throughWalls);
        } else {
            // Если цель далеко - используем обычную точку
            targetPosition = AuraUtil.a(eye, target, reach, throughWalls);
        }
        
        // Вычисляем вектор от глаз к цели
        Vec3d diff = targetPosition.subtract(eye);
        float yawToTarget = diff == Vec3d.ZERO ? Look.b()
                : (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0d);
        float pitchToTarget = diff == Vec3d.ZERO ? Look.c()
                : (float) (-Math.toDegrees(Math.atan2(diff.y, Math.hypot(diff.x, diff.z))));
        System.arraycopy(this.pitchHistory, 0, this.pitchHistory, 1, 29);
        this.pitchHistory[0] = pitchToTarget;
        if (this.target != null && this.attackCooldown >= 2 && ((ServerUtil.a.a$(this.target) > 6.0f
                || this.timers[2] > 43.0f) && this.timers[2] >= 33.0f
                && ((this.attackCooldown == 4 || Math.random() > 0.5d)
                        && (!this.randomDirection
                                || !AuraUtil.a(mc.player.getYaw(), mc.player.getPitch(), 3.0d, this.target, false))))) {
            ((platform.inject.invokers.MinecraftClientInvoker) mc).invokeDoAttack();
            if (Math.random() > 0.5d) {
                this.randomDirection = !this.randomDirection;
            }
            this.timers[2] = (int) MathUtil.a(-10.0f, 10.0f);
        }
        boolean skip = (this.dontHitWhen.a("Используется предмет").c().booleanValue() && mc.player.isUsingItem()
                && mc.player.getItemUseTimeLeft() > 0 && this.attackCooldown >= 8)
                || !(this.dontHitWhen.a("Открыт контейнер") == null
                        || !this.dontHitWhen.a("Открыт контейнер").c().booleanValue()
                        || mc.currentScreen == null || (mc.currentScreen instanceof GUIScreen)
                        || (mc.currentScreen instanceof AssistantScreen));
        if ((this.timers[3] <= 0.0f && canAttack()) || AuraUtil.a(this.attackCooldown, this.target, skip)) {
            this.timers[3] = 1.0f;
            if (!mc.player.isTouchingWater() && this.smartSprint.c().booleanValue() && !mc.player.isOnGround()) {
                this.timers[0] = 1.0f;
            }
        }
        if (Delta.getInstance().getModuleProcessor().t().F().m() && canAttack() && AuraUtil.a(this.target, 3.0d)
                && mc.player.isGliding()) {
            Delta.getInstance().getModuleProcessor().k().startAiming(new Rotation(yawToTarget, pitchToTarget), 180.0f,
                    0, 3);
        }
        if (!this.rotationType.c().contains("ФанТайм")
                && !this.rotationType.l("Нейро")
                && (InventoryUtil.b(Items.MACE) != -1 || (Delta.getInstance().getModuleProcessor().t().H().e
                        && mc.player.fallDistance > 3.0f && MaceUtil.a(mc.player, mc.world).map(pos -> {
                            return Double.valueOf(pos.distanceTo(mc.player.getPos()));
                        }).orElse(Double.valueOf(0.0d)).doubleValue() > 2.0d
                        && AuraUtil.a(this.target, 4.0d + (mc.player.getVelocity().length() * 3.0d))))) {
            float time = mc.player.age + mc.getRenderTickCounter().getTickDelta(false);
            float smoothW = ((float) ((((Math.sin(time * 0.31f) * 0.5d)
                    + (Math.sin((time * 0.73f) + 1.1f) * 0.3000000314327426d))
                    + (Math.sin((time * 1.7f) + 2.6f) * 0.2000000098386085d)) * 8.0d)) / 8.0f;
            float finalYaw = AuraUtil.a(mc.player.getYaw(), yawToTarget, 0.8f);
            float finalPitch = AuraUtil.a(mc.player.getPitch(), pitchToTarget, 0.8f);
            Delta.getInstance().getModuleProcessor().k().startAiming(
                    new Rotation(finalYaw + smoothW, finalPitch + smoothW),
                    180.0f, 1, 2);
        }
        switch (this.rotationType.c()) {
            case "ФанТайм":
            case "ФанТайм ФОВ":
                applyFantimeSmoothing(yawToTarget, pitchToTarget, targetPosition);
                break;
            case "Легит":
                applyLegitSmoothing(yawToTarget, pitchToTarget, targetPosition);
                break;
            case "Нейро":
                applyNeuroRotation();
                break;
        }
        float[] fArr = this.timers;
        fArr[3] = fArr[3] - 1.0f;
        float[] fArr2 = this.timers;
        fArr2[5] = fArr2[5] - 1.0f;
        float[] fArr3 = this.timers;
        fArr3[8] = fArr3[8] - 1.0f;
        this.timers[1] = (float) MathHelper.wrapDegrees(
                Math.toDegrees(Math.atan2(this.target.getZ() - mc.player.getZ(), this.target.getX() - mc.player.getX()))
                        - 90.0d);
    }

    private void applyNeuroRotation() {
        if (this.target == null) return;
        boolean isAttacking = canAttack();
        float smooth = this.neuroSmooth.c().floatValue();
        Rotation neuroRot = this.neuroExec.calculateRotation(this.target, isAttacking, smooth);
        if (neuroRot != null) {
            float aimSpeed = MathHelper.lerp(smooth, isAttacking ? 95.0f : 110.0f, isAttacking ? 165.0f : 180.0f);
            Delta.getInstance().getModuleProcessor().k().startAiming(neuroRot, aimSpeed, 1, isAttacking ? 3 : 2);
        }
    }

    private void applyFantimeSmoothing(float yawToTarget, float pitchToTarget, Vec3d vec3d) {
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        
        float yawDiff = MathHelper.wrapDegrees(yawToTarget - currentYaw);
        float pitchDiff = pitchToTarget - currentPitch;
        
        // SmoothFactor - коэффициент сглаживания (0.4-0.7 = 40-70% от разницы углов)
        float smoothFactor = 0.4f + (secureRandom.nextFloat() * 0.3f);
        float finalYaw = currentYaw + yawDiff * smoothFactor;
        float finalPitch = currentPitch + pitchDiff * smoothFactor;
        
        // Более плавные волны с меньшей частотой
        long time = System.currentTimeMillis();
        float wave1 = (float) Math.sin(time / 500.0 + this.hitCounter * 0.1);
        float wave2 = (float) Math.cos(time / 600.0 + this.hitCounter * 0.05);
        finalYaw += wave1 * 1.0f + (secureRandom.nextFloat() - 0.5f) * 1.5f;
        finalPitch += wave2 * 0.8f + (secureRandom.nextFloat() - 0.5f) * 1.0f;
        
        // Если готов к атаке - наводим точно на хитбокс
        if (canAttack() && this.target != null) {
            Vec3d eye = mc.player.getEyePos();
            Vec3d targetPos = this.target.getEyePos();
            Vec3d diff = targetPos.subtract(eye);
            finalYaw = currentYaw + MathHelper.wrapDegrees(
                (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f - currentYaw) * 0.8f;
            finalPitch = currentPitch + (float) (-Math.toDegrees(Math.atan2(diff.y, Math.hypot(diff.x, diff.z))) - currentPitch) * 0.8f;
        }
        
        // Клипим pitch
        finalPitch = MathHelper.clamp(finalPitch, -90.0f, 90.0f);
        
        // Скорость 60 yaw, 23 pitch
        Delta.getInstance().getModuleProcessor().k().startAiming(new Rotation(finalYaw, finalPitch), 180.0f, 60, 23);
    }

    private void applyLegitSmoothing(float yawToTarget, float pitchToTarget, Vec3d vec3d) {
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();
        
        float yawDiff = MathHelper.wrapDegrees(yawToTarget - currentYaw);
        float pitchDiff = pitchToTarget - currentPitch;
        
        // Более быстрая интерполяция
        float smoothFactor = 0.5f + (ThreadLocalRandom.current().nextFloat(0.0f, 1.0f) * 0.3f);
        float finalYaw = currentYaw + yawDiff * smoothFactor;
        float finalPitch = currentPitch + pitchDiff * smoothFactor;
        
        // Добавляем рандомные смещения
        long time = System.currentTimeMillis();
        float wave = (float) Math.sin(time / 180.0) * 1.0f;
        finalYaw += wave + (ThreadLocalRandom.current().nextFloat(-1.0f, 1.0f));
        finalPitch += (ThreadLocalRandom.current().nextFloat(-0.5f, 0.5f));
        
        // Клипим pitch
        finalPitch = MathHelper.clamp(finalPitch, -90.0f, 90.0f);
        
        // Скорость 60 yaw, 23 pitch
        Delta.getInstance().getModuleProcessor().k().startAiming(new Rotation(finalYaw, finalPitch), 180.0f, 60, 23);
    }
}
