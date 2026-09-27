package fun.rich.utils.features.aura.rotations.impl;

import fun.rich.features.impl.combat.Aura;
import fun.rich.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.rich.utils.features.aura.utils.MathAngle;
import fun.rich.utils.features.aura.warp.Turns;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.security.SecureRandom;
import java.util.Random;

public class SPAngle extends RotateConstructor {

    private static final float ROTATION_SPEED = 11.0F;
    private static final float LIMIT_ROTATION_SPEED = 100.0F;
    private static final long RELEASE_HOLD_MS = 150L;
    private static final long RELEASE_SLOWDOWN_MS = 2L;
    private static final float SHAKE_INTENSITY = 1.15F;
    private static final float SHAKE_SPEED = 3.0F;
    private static final float EPSILON = 1.0F;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static boolean releaseHoldActive = false;
    private static long releaseHoldUntil = 0L;
    private static boolean releaseSlowdownActive = false;
    private static long releaseSlowdownStart = 0L;
    private static Turns releaseFromAngle = null;
    private static Turns releaseToAngle = null;
    private static boolean hadTargetLastTick = false;
    private static boolean auraEnabledLastTick = false;

    private final Random random1 = new Random();

    public SPAngle() {
        super("SpookyTime");
    }

    @Override
    public Turns limitAngleChange(Turns currentTurns, Turns targetTurns, Vec3d vec3d, Entity entity) {
        Aura aura = Aura.getInstance();
        boolean auraEnabled = aura != null && aura.isState();
        boolean hasTarget = auraEnabled && aura.getTarget() != null && entity != null;
        long now = System.currentTimeMillis();

        if (hasTarget) {
            hadTargetLastTick = true;
            auraEnabledLastTick = true;
            releaseHoldActive = false;
            releaseHoldUntil = 0L;
            releaseSlowdownActive = false;
            releaseSlowdownStart = 0L;
            releaseFromAngle = null;
            releaseToAngle = null;

            Turns delta = MathAngle.calculateDelta(currentTurns, targetTurns);
            float yawDelta = delta.getYaw();
            float pitchDelta = delta.getPitch();
            float totalDelta = (float) Math.hypot(yawDelta, pitchDelta);

            float yawLimit = Math.min(Math.abs(yawDelta), 74.0F + randomBetween(0.0F, 1.0329834F));
            float pitchLimit = (float) Math.min(Math.abs(pitchDelta), 32.334);

            Turns result = new Turns(currentTurns.getYaw(), currentTurns.getPitch());

            if (totalDelta > EPSILON) {
                boolean pitchLimitReached = Math.abs(pitchDelta) >= pitchLimit;
                float pitchMaxStep = pitchLimitReached ? randomBetween(65.0F, 100.0F) : randomBetween(7.7F, 12.1F);
                float pitchStep = Math.min(totalDelta, pitchMaxStep);
                float pitchScale = pitchStep / totalDelta;

                if (!pitchLimitReached) {
                    pitchScale = easeTowardsTarget(pitchScale);
                }

                float newPitch = MathHelper.clamp(
                        currentTurns.getPitch() + pitchDelta * pitchScale,
                        -89.0F, 90.0F
                );
                result.setPitch(newPitch);
            }

            if (totalDelta > EPSILON) {
                boolean yawLimitReached = Math.abs(yawDelta) >= yawLimit;
                float yawMaxStep = yawLimitReached ? randomBetween(65.0F, 100.0F) : randomBetween(7.7F, 12.1F);
                float yawStep = Math.min(totalDelta, yawMaxStep);
                float yawScale = yawStep / totalDelta;

                if (!yawLimitReached) {
                    yawScale = easeTowardsTarget(yawScale);
                }

                float newYaw = currentTurns.getYaw() + yawDelta * yawScale;
                result.setYaw(newYaw);
            }

            applyShake(result);

            return result.adjustSensitivity();
        }

        boolean lostTargetThisTick = hadTargetLastTick;
        boolean auraDisabledThisTick = auraEnabledLastTick && !auraEnabled;

        if ((lostTargetThisTick || auraDisabledThisTick) && !releaseHoldActive && !releaseSlowdownActive) {
            releaseHoldActive = true;
            releaseHoldUntil = now + RELEASE_HOLD_MS;
            releaseSlowdownActive = false;
            releaseSlowdownStart = 0L;
            releaseFromAngle = new Turns(currentTurns.getYaw(), currentTurns.getPitch());
            releaseToAngle = targetTurns != null
                    ? new Turns(targetTurns.getYaw(), targetTurns.getPitch())
                    : new Turns(currentTurns.getYaw(), currentTurns.getPitch());
        }

        hadTargetLastTick = false;
        auraEnabledLastTick = auraEnabled;

        if (releaseHoldActive && now < releaseHoldUntil) {
            Turns frozenAngle = releaseFromAngle != null
                    ? releaseFromAngle
                    : new Turns(currentTurns.getYaw(), currentTurns.getPitch());

            return new Turns(frozenAngle.getYaw(), frozenAngle.getPitch()).adjustSensitivity();
        }

        if (releaseHoldActive && !releaseSlowdownActive) {
            releaseSlowdownActive = true;
            releaseSlowdownStart = now;
        }

        if (!releaseSlowdownActive) {
            return targetTurns != null ? targetTurns.adjustSensitivity() : currentTurns.adjustSensitivity();
        }

        float progress = MathHelper.clamp(
                (float)(now - releaseSlowdownStart) / RELEASE_SLOWDOWN_MS,
                0.0F, 1.0F
        );
        float eased = easeTowardsTarget(progress);

        Turns from = releaseFromAngle != null ? releaseFromAngle : currentTurns;
        Turns to = releaseToAngle != null ? releaseToAngle : (targetTurns != null ? targetTurns : currentTurns);

        Turns interpolated = new Turns(
                MathHelper.lerpAngleDegrees(eased, from.getYaw(), to.getYaw()),
                MathHelper.clamp(
                        MathHelper.lerp(eased, from.getPitch(), to.getPitch()),
                        -89.0F, 90.0F
                )
        );

        if (progress >= 1.0F) {
            releaseHoldActive = false;
            releaseHoldUntil = 0L;
            releaseSlowdownActive = false;
            releaseSlowdownStart = 0L;
            releaseFromAngle = null;
            releaseToAngle = null;
        }

        return interpolated.adjustSensitivity();
    }

    private void applyShake(Turns angle) {
        if (mc.player == null) return;

        float time = (System.currentTimeMillis() % 12000L) / 1200.0F;
        float swayPhase = time * SHAKE_SPEED * MathHelper.TAU;
        float swayYaw = (float) (MathHelper.sin(swayPhase) * SHAKE_INTENSITY * random1.nextGaussian());

        angle.setYaw(angle.getYaw() + swayYaw);
    }

    private float easeTowardsTarget(float t) {
        return t * (0.5F + 0.5F * t);
    }

    @Override
    public Vec3d randomValue() {
        return new Vec3d(0.04, 0.06, 0.04);
    }

    private float randomBetween(float min, float max) {
        return MathHelper.lerp(RANDOM.nextFloat(), min, max);
    }
}