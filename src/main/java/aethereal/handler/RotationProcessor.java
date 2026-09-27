package aethereal.handler;

import aethereal.config.BaseProcessor;
import aethereal.core.Delta;
import aethereal.core.EventTarget;
import aethereal.core.GlobalEvent;
import aethereal.event.InputEvent;
import aethereal.module.combat.AuraUtil;
import aethereal.util.Look;
import aethereal.util.MathUtil;
import aethereal.util.MoveUtil;
import aethereal.util.Rotation;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RotationProcessor extends BaseProcessor {
    private static rotationState state;
    private static float resetSpeed;
    private static int lookMode;
    private static int maxTicks;
    private static int currentTick;
    private static int minResetTicks;
    private static int priority;

    /* ===== Кубическая кривая Безье для наведения ===== */
    private static Rotation bezierStart;
    private static Rotation bezierEnd;
    private static Rotation bezierControl1;
    private static Rotation bezierControl2;
    private static int bezierTotalTicks;
    private static int bezierTick;
    private static float bezierAimSpeed;

    static {
        state = rotationState.IDLE;
    }

    private final Look currentLook = new Look();

    public static rotationState getState() {
        return state;
    }

    public static int getMinResetTicks() {
        return minResetTicks;
    }

    public void setMinResetTicks(int ticks) {
        minResetTicks = Math.max(ticks, 0);
    }

    public void reset() {
        this.currentLook.a(false);
        state = rotationState.IDLE;
        currentTick = 0;
        priority = 0;
        bezierStart = null;
        bezierEnd = null;
    }

    private static boolean isUsingUseableItem() {
        List<UseableHandler.UseableTask> tasks = Delta.getInstance().getModuleProcessor().v().getUseableHandler().a();
        if (tasks.isEmpty() || tasks.getFirst().e()) {
            return false;
        }
        Item item = tasks.getFirst().a().getItem();
        return item == Items.WIND_CHARGE || item == Items.ENDER_PEARL || item == Items.SNOWBALL
                || item == Items.SPLASH_POTION || item == Items.DRIED_KELP;
    }

    private static Rotation getDefaultWobbleRotation() {
        if (Delta.getInstance().getModuleProcessor().t().H().isThrowingWindCharge()) {
            return addSinusoidalWobble(new Rotation(Look.b(), 90.0f));
        }
        return addSinusoidalWobble(new Rotation(Look.b(), Look.c()));
    }

    public static Rotation addSinusoidalWobble(Rotation rotation) {
        float t = mc.player.age + mc.getRenderTickCounter().getTickDelta(false);
        float sw = ((float) ((((Math.sin(t * 0.8f) * 11.0d)
                + (Math.sin((((double) t) * 0.04000001502137623d) + 17.200010267039897d) * 1.5d))
                + (Math.sin((((double) t) * 0.10999997113093289d) + 5.8000000238651515d) * 3.0d))
                + (Math.sin((((double) t) * 0.07000004685868849d) + 12.300000009313816d)))) / 6.0f;
        float sh = ((float) (Math.sin(((double) t) * 0.09999998815548458d)
                + (Math.sin((((double) t) * 0.029999993539464892d) + 54.10000012300467d) * 0.5d))) / 4.0f;
        return new Rotation(rotation.c() + MathHelper.clamp(sw, -0.15f, 0.15f),
                rotation.d() + MathHelper.clamp(sh, -0.15f, 0.15f));
    }

    private static int getMaxTicksForMode(int mode) {
        switch (mode) {
            case 0:
                return 1;
            case 1:
                return 9;
            case 7:
                return 30;
            default:
                return 10;
        }
    }

    private static Rotation calculateModeRotation(int mode, int idleTicks) {
        float baseYaw = Look.b();
        float basePitch = Look.c();
        float t = mc.player.age + mc.getRenderTickCounter().getTickDelta(false);
        float sw = ((float) ((((Math.sin(t * 0.31f) * 0.5d) + (Math.sin((t * 0.73f) + 1.1f) * 0.3000000002422922d))
                + (Math.sin((t * 1.7f) + 2.6f) * 0.19999998556632664d)) * 12.0d)) / 4.0f;
        switch (mode) {
            case 1:
                float baseYaw2 = AuraUtil.a(mc.player.getYaw(), Look.b(), MathUtil.a(0.1f, 0.45f));
                float basePitch2 = AuraUtil.a(mc.player.getPitch(), Look.c(), MathUtil.a(0.1f, 0.45f));
                return new Rotation(baseYaw2 + sw, MathHelper.clamp(basePitch2 + sw, -90.0f, 90.0f));
            case 7:
                if (!Delta.getInstance().getModuleProcessor().t().aS().m()) {
                    idleTicks = 25;
                }
                if (idleTicks <= 20) {
                    return new Rotation(mc.player.getYaw() + sw,
                            MathHelper.clamp(mc.player.getPitch() + sw, -90.0f, 90.0f));
                }
                float baseYaw3 = AuraUtil.a(mc.player.getYaw(), Look.b(), MathUtil.a(0.2f, 0.35f));
                float basePitch3 = AuraUtil.a(mc.player.getPitch(), Look.c(), MathUtil.a(0.2f, 0.35f));
                return new Rotation(baseYaw3 + sw, MathHelper.clamp(basePitch3 + sw, -90.0f, 90.0f));
            default:
                return new Rotation(baseYaw + sw, basePitch + sw);
        }
    }

    public static float snapToGCD(float lastYaw, float current) {
        double sens = (mc.options.getMouseSensitivity().getValue().doubleValue() * 0.6000000498956214d)
                + 0.19999998556632664d;
        double gcd = sens * sens * sens * 8.0d;
        return (float) (((double) lastYaw) + (Math.ceil((((double) (current - lastYaw)) / gcd) / 0.15000006556510925d)
                * gcd * 0.15000006556510925d));
    }

    @Override

    public void setup() {
    }

    public Look getCurrentLook() {
        return this.currentLook;
    }

    @Override
    public void unSetup() {
    }

    @EventTarget
    private void onInputEvent(InputEvent e2) {
        if (isRotating()) {
            MoveUtil.a(e2, Look.b(), 10);
        }
    }

    @EventTarget
    private void onGlobalEvent(GlobalEvent e2) {
        currentTick++;
        // Продолжение движения по кубической кривой Безье между вызовами наведения
        if (state == rotationState.AIM && bezierStart != null && bezierEnd != null
                && currentTick <= maxTicks) {
            stepBezier(bezierAimSpeed);
        }
        if (isRotating()) {
            if (isUsingUseableItem()) {
                applyRotationStep(getDefaultWobbleRotation(), resetSpeed, false);
            } else {
                applyRotationStep(calculateModeRotation(lookMode, currentTick), resetSpeed, false);
            }
        }
        if (state == rotationState.AIM && currentTick > maxTicks) {
            state = rotationState.RESET;
        }
        if (state == rotationState.RESET && applyRotationStep(Rotation.a(), resetSpeed, true)) {
            this.currentLook.a(false);
            state = rotationState.IDLE;
            priority = 0;
        }
    }

    private boolean isRotating() {
        return currentTick >= 2 && currentTick <= maxTicks && state != rotationState.IDLE;
    }

    public void startAiming(Rotation rotation, float turnSpeed, int lookMode, int priority) {
        // Основной путь наведения: кубическая кривая Безье с рандомизацией скорости (+-30%).
        // Кривая гарантированно доводится до цели за конечное число тиков (без "вечного хвоста").
        if (mc.player != null && rotation != null && lookMode != 0 && priority >= RotationProcessor.priority) {
            Rotation current = new Rotation(mc.player);
            double delta = current.a(rotation);
            // Если цель уже "на кончике прицела" — кривая не нужна, точная доводка
            if (delta > 1.2d) {
                ThreadLocalRandom rnd = ThreadLocalRandom.current();
                float speed = (float) (turnSpeed * (0.7d + rnd.nextDouble() * 0.6d));
                if (state == rotationState.IDLE) {
                    this.currentLook.a(true);
                }
                RotationProcessor.lookMode = lookMode;
                maxTicks = getMaxTicksForMode(lookMode);
                RotationProcessor.resetSpeed = turnSpeed;
                RotationProcessor.priority = priority;
                state = rotationState.AIM;
                currentTick = 0;
                int ticks = (int) MathHelper.clamp(Math.ceil(delta / speed), 2.0d, 8.0d);
                beginBezier(current, rotation, ticks, speed);
                return;
            }
        }
        startAimingWithSpeeds(rotation, turnSpeed, turnSpeed, lookMode, priority);
    }

    public void startAimingWithSpeeds(Rotation rotation, float aimSpeed, float resetSpeed, int lookMode, int priority) {
        if (priority < RotationProcessor.priority) {
            return;
        }
        if (state != rotationState.IDLE && isUsingUseableItem()) {
            rotation = getDefaultWobbleRotation();
        }
        if (state == rotationState.IDLE) {
            this.currentLook.a(true);
        }
        RotationProcessor.resetSpeed = resetSpeed;
        RotationProcessor.lookMode = lookMode;
        maxTicks = getMaxTicksForMode(lookMode);
        RotationProcessor.priority = priority;
        state = rotationState.AIM;
        currentTick = 0;
        bezierStart = null;
        applyRotationStep(rotation, aimSpeed, true);
    }

    /* ===================== Кубическая кривая Безье ===================== */

    /**
     * Наведение по кубической кривой Безье. В отличие от экспоненциального lerp
     * (который в конце движется бесконечно медленно и никогда не доводится),
     * кривая гарантированно проходит через конечную точку за фиксированное число тиков.
     * Скорость движения рандомизирована в пределах +/- 30%.
     */
    public void startBezierAiming(Rotation target, float baseSpeed, int priority) {
        if (mc.player == null || target == null || priority < RotationProcessor.priority) {
            return;
        }
        if (state == rotationState.IDLE) {
            this.currentLook.a(true);
        }
        RotationProcessor.priority = priority;
        state = rotationState.AIM;
        currentTick = 0;
        Rotation current = new Rotation(mc.player);
        double delta = current.a(target);
        if (delta < 0.6d) {
            // Уже наведены — просто точно доводим без дребезга
            bezierStart = null;
            applyRotationStep(target, baseSpeed, true);
            return;
        }
        // Рандомизация скорости +-30%
        float speed = (float) (baseSpeed * (0.7d + ThreadLocalRandom.current().nextDouble() * 0.6d));
        bezierAimSpeed = speed;
        int ticks = (int) MathHelper.clamp(Math.ceil(delta / speed), 2.0d, 8.0d);
        beginBezier(current, target, ticks, speed);
    }

    private void beginBezier(Rotation start, Rotation end, int ticks, float speed) {
        bezierStart = start;
        bezierEnd = end;
        bezierTotalTicks = ticks;
        bezierTick = 0;
        double yawDiff = MathHelper.wrapDegrees(end.c() - start.c());
        double pitchDiff = end.d() - start.d();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        // Контрольные точки дают естественную дугообразную траекторию (как у мыши человека)
        double offY1 = yawDiff * rnd.nextDouble(0.02d, 0.10d) * (rnd.nextBoolean() ? 1 : -1);
        double offP1 = pitchDiff * rnd.nextDouble(0.02d, 0.10d) * (rnd.nextBoolean() ? 1 : -1);
        double offY2 = yawDiff * rnd.nextDouble(0.01d, 0.06d) * (rnd.nextBoolean() ? 1 : -1);
        double offP2 = pitchDiff * rnd.nextDouble(0.01d, 0.06d) * (rnd.nextBoolean() ? 1 : -1);
        bezierControl1 = new Rotation((float) (start.c() + yawDiff * 0.35d + offY1),
                (float) MathHelper.clamp(start.d() + pitchDiff * 0.35d + offP1, -90.0d, 90.0d));
        bezierControl2 = new Rotation((float) (start.c() + yawDiff * 0.72d + offY2),
                (float) MathHelper.clamp(start.d() + pitchDiff * 0.72d + offP2, -90.0d, 90.0d));
        // Первый шаг сразу задаем скорость, чтобы движение начиналось быстро
        stepBezier(speed);
    }

    private void stepBezier(float speed) {
        if (bezierStart == null || bezierEnd == null) {
            return;
        }
        bezierTick++;
        float t = MathHelper.clamp((float) bezierTick / (float) bezierTotalTicks, 0.0f, 1.0f);
        // easeInOutCubic — быстрый старт, плавная (но НЕ бесконечная) доводка в конце
        float e = t < 0.5f ? 4.0f * t * t * t : 1.0f - Math.pow(-2.0f * t + 2.0f, 3.0f) / 2.0f;
        Rotation point = cubicBezier(bezierStart, bezierControl1, bezierControl2, bezierEnd, e);
        applyRotationStep(point, speed, false);
        if (bezierTick >= bezierTotalTicks) {
            // Гарантированная финальная доводка точно в цель
            applyRotationStep(bezierEnd, speed, true);
            bezierStart = null;
            bezierEnd = null;
        }
    }

    private static Rotation cubicBezier(Rotation p0, Rotation p1, Rotation p2, Rotation p3, float t) {
        float u = 1.0f - t;
        float uu = u * u;
        float tt = t * t;
        float w0 = uu * u;
        float w1 = 3.0f * uu * t;
        float w2 = 3.0f * u * tt;
        float w3 = tt * t;
        float yaw = w0 * p0.c() + w1 * p1.c() + w2 * p2.c() + w3 * p3.c();
        float pitch = w0 * p0.d() + w1 * p1.d() + w2 * p2.d() + w3 * p3.d();
        return new Rotation(MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0f, 90.0f));
    }

    private boolean applyRotationStep(Rotation rotation, float turnSpeed, boolean bait) {
        Rotation currentRotation = new Rotation(mc.player);
        float yawDelta = MathHelper.wrapDegrees(rotation.c() - currentRotation.c());
        float pitchDelta = rotation.d() - currentRotation.d();
        float totalDelta = Math.abs(yawDelta) + Math.abs(pitchDelta);
        float yawSpeed = totalDelta == 0.0f ? 0.0f : Math.abs(yawDelta / totalDelta) * turnSpeed;
        float pitchSpeed = totalDelta == 0.0f ? 0.0f : Math.abs(pitchDelta / totalDelta) * turnSpeed;
        float newYaw = mc.player.getYaw() + MathHelper.clamp(yawDelta, -yawSpeed, yawSpeed);
        float newPitch = mc.player.getPitch() + MathHelper.clamp(pitchDelta, -pitchSpeed, pitchSpeed);
        float newYaw2 = snapToGCD(mc.player.getYaw(), newYaw);
        float newPitch2 = MathHelper.clamp(snapToGCD(mc.player.getPitch(), newPitch), -90.0f, 90.0f);
        if (minResetTicks > 0) {
            newPitch2 = mc.player.getPitch();
            newYaw2 = mc.player.getYaw();
            minResetTicks--;
        }
        mc.player.setYaw(newYaw2);
        mc.player.setPitch(newPitch2);
        Rotation finalRotation = new Rotation(mc.player);
        if (bait) {
            currentTick = 0;
        }
        return finalRotation.a(rotation) < ((double) turnSpeed);
    }

    public enum rotationState {
        AIM,
        RESET,
        IDLE
    }
}
