package aethereal.module.combat.neuro;

import aethereal.util.Rotation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public final class NeuroAuraExec {

    static final float NEURO_ASSIST_WHEN_MANUAL = 0.35f;
    static final float NEURO_BASE_STRENGTH = 0.88f;
    static final float NEURO_OVERSHOOT_PROB = 0.21f;
    static final float NEURO_OVERSHOOT_MIN = 0.94f;
    static final float NEURO_OVERSHOOT_MAX = 1.15f;

    static final float NEURO_NOISE_YAW_MIN = 0.06f;
    static final float NEURO_NOISE_YAW_MAX = 0.38f;
    static final float NEURO_NOISE_PITCH_MIN = 0.04f;
    static final float NEURO_NOISE_PITCH_MAX = 0.22f;

    static final float NEURO_NOISE_FREQ_MIN = 0.55f;
    static final float NEURO_NOISE_FREQ_MAX = 1.65f;

    static final long NEURO_LOST_SHAKE_MS = 480L;
    static final float NEURO_LOST_SHAKE_YAW_MIN = 4.0f;
    static final float NEURO_LOST_SHAKE_YAW_MAX = 11.0f;

    static final float FALLBACK_GATE_CONF = 0.28f;
    static final float FALLBACK_POINT_GATE = 0.32f;
    static final float FALLBACK_MIN_CONFIDENCE = 0.22f;

    static final float HUMAN_JITTER_SCALE = 7.5f;
    static final float HUMAN_SPEED_SCALE = 18.0f;
    static final float HUMAN_AMP_SCALE = 26.0f;

    static final float GCD_MIN = 0.006f;
    static final float GCD_MAX = 0.45f;

    static final float NEURO_CONF_EMA_ALPHA = 0.18f;
    static final float NEURO_POINT_CONF_EMA_ALPHA = 0.16f;
    static final long NEURO_CONF_DROP_HOLD_MS = 140L;
    static final float NEURO_OUTPUT_HOLD_BLEND = 0.68f;
    static final float NEURO_STYLE_EMA_ALPHA = 0.20f;

    static final float NEURO_DELTA_SOFT_YAW = 22.0f;
    static final float NEURO_DELTA_SOFT_PITCH = 16.0f;

    private final NeuroRotationModel model = new NeuroRotationModel();
    private final PerlinNoise perlin = new PerlinNoise(1337);
    private NeuroAuraLearn learn;
    private boolean modelDirty;

    private float confEma;
    private float pointConfEma;
    private long confDropHoldUntil;
    private float lastStableOutYaw;
    private float lastStableOutPitch;
    private long lastStableOutMs;

    private float styleYawSpeedEma;
    private float stylePitchSpeedEma;
    private float styleYawJitterEma;
    private float stylePitchJitterEma;
    private float styleAmpEma;
    private float styleGcdYawEma;
    private float styleGcdPitchEma;

    private float noisePhaseYaw;
    private float noisePhasePitch;

    private boolean lostShakeActive;
    private long lostShakeStartTime;
    private long lostShakeEndTime;
    private long lostShakeLastTime;
    private float lostShakeYaw;
    private float lostShakePitch;
    private int lostShakePhase;
    private boolean lostShakeArmed;

    private int aimSmoothTargetId = -1;
    private long aimSmoothLastTime;
    private float aimSmoothedDy;
    private float aimSmoothedDp;

    private long aimEngageStart;
    private long aimEngageUntil;
    private int aimEngageTargetId = -1;
    private boolean aimEngage;

    private NeuroRotationModel.Prediction predCache;
    private long predCacheTime;
    private int predTargetId = -1;
    private boolean predCacheAttack;
    private long lastSaveMs;
    private Vec3d smoothedAimPoint;
    private int smoothedAimTargetId = -1;
    private long smoothedAimLastTime;

    private float prevCamYaw;
    private float prevCamPitch;
    private float emaCamVelYaw;
    private float emaCamVelPitch;
    private int targetStableTicks;
    private int lastTargetId = -1;

    public NeuroAuraExec() {
        try {
            this.model.load();
        } catch (Exception ignored) {
        }
    }

    public void attachLearn(NeuroAuraLearn learn) {
        this.learn = learn;
    }

    public NeuroRotationModel getModel() {
        return this.model;
    }

    public void markModelDirty() {
        this.modelDirty = true;
    }

    public void notifyModelUpdated() {
        this.modelDirty = true;
        this.predCache = null;
        this.predCacheTime = 0L;
        this.predTargetId = -1;
    }

    public void reloadModel() {
        try {
            this.model.load();
        } catch (Exception ignored) {
        }
        this.modelDirty = false;
        this.predCache = null;
        this.predCacheTime = 0L;
        this.predTargetId = -1;
        this.predCacheAttack = false;
        this.confEma = 0.0f;
        this.pointConfEma = 0.0f;
        this.smoothedAimPoint = null;
        this.smoothedAimTargetId = -1;
        this.smoothedAimLastTime = 0L;
    }

    public void onActivate() {
        this.lostShakeActive = false;
        this.aimEngage = false;
        this.predCache = null;
        this.predCacheTime = 0L;
        this.predTargetId = -1;
        reloadModel();
    }

    public void onTargetSelected(LivingEntity t) {
        if (t != null) {
            long now = System.currentTimeMillis();
            startAimEngage(t.getId(), now);
            this.predCache = null;
            this.predCacheTime = 0L;
            this.predTargetId = -1;
            this.predCacheAttack = false;

            this.noisePhaseYaw = rnd(0.0f, 999.0f);
            this.noisePhasePitch = rnd(0.0f, 999.0f);

            this.confDropHoldUntil = 0L;
            this.lastStableOutMs = 0L;
            this.lostShakeArmed = true;
            this.smoothedAimPoint = null;
            this.smoothedAimTargetId = -1;
            this.smoothedAimLastTime = 0L;
        }
    }

    public Vec3d adjustPointForExec(LivingEntity target, Vec3d fallbackPoint, boolean plannedAttack, float smoothSetting) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) {
            return fallbackPoint;
        }

        if (this.modelDirty) {
            reloadModel();
        }

        long now = System.currentTimeMillis();
        NeuroRotationModel.Prediction pr = getPrediction(target, plannedAttack, now);
        NeuroRotationModel.FallbackPrediction fb = this.model.fallbackPredict(target, plannedAttack);

        Vec3d desired = chooseExecPoint(target, fallbackPoint, pr, fb);
        if (desired == null) {
            return fallbackPoint;
        }

        Box hb = target.getBoundingBox();
        desired = clampPointToBox(desired, hb);

        int tid = target.getId();
        if (this.smoothedAimPoint == null || this.smoothedAimTargetId != tid) {
            this.smoothedAimPoint = desired;
            this.smoothedAimTargetId = tid;
            this.smoothedAimLastTime = now;
            return desired;
        }

        long dtMs = now - this.smoothedAimLastTime;
        if (dtMs < 1L) dtMs = 1L;
        if (dtMs > 90L) dtMs = 90L;
        this.smoothedAimLastTime = now;

        float s = MathHelper.clamp(smoothSetting, 0.20f, 0.95f);
        float tau = MathHelper.lerp(s, 60.0f, 145.0f);
        float a = MathHelper.clamp((float) dtMs / tau, 0.0f, 1.0f);
        a = a * a * (3.0f - 2.0f * a);

        this.smoothedAimPoint = new Vec3d(
                lerpD(this.smoothedAimPoint.x, desired.x, a),
                lerpD(this.smoothedAimPoint.y, desired.y, a),
                lerpD(this.smoothedAimPoint.z, desired.z, a)
        );
        this.smoothedAimPoint = clampPointToBox(this.smoothedAimPoint, hb);
        return this.smoothedAimPoint;
    }

    public Rotation calculateRotation(LivingEntity target, boolean plannedAttack, float smoothSetting) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) {
            return null;
        }

        if (this.modelDirty) {
            reloadModel();
        }

        int tid = target.getId();
        long now = System.currentTimeMillis();
        NeuroRotationModel.Prediction pr = getPrediction(target, plannedAttack, now);

        NeuroRotationModel.FallbackPrediction fb = this.model.fallbackPredict(target, plannedAttack);
        updatePlaybackStats(pr);

        Vec3d point = adjustPointForExec(target, null, plannedAttack, smoothSetting);
        if (point == null) {
            Box box = target.getBoundingBox();
            point = new Vec3d((box.minX + box.maxX) * 0.5, box.minY + target.getHeight() * 0.65, (box.minZ + box.maxZ) * 0.5);
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d diff = point.subtract(eye);
        double dist = Math.sqrt((diff.x * diff.x) + (diff.z * diff.z));
        float baseYaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0d);
        float basePitch = (float) (-Math.toDegrees(Math.atan2(diff.y, dist)));

        float s = MathHelper.clamp(smoothSetting, 0.20f, 0.95f);

        float confRaw = pr != null ? MathHelper.clamp(pr.confidence, 0.0f, 1.0f) : 0.0f;
        float pcRaw = pr != null ? MathHelper.clamp(pr.pointConfidence / 100.0f, 0.0f, 1.0f) : 0.0f;

        float conf = MathHelper.clamp(confRaw * 0.58f + this.confEma * 0.42f, 0.0f, 1.0f);
        float pc = MathHelper.clamp(pcRaw * 0.62f + this.pointConfEma * 0.38f, 0.0f, 1.0f);

        float fbConf = fb != null ? fb.confidence : 0.0f;
        boolean preferFallback = pr == null
                || conf < FALLBACK_GATE_CONF
                || pc < FALLBACK_POINT_GATE
                || (fbConf > conf + 0.10f && fbConf >= FALLBACK_MIN_CONFIDENCE);

        float dyNeed = pr != null ? MathHelper.clamp(pr.yawDeltaDeg, -NeuroRotationModel.AIM_MAX_YAW_DELTA, NeuroRotationModel.AIM_MAX_YAW_DELTA) : 0.0f;
        float dpNeed = pr != null ? MathHelper.clamp(pr.pitchDeltaDeg, -NeuroRotationModel.AIM_MAX_PITCH_DELTA, NeuroRotationModel.AIM_MAX_PITCH_DELTA) : 0.0f;

        if (fb != null) {
            float fdy = MathHelper.clamp(fb.yawDeltaDeg, -NeuroRotationModel.AIM_MAX_YAW_DELTA, NeuroRotationModel.AIM_MAX_YAW_DELTA);
            float fdp = MathHelper.clamp(fb.pitchDeltaDeg, -NeuroRotationModel.AIM_MAX_PITCH_DELTA, NeuroRotationModel.AIM_MAX_PITCH_DELTA);

            if (preferFallback) {
                dyNeed = fdy;
                dpNeed = fdp;
                conf = fbConf;
                pc = Math.max(pc, MathHelper.clamp(fbConf, 0.0f, 1.0f));
            } else {
                float w = MathHelper.clamp((FALLBACK_GATE_CONF - conf) / FALLBACK_GATE_CONF, 0.0f, 1.0f);
                w *= MathHelper.clamp(fbConf, 0.0f, 1.0f);
                w *= MathHelper.lerp(s, 1.0f, 0.82f);
                dyNeed = dyNeed + (fdy - dyNeed) * w;
                dpNeed = dpNeed + (fdp - dpNeed) * w;
            }
        }

        dyNeed = softLimit(dyNeed, NEURO_DELTA_SOFT_YAW, NeuroRotationModel.AIM_MAX_YAW_DELTA);
        dpNeed = softLimit(dpNeed, NEURO_DELTA_SOFT_PITCH, NeuroRotationModel.AIM_MAX_PITCH_DELTA);

        float confGate = MathHelper.clamp(0.25f + 0.75f * MathHelper.clamp(conf, 0.0f, 1.0f), 0.25f, 1.0f);
        float strength = NEURO_BASE_STRENGTH * confGate;
        strength = MathHelper.clamp(strength, 0.05f, 1.0f);

        if (!this.aimEngage || this.aimEngageTargetId != tid) {
            startAimEngage(tid, now);
        }
        if (this.aimEngage && now <= this.aimEngageUntil && this.aimEngageUntil > this.aimEngageStart) {
            float raw = (float) (now - this.aimEngageStart) / (float) (this.aimEngageUntil - this.aimEngageStart);
            strength *= easeOutCubic(raw);
        }

        float overshoot = 1.0f;
        float osProb = NEURO_OVERSHOOT_PROB;
        if (plannedAttack) osProb *= 1.12f;
        osProb *= MathHelper.lerp(s, 1.0f, 0.92f);
        if (ThreadLocalRandom.current().nextFloat() < osProb) {
            overshoot = rnd(NEURO_OVERSHOOT_MIN, NEURO_OVERSHOOT_MAX);
        }

        if (this.aimSmoothTargetId != tid) {
            this.aimSmoothTargetId = tid;
            this.aimSmoothedDy = 0.0f;
            this.aimSmoothedDp = 0.0f;
            this.aimSmoothLastTime = 0L;
            startAimEngage(tid, now);
        }

        long dtMs = now - this.aimSmoothLastTime;
        if (this.aimSmoothLastTime == 0L) dtMs = 50L;
        if (dtMs < 1) dtMs = 1;
        if (dtMs > 120) dtMs = 120;
        this.aimSmoothLastTime = now;

        float yawPer50 = MathHelper.lerp(s, plannedAttack ? 16.5f : 18.0f, plannedAttack ? 4.0f : 4.2f);
        float pitchPer50 = MathHelper.lerp(s, plannedAttack ? 12.8f : 14.0f, plannedAttack ? 3.0f : 3.4f);

        float dyMax = yawPer50 * ((float) dtMs / 50.0f);
        float dpMax = pitchPer50 * ((float) dtMs / 50.0f);

        float dyErr = MathHelper.wrapDegrees(dyNeed - this.aimSmoothedDy);
        float dpErr = MathHelper.wrapDegrees(dpNeed - this.aimSmoothedDp);

        this.aimSmoothedDy += MathHelper.clamp(dyErr, -dyMax, dyMax);
        this.aimSmoothedDp += MathHelper.clamp(dpErr, -dpMax, dpMax);

        this.aimSmoothedDy = MathHelper.clamp(this.aimSmoothedDy, -NeuroRotationModel.AIM_MAX_YAW_DELTA, NeuroRotationModel.AIM_MAX_YAW_DELTA);
        this.aimSmoothedDp = MathHelper.clamp(this.aimSmoothedDp, -NeuroRotationModel.AIM_MAX_PITCH_DELTA, NeuroRotationModel.AIM_MAX_PITCH_DELTA);

        float nYaw = baseYaw + this.aimSmoothedDy * strength * overshoot;
        float nPitch = basePitch + this.aimSmoothedDp * strength * overshoot;

        float human = 1.0f - MathHelper.clamp(conf, 0.0f, 1.0f);
        human = human * human;
        if (plannedAttack) human *= 0.55f;
        human *= MathHelper.lerp(s, 1.05f, 0.85f);

        float lj = 0.0f;
        float ls = 0.0f;
        float la = 0.0f;
        float gcdYaw = 0.0f;
        float gcdPitch = 0.0f;

        if (pr != null) {
            float pyj = this.styleYawJitterEma != 0.0f ? this.styleYawJitterEma : Math.abs(pr.yawJitter);
            float ppj = this.stylePitchJitterEma != 0.0f ? this.stylePitchJitterEma : Math.abs(pr.pitchJitter);
            float pys = this.styleYawSpeedEma != 0.0f ? this.styleYawSpeedEma : Math.abs(pr.yawSpeed);
            float pps = this.stylePitchSpeedEma != 0.0f ? this.stylePitchSpeedEma : Math.abs(pr.pitchSpeed);
            float pamp = this.styleAmpEma != 0.0f ? this.styleAmpEma : Math.abs(pr.amp);

            lj = MathHelper.clamp((pyj + ppj) / HUMAN_JITTER_SCALE, 0.0f, 1.0f);
            ls = MathHelper.clamp((pys + pps) / HUMAN_SPEED_SCALE, 0.0f, 1.0f);
            la = MathHelper.clamp(pamp / HUMAN_AMP_SCALE, 0.0f, 1.0f);

            gcdYaw = this.styleGcdYawEma > 0.0f ? this.styleGcdYawEma : pr.gcdYaw;
            gcdPitch = this.styleGcdPitchEma > 0.0f ? this.styleGcdPitchEma : pr.gcdPitch;
        }

        float learnHumanBoost = 0.20f + 0.80f * (0.55f * lj + 0.30f * ls + 0.15f * la);
        learnHumanBoost = MathHelper.clamp(learnHumanBoost, 0.20f, 1.15f);

        float aYaw = MathHelper.lerp(human, NEURO_NOISE_YAW_MIN, NEURO_NOISE_YAW_MAX) * learnHumanBoost;
        float aPitch = MathHelper.lerp(human, NEURO_NOISE_PITCH_MIN, NEURO_NOISE_PITCH_MAX) * learnHumanBoost;

        float fYaw = MathHelper.lerp(human, NEURO_NOISE_FREQ_MIN, NEURO_NOISE_FREQ_MAX) * (0.85f + 0.55f * ls);
        float fPitch = MathHelper.lerp(human, NEURO_NOISE_FREQ_MIN, NEURO_NOISE_FREQ_MAX) * (0.85f + 0.55f * ls);

        NeuroAuraLearn.HumanizerProfile hp = null;
        if (this.learn != null) {
            try {
                hp = this.learn.suggestHumanizer(target, plannedAttack);
            } catch (Exception ignored) {
            }
        }

        if (hp != null) {
            aYaw = hp.noiseYaw() * learnHumanBoost;
            aPitch = hp.noisePitch() * learnHumanBoost;
            fYaw = hp.freqYaw();
            fPitch = hp.freqPitch();
            confGate *= hp.gate();
            if (hp.gcdHintYaw() > 0.0f) gcdYaw = hp.gcdHintYaw();
            if (hp.gcdHintPitch() > 0.0f) gcdPitch = hp.gcdHintPitch();
        }

        float tt = (float) ((now % 100000L) / 1000.0);
        float ny = this.perlin.fbm((tt * fYaw + this.noisePhaseYaw), (float) (tid * 0.013 + 0.11), 4, 2.0f, 0.55f);
        float np = this.perlin.fbm((tt * fPitch + this.noisePhasePitch), (float) (tid * 0.017 + 37.7), 4, 2.0f, 0.55f);

        float sway = this.perlin.noise((tt * 0.22f + this.noisePhaseYaw * 0.07f), (float) (tid * 0.009 + 9.3));
        float spn = this.perlin.noise((tt * 0.19f + this.noisePhasePitch * 0.07f), (float) (tid * 0.011 + 3.7));

        float nyOut = (ny * 0.72f + sway * 0.28f) * aYaw;
        float npOut = (np * 0.72f + spn * 0.28f) * aPitch;

        float kStr = strength * (plannedAttack ? 0.62f : 0.72f);
        nYaw += nyOut * kStr;
        nPitch += npOut * kStr;

        float curYaw = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        float outYaw = nYaw;
        float outPitch = nPitch;

        float gYaw = MathHelper.clamp(gcdYaw == 0.0f ? 0.0f : Math.abs(gcdYaw), GCD_MIN, GCD_MAX);
        float gPitch = MathHelper.clamp(gcdPitch == 0.0f ? 0.0f : Math.abs(gcdPitch), GCD_MIN, GCD_MAX);

        if (gYaw > 0.0f) {
            float dy = MathHelper.wrapDegrees(outYaw - curYaw);
            outYaw = curYaw + quantize(dy, gYaw);
        }
        if (gPitch > 0.0f) {
            float dp = MathHelper.wrapDegrees(outPitch - curPitch);
            outPitch = curPitch + quantize(dp, gPitch);
        }

        if (this.confDropHoldUntil > now && this.lastStableOutMs != 0L) {
            outYaw = blendAngle(outYaw, this.lastStableOutYaw, NEURO_OUTPUT_HOLD_BLEND);
            outPitch = MathHelper.lerp(NEURO_OUTPUT_HOLD_BLEND, outPitch, this.lastStableOutPitch);
        }

        outPitch = MathHelper.clamp(outPitch, -90.0f, 90.0f);

        if (conf >= 0.34f) {
            this.lastStableOutYaw = outYaw;
            this.lastStableOutPitch = outPitch;
            this.lastStableOutMs = now;
        }

        return new Rotation(outYaw, outPitch);
    }

    public Rotation handleNoTargetShake(LivingEntity lastTarget, boolean shakeEnabled) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return null;

        if (shakeEnabled && lastTarget != null && !this.lostShakeActive && this.lostShakeArmed) {
            this.lostShakeArmed = false;
            this.lostShakeActive = true;
            this.lostShakeStartTime = System.currentTimeMillis();
            this.lostShakeEndTime = this.lostShakeStartTime + NEURO_LOST_SHAKE_MS;
            this.lostShakeLastTime = this.lostShakeStartTime;
            this.lostShakeYaw = mc.player.getYaw();
            this.lostShakePitch = mc.player.getPitch();
            this.lostShakePhase = 0;
        }

        if (this.lostShakeActive) {
            long now = System.currentTimeMillis();
            if (now >= this.lostShakeEndTime || this.lostShakeEndTime <= this.lostShakeStartTime) {
                this.lostShakeActive = false;
                return null;
            }

            long dtMs = now - this.lostShakeLastTime;
            if (dtMs < 1) dtMs = 1;
            if (dtMs > 120) dtMs = 120;
            this.lostShakeLastTime = now;

            float raw = (float) (now - this.lostShakeStartTime) / (float) (this.lostShakeEndTime - this.lostShakeStartTime);
            float k = 1.0f - raw;

            float amp = rnd(NEURO_LOST_SHAKE_YAW_MIN, NEURO_LOST_SHAKE_YAW_MAX) * k;
            float sign = (this.lostShakePhase++ % 2 == 0) ? 1.0f : -1.0f;

            float jitter = rnd(-0.9f, 0.9f) * 0.65f;
            float dy = (amp + jitter) * sign;
            float dp = rnd(-0.55f, 0.35f) * k;

            this.lostShakeYaw += dy;
            this.lostShakePitch += dp;

            return new Rotation(this.lostShakeYaw, MathHelper.clamp(this.lostShakePitch, -90.0f, 90.0f));
        }

        return null;
    }

    public void recordAttack(LivingEntity target) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) return;

        Vec3d diff = target.getEyePos().subtract(mc.player.getEyePos());
        double dist = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float idealYaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0d);
        float idealPitch = (float) (-Math.toDegrees(Math.atan2(diff.y, dist)));

        float dy = MathHelper.wrapDegrees(mc.player.getYaw() - idealYaw);
        float dp = MathHelper.wrapDegrees(mc.player.getPitch() - idealPitch);

        Box hb = target.getBoundingBox();
        float px = 0.5f;
        float py = 0.65f;
        float pz = 0.5f;

        this.model.learn(target, true, dy, dp, px, py, pz, 120.0f, 80.0f, 1.2f, 0.8f, 0.05f, 0.05f, 10.0f, 0.15f);
    }

    public void onTick() {
        long now = System.currentTimeMillis();
        if ((this.modelDirty || now - this.lastSaveMs > 10000L) && (now - this.lastSaveMs > 4500L)) {
            this.lastSaveMs = now;
            this.modelDirty = false;
            try {
                this.model.save();
            } catch (Exception ignored) {
            }
        }
    }

    public void onDeactivate() {
        this.lostShakeActive = false;
        this.aimEngage = false;
        this.smoothedAimPoint = null;
        this.smoothedAimTargetId = -1;
        this.smoothedAimLastTime = 0L;
        try {
            this.model.save();
        } catch (Exception ignored) {
        }
    }

    private void startAimEngage(int tid, long now) {
        this.aimEngage = true;
        this.aimEngageStart = now;
        this.aimEngageUntil = now + 120L;
        this.aimEngageTargetId = tid;
    }

    private void updatePlaybackStats(NeuroRotationModel.Prediction pr) {
        float c = (pr != null) ? MathHelper.clamp(pr.confidence, 0.0f, 1.0f) : 0.0f;
        float pc = (pr != null) ? MathHelper.clamp(pr.pointConfidence / 100.0f, 0.0f, 1.0f) : 0.0f;

        this.confEma = ema(this.confEma, c, NEURO_CONF_EMA_ALPHA);
        this.pointConfEma = ema(this.pointConfEma, pc, NEURO_POINT_CONF_EMA_ALPHA);

        if (pr == null) return;

        this.styleYawSpeedEma = ema(this.styleYawSpeedEma, Math.abs(pr.yawSpeed), NEURO_STYLE_EMA_ALPHA);
        this.stylePitchSpeedEma = ema(this.stylePitchSpeedEma, Math.abs(pr.pitchSpeed), NEURO_STYLE_EMA_ALPHA);
        this.styleYawJitterEma = ema(this.styleYawJitterEma, Math.abs(pr.yawJitter), NEURO_STYLE_EMA_ALPHA);
        this.stylePitchJitterEma = ema(this.stylePitchJitterEma, Math.abs(pr.pitchJitter), NEURO_STYLE_EMA_ALPHA);
        this.styleAmpEma = ema(this.styleAmpEma, Math.abs(pr.amp), NEURO_STYLE_EMA_ALPHA);

        if (pr.gcdYaw > 0.0f) {
            this.styleGcdYawEma = this.styleGcdYawEma == 0.0f ? pr.gcdYaw : ema(this.styleGcdYawEma, pr.gcdYaw, 0.12f);
        }
        if (pr.gcdPitch > 0.0f) {
            this.styleGcdPitchEma = this.styleGcdPitchEma == 0.0f ? pr.gcdPitch : ema(this.styleGcdPitchEma, pr.gcdPitch, 0.12f);
        }
    }

    private NeuroRotationModel.Prediction getPrediction(LivingEntity target, boolean plannedAttack, long now) {
        int tid = target.getId();
        if (this.predCache != null && this.predTargetId == tid && this.predCacheAttack == plannedAttack && (now - this.predCacheTime) <= 200L) {
            return this.predCache;
        }

        NeuroRotationModel.Prediction pr = this.model.predict(target, plannedAttack);
        this.predCache = pr;
        this.predCacheTime = now;
        this.predTargetId = tid;
        this.predCacheAttack = plannedAttack;
        return pr;
    }

    private Vec3d chooseExecPoint(LivingEntity target,
                                  Vec3d fallbackPoint,
                                  NeuroRotationModel.Prediction pr,
                                  NeuroRotationModel.FallbackPrediction fb) {
        Box hb = target.getBoundingBox();
        Vec3d center = new Vec3d((hb.minX + hb.maxX) * 0.5, hb.minY + target.getHeight() * 0.65, (hb.minZ + hb.maxZ) * 0.5);
        Vec3d basePoint = fallbackPoint != null ? fallbackPoint : center;
        if (pr == null) {
            return basePoint;
        }

        Vec3d predPoint = this.model.pointFromPrediction(target, pr);
        if (predPoint == null) {
            return basePoint;
        }

        float conf = MathHelper.clamp(pr.confidence, 0.0f, 1.0f);
        float pointConf = MathHelper.clamp(pr.pointConfidence / 100.0f, 0.0f, 1.0f);
        float fbConf = fb != null ? MathHelper.clamp(fb.confidence, 0.0f, 1.0f) : 0.0f;

        float pointBlend = MathHelper.clamp(0.25f + 0.75f * Math.max(conf, pointConf), 0.15f, 1.0f);
        if (fbConf > conf + 0.10f) {
            pointBlend *= 0.75f;
        }

        return new Vec3d(
                lerpD(basePoint.x, predPoint.x, pointBlend),
                lerpD(basePoint.y, predPoint.y, pointBlend),
                lerpD(basePoint.z, predPoint.z, pointBlend)
        );
    }

    private static Vec3d clampPointToBox(Vec3d point, Box hb) {
        if (point == null || hb == null) return point;
        return new Vec3d(
                clampD(point.x, hb.minX + 1.0e-4, hb.maxX - 1.0e-4),
                clampD(point.y, hb.minY + 1.0e-4, hb.maxY - 1.0e-4),
                clampD(point.z, hb.minZ + 1.0e-4, hb.maxZ - 1.0e-4)
        );
    }

    private static double lerpD(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static double clampD(double v, double mn, double mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }

    private static float ema(float cur, float value, float alpha) {
        if (alpha <= 0.0f) return cur;
        if (cur == 0.0f) return value;
        return cur + (value - cur) * MathHelper.clamp(alpha, 0.0f, 1.0f);
    }

    private static float softLimit(float v, float soft, float hard) {
        float av = Math.abs(v);
        if (av <= soft) return v;
        if (av >= hard) return Math.copySign(hard, v);

        float t = (av - soft) / Math.max(1.0e-6f, (hard - soft));
        t = MathHelper.clamp(t, 0.0f, 1.0f);
        float eased = t * t * (3.0f - 2.0f * t);

        float out = soft + (hard - soft) * eased;
        return Math.copySign(out, v);
    }

    private static float blendAngle(float from, float to, float t) {
        float d = MathHelper.wrapDegrees(to - from);
        return from + d * MathHelper.clamp(t, 0.0f, 1.0f);
    }

    private static float quantize(float v, float step) {
        if (step <= 0.0f) return v;
        float s = MathHelper.clamp(step, GCD_MIN, GCD_MAX);
        return Math.round(v / s) * s;
    }

    private static float easeOutCubic(float x) {
        x = MathHelper.clamp(x, 0.0f, 1.0f);
        float f = 1.0f - x;
        return 1.0f - f * f * f;
    }

    private static float rnd(float min, float max) {
        if (min >= max) return min;
        return min + ThreadLocalRandom.current().nextFloat() * (max - min);
    }
}
