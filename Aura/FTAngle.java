package fun.rich.utils.features.aura.rotations.impl;

import fun.rich.Rich;
import fun.rich.features.impl.combat.Aura;
import fun.rich.utils.features.aura.rotations.constructor.RotateConstructor;
import fun.rich.utils.features.aura.striking.StrikeManager;
import fun.rich.utils.features.aura.utils.MathAngle;
import fun.rich.utils.features.aura.warp.Turns;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public class FTAngle extends RotateConstructor {

    private long smoothbackShakeStartMs = -1L;

    public FTAngle() {
        super("FunTime");
    }

    @Override
    public Turns limitAngleChange(Turns currentTurns, Turns targetTurns, Vec3d vec3d, Entity entity) {
        Aura aura = Aura.getInstance();
        StrikeManager attackHandler = Rich.getInstance().getAttackPerpetrator().getAttackHandler();

        if (aura == null || mc.player == null) {
            return currentTurns;
        }

        if (aura.isState() && aura.getTarget() != null && entity != null
                && attackHandler.canAttack(aura.getConfig(), 1)) {

            this.smoothbackShakeStartMs = -1L;

            Turns deltaTurns = MathAngle.calculateDelta(currentTurns, targetTurns);
            float yawDelta = deltaTurns.getYaw();
            float pitchDelta = deltaTurns.getPitch();
            float totalDelta = (float) Math.hypot(yawDelta, pitchDelta);

            float yawLimit   = Math.abs(yawDelta   / totalDelta) * 130.0F;
            float pitchLimit = Math.abs(pitchDelta / totalDelta) * 130.0F;

            return new Turns(
                    MathHelper.lerp(0.85F, currentTurns.getYaw(),
                            currentTurns.getYaw() + MathHelper.clamp(yawDelta, -yawLimit, yawLimit)),
                    MathHelper.lerp(0.85F, currentTurns.getPitch(),
                            currentTurns.getPitch() + MathHelper.clamp(pitchDelta, -pitchLimit, pitchLimit))
            );
        }

        Turns playerTurns = new Turns(mc.player.getYaw(), mc.player.getPitch());
        Turns returnDelta = MathAngle.calculateDelta(currentTurns, playerTurns);

        float retYaw   = returnDelta.getYaw();
        float retPitch = returnDelta.getPitch();
        float retTotal = (float) Math.hypot(retYaw, retPitch);

        float shakeYaw   = (float) (randomBetween(18.0F, 28.0F)
                * Math.sin((double) System.currentTimeMillis() / 60.0));
        float shakePitch = (float) (randomBetween(6.0F, 16.0F)
                * Math.cos((double) System.currentTimeMillis() / 60.0));

        if (aura.isState() && aura.getTarget() != null) {
            this.smoothbackShakeStartMs = -1L;
        } else {
            if (this.smoothbackShakeStartMs < 0L) {
                this.smoothbackShakeStartMs = System.currentTimeMillis();
            }

            float fadeRatio = 1.0F - MathHelper.clamp(
                    (float)(System.currentTimeMillis() - this.smoothbackShakeStartMs) / 1000.0F,
                    0.0F, 1.0F
            );
            shakeYaw   *= fadeRatio;
            shakePitch *= fadeRatio;
        }

        float limitMultiplier = !attackHandler.getAttackTimer().finished(535.0) ? 0.0F : 45.0F;
        float yawLimit   = Math.abs(retYaw   / retTotal) * limitMultiplier;
        float pitchLimit = Math.abs(retPitch / retTotal) * limitMultiplier;

        if (attackHandler.getCount() % 86 == 0 && attackHandler.getCount() > 0
                && !attackHandler.getAttackTimer().finished(250.0)) {

            shakePitch = -90.0F;

            if (attackHandler.getAttackTimer().finished(240.0)) {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }

        return new Turns(
                MathHelper.lerp(0.85F, currentTurns.getYaw(),
                        currentTurns.getYaw() + MathHelper.clamp(retYaw, -yawLimit, yawLimit) + shakeYaw),
                MathHelper.lerp(0.85F, currentTurns.getPitch(),
                        currentTurns.getPitch() + MathHelper.clamp(retPitch, -pitchLimit, pitchLimit) + shakePitch)
        );
    }

    @Override
    public Vec3d randomValue() {
        return Vec3d.ZERO;
    }

    private float randomBetween(float min, float max) {
        if (min == max) return min;
        if (min > max) {
            float tmp = min;
            min = max;
            max = tmp;
        }
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }
}