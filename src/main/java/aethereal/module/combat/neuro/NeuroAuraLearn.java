package aethereal.module.combat.neuro;

import aethereal.module.combat.Aura;
import aethereal.util.Rotation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Optional;

public final class NeuroAuraLearn {

    private static final float NEURO_LEARN_RATE_TRACK = 0.10f;
    private static final float NEURO_LEARN_RATE_ATTACK = 0.16f;

    private static final float TRACK_ERR_SOFT = 28.0f;
    private static final float TRACK_ERR_HARD = 85.0f;
    private static final float ATTACK_ERR_SOFT = 38.0f;
    private static final float ATTACK_ERR_HARD = 105.0f;

    private static final float MIN_LR_MUL = 0.02f;
    private static final float MAX_LR_MUL = 1.35f;

    private static final int PATTERN_SAMPLES_TRACK = 2;
    private static final int PATTERN_SAMPLES_ATTACK = 4;

    private static final int POINT_SAMPLES_TRACK = 3;
    private static final int POINT_SAMPLES_ATTACK = 6;

    private static final float CAM_VEL_EMA = 0.18f;

    private static final long EXEC_NOTIFY_COOLDOWN_MS = 220L;
    private static final float EXEC_GCD_MIN = 0.006f;
    private static final float EXEC_GCD_MAX = 0.45f;

    private final HumanizerModel model = new HumanizerModel();

    private NeuroAuraExec exec;
    private boolean execReplayLoaded = false;
    private long lastExecNotifyMs = 0L;

    private boolean loaded = false;

    private int prevSwingTicks = 0;
    private int attackBurstTicks = 0;

    private float prevCamYaw = 0.0f;
    private float prevCamPitch = 0.0f;
    private float emaCamVelYaw = 0.0f;
    private float emaCamVelPitch = 0.0f;

    private float lastClampedYaw = 0.0f;
    private float lastClampedPitch = 0.0f;
    private boolean hasLastClamped = false;

    private int lastTargetId = Integer.MIN_VALUE;
    private int targetStableTicks = 0;

    private boolean dirty = false;

    public void bindExec(NeuroAuraExec exec) {
        this.exec = exec;
        this.execReplayLoaded = false;
    }

    public HumanizerProfile suggestHumanizer(LivingEntity target, boolean attackSample) {
        float camVel = (float) Math.sqrt(this.emaCamVelYaw * this.emaCamVelYaw + this.emaCamVelPitch * this.emaCamVelPitch);
        return this.model.suggestHumanizer(target, attackSample, camVel);
    }

    public void onActivate(Aura a) {
        this.prevSwingTicks = 0;
        this.attackBurstTicks = 0;
        this.hasLastClamped = false;
        this.lastTargetId = Integer.MIN_VALUE;
        this.targetStableTicks = 0;

        this.dirty = false;
        this.loaded = false;

        this.execReplayLoaded = false;
        this.lastExecNotifyMs = 0L;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            this.prevSwingTicks = mc.player.handSwingTicks;
            this.prevCamYaw = mc.player.getYaw();
            this.prevCamPitch = mc.player.getPitch();
            this.emaCamVelYaw = 0.0f;
            this.emaCamVelPitch = 0.0f;
        }
    }

    public void onDeactivate(Aura a) {
        flushSave(a);
        if (this.exec != null) {
            try {
                this.exec.notifyModelUpdated();
            } catch (Exception ignored) {
            }
        }
    }

    public void onTick(Aura a) {
        maybeSave(false);
    }

    public void learnStep(Aura a, boolean onAttack) {
        if (a == null) return;
        if (!a.neuroLearn()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (!this.loaded) {
            this.loaded = true;
            this.model.load(mc);
            this.prevSwingTicks = mc.player.handSwingTicks;
            this.prevCamYaw = mc.player.getYaw();
            this.prevCamPitch = mc.player.getPitch();
            this.emaCamVelYaw = 0.0f;
            this.emaCamVelPitch = 0.0f;
        }

        ensureExecReplayLoaded();

        LivingEntity t = a.getTarget();
        if (t == null) return;

        int tid = t.getId();
        if (tid != this.lastTargetId) {
            this.lastTargetId = tid;
            this.targetStableTicks = 0;
            this.hasLastClamped = false;
        } else {
            this.targetStableTicks++;
        }

        int curSwing = mc.player.handSwingTicks;
        boolean justAttacked = neuroSwingStarted(this.prevSwingTicks, curSwing);
        this.prevSwingTicks = curSwing;

        if (justAttacked) this.attackBurstTicks = 3;
        boolean burstAttack = onAttack || this.attackBurstTicks > 0;

        float camYaw = mc.player.getYaw();
        float camPitch = mc.player.getPitch();

        float dYawTick = wrapDeg(camYaw - this.prevCamYaw);
        float dPitchTick = camPitch - this.prevCamPitch;

        this.prevCamYaw = camYaw;
        this.prevCamPitch = camPitch;

        this.emaCamVelYaw = lerp(this.emaCamVelYaw, dYawTick, CAM_VEL_EMA);
        this.emaCamVelPitch = lerp(this.emaCamVelPitch, dPitchTick, CAM_VEL_EMA);

        float camVel = (float) Math.sqrt(this.emaCamVelYaw * this.emaCamVelYaw + this.emaCamVelPitch * this.emaCamVelPitch);

        Rotation targetRot = a.getCachedRotation();
        float baseYaw = targetRot != null ? targetRot.c() : camYaw;
        float basePitch = targetRot != null ? targetRot.d() : camPitch;

        float rawDy = wrapDeg(camYaw - baseYaw);
        float rawDp = camPitch - basePitch;

        float err = (float) Math.sqrt(rawDy * rawDy + rawDp * rawDp);

        float soft = burstAttack ? ATTACK_ERR_SOFT : TRACK_ERR_SOFT;
        float hard = burstAttack ? ATTACK_ERR_HARD : TRACK_ERR_HARD;

        float clipMul = 1.0f;
        if (err > soft) {
            float tMul = soft / Math.max(err, 0.001f);
            clipMul *= clamp(tMul, MIN_LR_MUL, 1.0f);
        }
        if (err > hard) {
            float tMul = hard / Math.max(err, 0.001f);
            clipMul *= clamp(tMul, 0.02f, 1.0f);
        }

        float velMul = clamp(0.55f + camVel * 0.11f, 0.55f, 1.25f);
        float stableMul = clamp(0.35f + (this.targetStableTicks / 8.0f), 0.35f, 1.0f);

        float lrBase = burstAttack ? NEURO_LEARN_RATE_ATTACK : NEURO_LEARN_RATE_TRACK;
        float lrMul = clipMul * velMul * stableMul;
        if (burstAttack && justAttacked) lrMul *= 1.10f;
        lrMul = clamp(lrMul, MIN_LR_MUL, MAX_LR_MUL);

        float maxErr = hard * 1.65f;
        float dy = clamp(rawDy, -maxErr, maxErr);
        float dp = clamp(rawDp, -maxErr, maxErr);

        float clampedYaw = baseYaw + dy;
        float clampedPitch = basePitch + dp;

        Box hb = a.getCachedHitbox() != null ? a.getCachedHitbox() : t.getBoundingBox();
        Vec3d computed = a.getCachedPoint();
        Vec3d aimedPoint = neuroAimPointFromView(hb, computed, t);

        int patternSamples = burstAttack ? PATTERN_SAMPLES_ATTACK : PATTERN_SAMPLES_TRACK;
        int pointSamples = burstAttack ? POINT_SAMPLES_ATTACK : POINT_SAMPLES_TRACK;

        if (camVel > 3.0f) {
            pointSamples = Math.min(pointSamples + 1, burstAttack ? 8 : 5);
            patternSamples = Math.min(patternSamples + 1, burstAttack ? 6 : 3);
        }

        if (patternSamples < 1) patternSamples = 1;
        if (pointSamples < 1) pointSamples = 1;

        if (!this.hasLastClamped) {
            this.lastClampedYaw = clampedYaw;
            this.lastClampedPitch = clampedPitch;
            this.hasLastClamped = true;
        }

        float dYawPath = wrapDeg(clampedYaw - this.lastClampedYaw);
        float dPitchPath = clampedPitch - this.lastClampedPitch;

        int seedBase = mixSeed(mc.player.age, tid);

        float stepYawAbs = Math.abs(dYawTick);
        float stepPitchAbs = Math.abs(dPitchTick);
        float jitterYaw = Math.abs(dYawTick - this.emaCamVelYaw);
        float jitterPitch = Math.abs(dPitchTick - this.emaCamVelPitch);

        touchDirty();

        boolean execLearned = false;

        for (int i = 0; i < patternSamples; i++) {
            float tt = (patternSamples == 1) ? 1.0f : (i + 1) / (float) (patternSamples + 1);

            float py = this.lastClampedYaw + dYawPath * tt;
            float pp = this.lastClampedPitch + dPitchPath * tt;

            if (burstAttack) {
                float os = (i == patternSamples - 1) ? 1.18f : 1.0f;
                py = this.lastClampedYaw + dYawPath * tt * os;
                pp = this.lastClampedPitch + dPitchPath * tt * os;
            }

            float ddy = clamp(wrapDeg(py - baseYaw), -maxErr, maxErr);
            float ddp = clamp(pp - basePitch, -maxErr, maxErr);

            float fy = baseYaw + ddy;
            float fp = basePitch + ddp;

            float lr = lrBase * lrMul * (1.0f / Math.max(1, patternSamples));

            for (int p = 0; p < pointSamples; p++) {
                Vec3d pt = pickLearnPoint(hb, aimedPoint, computed, t, seedBase, i, p, pointSamples, burstAttack, camVel);
                if (pt == null) continue;

                float lrPt = lr;
                if (!burstAttack && p > 0) lrPt *= 0.62f;
                if (burstAttack && p > 1) lrPt *= 0.75f;

                try {
                    this.model.learn(t, baseYaw, basePitch, fy, fp, hb, pt, lrPt, burstAttack, camVel, stepYawAbs, stepPitchAbs, jitterYaw, jitterPitch);
                } catch (Exception ignored) {
                }

                if (learnExecReplaySample(t, hb, pt, burstAttack, ddy, ddp, stepYawAbs, stepPitchAbs, jitterYaw, jitterPitch, lrPt, lrBase, ampFrom(ddy, ddp))) {
                    execLearned = true;
                }
            }
        }

        this.lastClampedYaw = clampedYaw;
        this.lastClampedPitch = clampedPitch;

        if (this.attackBurstTicks > 0) this.attackBurstTicks--;

        if (execLearned) {
            notifyExecReplayUpdated(false);
        }

        maybeSave(false);
    }

    public void flushSave(Aura a) {
        if (!this.dirty) {
            notifyExecReplayUpdated(true);
            return;
        }
        maybeSave(true);
        notifyExecReplayUpdated(true);
    }

    private void touchDirty() {
        this.dirty = true;
    }

    private void maybeSave(boolean force) {
        if (!this.dirty && !force) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        try {
            this.model.save(mc);
            this.dirty = false;
        } catch (Exception ignored) {
        }
    }

    private void ensureExecReplayLoaded() {
        NeuroAuraExec e = this.exec;
        if (e == null) return;
        if (this.execReplayLoaded) return;
        this.execReplayLoaded = true;
        try {
            NeuroRotationModel replay = e.getModel();
            if (replay != null) {
                replay.load();
            }
        } catch (Exception ignored) {
        }
    }

    private boolean learnExecReplaySample(LivingEntity target,
                                          Box hb,
                                          Vec3d point,
                                          boolean attackSample,
                                          float dyDeg,
                                          float dpDeg,
                                          float stepYawAbs,
                                          float stepPitchAbs,
                                          float jitterYawAbs,
                                          float jitterPitchAbs,
                                          float lrPt,
                                          float lrBase,
                                          float amp) {
        NeuroAuraExec e = this.exec;
        if (e == null || target == null || point == null) return false;

        NeuroRotationModel replay = e.getModel();
        if (replay == null) return false;

        Box box = hb != null ? hb : target.getBoundingBox();
        if (box == null) return false;

        double sx = box.maxX - box.minX;
        double sy = box.maxY - box.minY;
        double sz = box.maxZ - box.minZ;
        if (sx <= 1.0E-9 || sy <= 1.0E-9 || sz <= 1.0E-9) return false;

        float px = (float) clampD((point.x - box.minX) / sx, 0.0, 1.0);
        float py = (float) clampD((point.y - box.minY) / sy, 0.0, 1.0);
        float pz = (float) clampD((point.z - box.minZ) / sz, 0.0, 1.0);

        float yawSp = MathHelper.clamp(stepYawAbs * 20.0f, 0.0f, 240.0f);
        float pitSp = MathHelper.clamp(stepPitchAbs * 20.0f, 0.0f, 240.0f);

        float yJ = MathHelper.clamp(jitterYawAbs, 0.0f, 48.0f);
        float pJ = MathHelper.clamp(jitterPitchAbs, 0.0f, 48.0f);

        float gY = this.model.getGcdYawEma() > 0.0001f ? MathHelper.clamp(this.model.getGcdYawEma(), EXEC_GCD_MIN, EXEC_GCD_MAX) : 0.0f;
        float gP = this.model.getGcdPitchEma() > 0.0001f ? MathHelper.clamp(this.model.getGcdPitchEma(), EXEC_GCD_MIN, EXEC_GCD_MAX) : 0.0f;

        float rateMul = lrBase <= 0.0f ? 1.0f : (lrPt / lrBase);
        float rateBase = attackSample ? NEURO_LEARN_RATE_ATTACK : NEURO_LEARN_RATE_TRACK;
        float rate = MathHelper.clamp(rateBase * rateMul, 0.01f, 0.40f);

        try {
            boolean ok = replay.learn(
                    target,
                    attackSample,
                    dyDeg,
                    dpDeg,
                    px,
                    py,
                    pz,
                    yawSp,
                    pitSp,
                    yJ,
                    pJ,
                    gY,
                    gP,
                    MathHelper.clamp(amp, 0.0f, 50.0f),
                    rate
            );
            if (ok) {
                e.markModelDirty();
                return true;
            }
        } catch (Exception ignored) {
        }

        return false;
    }

    private void notifyExecReplayUpdated(boolean force) {
        NeuroAuraExec e = this.exec;
        if (e == null) return;

        long now = System.currentTimeMillis();
        if (!force && (now - this.lastExecNotifyMs) < EXEC_NOTIFY_COOLDOWN_MS) {
            try {
                e.markModelDirty();
            } catch (Exception ignored) {
            }
            return;
        }

        this.lastExecNotifyMs = now;
        try {
            e.notifyModelUpdated();
        } catch (Exception ignored) {
        }
    }

    private static float ampFrom(float dy, float dp) {
        return (float) Math.sqrt(dy * dy + dp * dp);
    }

    private Vec3d pickLearnPoint(Box hb, Vec3d aimed, Vec3d computed, LivingEntity t, int seedBase, int patIdx, int pIdx, int pCount, boolean onAttack, float camVel) {
        if (pIdx == 0) return aimed != null ? aimed : computed;
        if (pIdx == 1 && computed != null) return computed;

        if (hb == null) return aimed != null ? aimed : computed;

        double cx = (hb.minX + hb.maxX) * 0.5;
        double cy = (hb.minY + hb.maxY) * 0.5;
        double cz = (hb.minZ + hb.maxZ) * 0.5;

        double sx = (hb.maxX - hb.minX) * 0.5;
        double sy = (hb.maxY - hb.minY) * 0.5;
        double sz = (hb.maxZ - hb.minZ) * 0.5;

        int s = seedBase ^ (patIdx * 0x9E3779B9) ^ (pIdx * 0x85EBCA6B);
        float r1 = rand01(s);
        float r2 = rand01(s ^ 0x68BC21EB);
        float r3 = rand01(s ^ 0x02E5BE93);

        float rel = relativeHeightBlocks(t);

        float biasUp = onAttack ? 0.10f : 0.0f;
        float biasMid = camVel > 2.0f ? 0.06f : 0.0f;

        float surfaceBias = 0.0f;
        if (rel <= -0.55f) {
            float k = clamp((-rel - 0.55f) / 2.25f, 0.0f, 1.0f);
            surfaceBias = 0.18f + 0.30f * k;
        } else if (rel >= 0.55f) {
            float k = clamp((rel - 0.55f) / 2.25f, 0.0f, 1.0f);
            surfaceBias = -(0.18f + 0.30f * k);
        }

        float oy = (r2 - 0.5f) * 0.90f + biasUp + biasMid + surfaceBias;
        float ox = (r1 - 0.5f) * 0.95f;
        float oz = (r3 - 0.5f) * 0.95f;

        if (!onAttack && pIdx == pCount - 1) {
            if (rel >= 0.55f) {
                oy = -0.34f;
            } else if (rel <= -0.55f) {
                oy = 0.52f;
            } else {
                oy = 0.18f;
            }
            ox *= 0.65f;
            oz *= 0.65f;
        }

        double px = cx + ox * sx;
        double py = cy + oy * sy;
        double pz = cz + oz * sz;

        px = clampD(px, hb.minX + 1.0E-4, hb.maxX - 1.0E-4);
        py = clampD(py, hb.minY + 1.0E-4, hb.maxY - 1.0E-4);
        pz = clampD(pz, hb.minZ + 1.0E-4, hb.maxZ - 1.0E-4);

        return new Vec3d(px, py, pz);
    }

    private static float relativeHeightBlocks(LivingEntity t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || t == null) return 0.0f;
        return (float) (t.getEyeY() - mc.player.getEyeY());
    }

    private static Vec3d neuroAimPointFromView(Box hb, Vec3d fallback, LivingEntity t) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return fallback;
        if (hb == null) return fallback;

        Vec3d eye = mc.player.getEyePos();
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        Vec3d dir = vecFromYawPitch(yaw, pitch);
        if (dir == null) return fallback;

        double dist = 7.0;
        if (t != null) {
            dist = Math.max(3.0, mc.player.distanceTo(t) + 3.5);
        }

        Vec3d end = eye.add(dir.multiply(dist));
        Vec3d hit = boxRaycast(hb, eye, end);
        if (hit != null) return hit;

        if (fallback != null) return fallback;

        return new Vec3d((hb.minX + hb.maxX) * 0.5, (hb.minY + hb.maxY) * 0.6, (hb.minZ + hb.maxZ) * 0.5);
    }

    private static Vec3d boxRaycast(Box box, Vec3d start, Vec3d end) {
        if (box == null || start == null || end == null) return null;
        Optional<Vec3d> opt = box.raycast(start, end);
        return opt.orElse(null);
    }

    private static Vec3d vecFromYawPitch(float yawDeg, float pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cp = Math.cos(pitch);
        return new Vec3d(-Math.sin(yaw) * cp, -Math.sin(pitch), Math.cos(yaw) * cp);
    }

    private static boolean neuroSwingStarted(int prev, int cur) {
        if (cur < prev) return true;
        if (prev == 0 && cur > 0) return true;
        if (prev > 0 && cur == 0) return true;
        return false;
    }

    private static float wrapDeg(float v) {
        v %= 360.0f;
        if (v >= 180.0f) v -= 360.0f;
        if (v < -180.0f) v += 360.0f;
        return v;
    }

    private static float clamp(float v, float mn, float mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }

    private static double clampD(double v, double mn, double mx) {
        return v < mn ? mn : (v > mx ? mx : v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int mixSeed(int a, int b) {
        int x = a * 0x9E3779B9;
        x ^= b * 0x85EBCA6B;
        x ^= (x >>> 16);
        x *= 0xC2B2AE35;
        x ^= (x >>> 13);
        x *= 0x27D4EB2F;
        x ^= (x >>> 16);
        return x;
    }

    private static float rand01(int x) {
        x ^= (x >>> 16);
        x *= 0x7FEB352D;
        x ^= (x >>> 15);
        x *= 0x846CA68B;
        x ^= (x >>> 16);
        int m = x & 0x00FFFFFF;
        return m / 16777215.0f;
    }

    public static final class HumanizerProfile {
        private final float noiseYaw;
        private final float noisePitch;
        private final float freqYaw;
        private final float freqPitch;
        private final float gate;
        private final float gcdHintYaw;
        private final float gcdHintPitch;
        private final float confidence;

        public HumanizerProfile(float noiseYaw, float noisePitch, float freqYaw, float freqPitch, float gate, float gcdHintYaw, float gcdHintPitch, float confidence) {
            this.noiseYaw = noiseYaw;
            this.noisePitch = noisePitch;
            this.freqYaw = freqYaw;
            this.freqPitch = freqPitch;
            this.gate = gate;
            this.gcdHintYaw = gcdHintYaw;
            this.gcdHintPitch = gcdHintPitch;
            this.confidence = confidence;
        }

        public float noiseYaw() { return this.noiseYaw; }
        public float noisePitch() { return this.noisePitch; }
        public float freqYaw() { return this.freqYaw; }
        public float freqPitch() { return this.freqPitch; }
        public float gate() { return this.gate; }
        public float gcdHintYaw() { return this.gcdHintYaw; }
        public float gcdHintPitch() { return this.gcdHintPitch; }
        public float confidence() { return this.confidence; }
    }

    public static final class HumanizerModel {
        private static final int INPUT_SIZE = 10;
        private static final int HIDDEN1 = 32;
        private static final int HIDDEN2 = 32;
        private static final int OUTPUT_SIZE = 3;

        private final float[] w1 = new float[INPUT_SIZE * HIDDEN1];
        private final float[] b1 = new float[HIDDEN1];
        private final float[] w2 = new float[HIDDEN1 * HIDDEN2];
        private final float[] b2 = new float[HIDDEN2];
        private final float[] w3 = new float[HIDDEN2 * OUTPUT_SIZE];
        private final float[] b3 = new float[OUTPUT_SIZE];

        private final float[] h1 = new float[HIDDEN1];
        private final float[] h2 = new float[HIDDEN2];
        private final float[] out = new float[OUTPUT_SIZE];
        private final float[] dOut = new float[OUTPUT_SIZE];
        private final float[] dH2 = new float[HIDDEN2];
        private final float[] dH1 = new float[HIDDEN1];

        private final float[] bufInput = new float[INPUT_SIZE];
        private final float[] bufTarget = new float[OUTPUT_SIZE];

        private float yawSpeedEma;
        private float pitchSpeedEma;
        private float yawJitterEma;
        private float pitchJitterEma;
        private float ampEma;
        private float posErrEma;
        private float camVelEma;
        private float gcdYawEma;
        private float gcdPitchEma;
        private float samples;

        public HumanizerModel() {
            initRandomWeights();
        }

        public float getGcdYawEma() {
            return this.gcdYawEma;
        }

        public float getGcdPitchEma() {
            return this.gcdPitchEma;
        }

        private void initRandomWeights() {
            java.util.Random rnd = new java.util.Random(0xC0FFEE);
            float scale1 = (float) (1.0 / Math.sqrt(INPUT_SIZE));
            for (int i = 0; i < this.w1.length; i++) {
                this.w1[i] = (float) (rnd.nextGaussian() * scale1);
            }
            for (int i = 0; i < this.b1.length; i++) {
                this.b1[i] = 0.0f;
            }

            float scale2 = (float) (1.0 / Math.sqrt(HIDDEN1));
            for (int i = 0; i < this.w2.length; i++) {
                this.w2[i] = (float) (rnd.nextGaussian() * scale2);
            }
            for (int i = 0; i < this.b2.length; i++) {
                this.b2[i] = 0.0f;
            }

            float scale3 = (float) (1.0 / Math.sqrt(HIDDEN2));
            for (int i = 0; i < this.w3.length; i++) {
                this.w3[i] = (float) (rnd.nextGaussian() * scale3);
            }
            for (int i = 0; i < this.b3.length; i++) {
                this.b3[i] = 0.0f;
            }

            this.yawSpeedEma = 0.0f;
            this.pitchSpeedEma = 0.0f;
            this.yawJitterEma = 0.0f;
            this.pitchJitterEma = 0.0f;
            this.ampEma = 0.0f;
            this.posErrEma = 0.0f;
            this.camVelEma = 0.0f;
            this.gcdYawEma = 0.0f;
            this.gcdPitchEma = 0.0f;
            this.samples = 0.0f;
        }

        public HumanizerProfile suggestHumanizer(LivingEntity t, boolean attackSample, float camVel) {
            float sampleFactor = MathHelper.clamp(this.samples / 600.0f, 0.0f, 1.0f);
            float posErr = MathHelper.clamp(this.posErrEma, 0.0f, 1.5f);
            float confE = (float) Math.exp(-posErr * 1.25f);
            float confidence = MathHelper.clamp(sampleFactor * confE, 0.0f, 1.0f);

            float speedN = MathHelper.clamp(((Math.abs(this.yawSpeedEma) + Math.abs(this.pitchSpeedEma)) * 0.5f) / 18.0f, 0.0f, 1.0f);
            float jitterN = MathHelper.clamp(((Math.abs(this.yawJitterEma) + Math.abs(this.pitchJitterEma)) * 0.5f) / 10.0f, 0.0f, 1.0f);
            float ampN = MathHelper.clamp(this.ampEma / 40.0f, 0.0f, 1.0f);
            float camN = MathHelper.clamp(camVel / 6.0f, 0.0f, 1.0f);

            float stable = confidence;
            float low = 1.0f - stable;

            float noiseYaw = 0.10f + stable * 0.30f + jitterN * 0.20f + ampN * 0.12f;
            float noisePitch = 0.08f + stable * 0.24f + jitterN * 0.16f + ampN * 0.10f;

            float speedMul = 1.0f - speedN * 0.30f;
            float camMul = 1.0f - camN * 0.55f;

            noiseYaw *= speedMul * camMul;
            noisePitch *= speedMul * camMul;

            if (attackSample) {
                noiseYaw *= 0.85f;
                noisePitch *= 0.80f;
            } else {
                noiseYaw *= 0.95f + low * 0.05f;
                noisePitch *= 0.95f + low * 0.05f;
            }

            noiseYaw = MathHelper.clamp(noiseYaw, 0.04f, 1.25f);
            noisePitch = MathHelper.clamp(noisePitch, 0.03f, 1.05f);

            float freqBase = 0.55f + speedN * 0.75f + jitterN * 0.40f + stable * 0.25f;
            if (attackSample) freqBase *= 1.05f;

            float freqYaw = MathHelper.clamp(freqBase, 0.45f, 2.40f);
            float freqPitch = MathHelper.clamp(freqBase * 0.92f, 0.40f, 2.20f);

            float gate = (0.30f + 0.70f * stable) * (1.0f - camN * 0.55f);
            if (attackSample) gate *= 0.86f;
            gate = MathHelper.clamp(gate, 0.10f, 1.0f);

            float gcdHintYaw = this.gcdYawEma > 0.0005f ? MathHelper.clamp(this.gcdYawEma, 0.0005f, 3.5f) : 0.0f;
            float gcdHintPitch = this.gcdPitchEma > 0.0005f ? MathHelper.clamp(this.gcdPitchEma, 0.0005f, 3.5f) : 0.0f;

            return new HumanizerProfile(noiseYaw, noisePitch, freqYaw, freqPitch, gate, gcdHintYaw, gcdHintPitch, confidence);
        }

        public void learn(LivingEntity t, float baseYaw, float basePitch, float plyYaw, float plyPitch, Box hb, Vec3d aimedPoint, float baseLr, boolean attackSample, float camVel, float stepYaw, float stepPitch, float jitterYaw, float jitterPitch) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || t == null) return;

            Vec3d tv = t.getVelocity();
            float sp = (float) tv.horizontalLength();
            float dist = mc.player.distanceTo(t);

            float relHeight = (float) (t.getEyeY() - mc.player.getEyeY());

            float dyRaw = MathHelper.wrapDegrees(plyYaw - baseYaw);
            float dpRaw = MathHelper.clamp(plyPitch - basePitch, -90.0f, 90.0f);

            float dyClamp = MathHelper.clamp(dyRaw, -70.0f, 70.0f);
            float dpClamp = MathHelper.clamp(dpRaw, -50.0f, 50.0f);

            float dyNorm = dyClamp / 70.0f;
            float dpNorm = dpClamp / 50.0f;

            float heightNorm = 0.6f;
            if (hb != null && aimedPoint != null) {
                double hy = hb.maxY - hb.minY;
                if (hy > 1.0E-6) {
                    heightNorm = (float) ((aimedPoint.y - hb.minY) / hy);
                    heightNorm = MathHelper.clamp(heightNorm, 0.0f, 1.0f);
                }
            }
            float heightScaled = heightNorm * 2.0f - 1.0f;

            float stepYawAbs = Math.abs(stepYaw);
            float stepPitchAbs = Math.abs(stepPitch);
            float jitterYawAbs = Math.abs(jitterYaw);
            float jitterPitchAbs = Math.abs(jitterPitch);
            float amp = (float) Math.sqrt(dyClamp * dyClamp + dpClamp * dpClamp);

            this.yawSpeedEma = updateEma(this.yawSpeedEma, stepYawAbs, 0.05f);
            this.pitchSpeedEma = updateEma(this.pitchSpeedEma, stepPitchAbs, 0.05f);
            this.yawJitterEma = updateEma(this.yawJitterEma, jitterYawAbs, 0.05f);
            this.pitchJitterEma = updateEma(this.pitchJitterEma, jitterPitchAbs, 0.05f);
            this.ampEma = updateEma(this.ampEma, amp, 0.05f);

            if (hb != null && aimedPoint != null) {
                double hx = hb.maxX - hb.minX;
                double hy = hb.maxY - hb.minY;
                double hz = hb.maxZ - hb.minZ;
                if (hx > 1.0E-6 && hy > 1.0E-6 && hz > 1.0E-6) {
                    float pxN = (float) ((aimedPoint.x - hb.minX) / hx);
                    float pyN = (float) ((aimedPoint.y - hb.minY) / hy);
                    float pzN = (float) ((aimedPoint.z - hb.minZ) / hz);
                    pxN = MathHelper.clamp(pxN, 0.0f, 1.0f);
                    pyN = MathHelper.clamp(pyN, 0.0f, 1.0f);
                    pzN = MathHelper.clamp(pzN, 0.0f, 1.0f);
                    float dx = pxN - 0.5f;
                    float dyPos = pyN - 0.65f;
                    float dz = pzN - 0.5f;
                    float posErr = (float) Math.sqrt(dx * dx + dyPos * dyPos + dz * dz);
                    this.posErrEma = updateEma(this.posErrEma, posErr, 0.05f);
                }
            }

            this.camVelEma = updateEma(this.camVelEma, camVel, 0.05f);
            if (stepYawAbs > 0.001f) this.gcdYawEma = updateGcd(this.gcdYawEma, stepYawAbs, 0.03f);
            if (stepPitchAbs > 0.001f) this.gcdPitchEma = updateGcd(this.gcdPitchEma, stepPitchAbs, 0.03f);
            if (this.samples < 1_000_000f) this.samples += 1.0f;

            float[] x = this.bufInput;
            x[0] = MathHelper.clamp(dist / 8.0f, 0.0f, 1.0f);
            x[1] = MathHelper.clamp(relHeight / 4.0f, -1.0f, 1.0f);
            x[2] = MathHelper.clamp(sp / 1.4f, 0.0f, 1.0f);
            x[3] = MathHelper.clamp(camVel / 8.0f, 0.0f, 1.0f);
            x[4] = MathHelper.clamp(stepYaw / 45.0f, -1.0f, 1.0f);
            x[5] = MathHelper.clamp(stepPitch / 45.0f, -1.0f, 1.0f);
            x[6] = MathHelper.clamp((jitterYawAbs + jitterPitchAbs) * 0.5f / 25.0f, 0.0f, 1.0f);
            boolean airborne = mc.player.isGliding() || t.isGliding() || !mc.player.isOnGround();
            x[7] = airborne ? 1.0f : 0.0f;
            x[8] = attackSample ? 1.0f : 0.0f;
            int side = sideBin(tv, mc.player.getYaw());
            float sideVal = (side == 0) ? -1.0f : ((side == 2) ? 1.0f : 0.0f);
            x[9] = sideVal;

            float[] y = this.bufTarget;
            y[0] = dyNorm;
            y[1] = dpNorm;
            y[2] = heightScaled;

            float lr = MathHelper.clamp(baseLr * 0.65f, 0.0025f, 0.05f);
            if (attackSample) lr = MathHelper.clamp(lr * 1.5f, 0.0035f, 0.08f);

            trainSample(x, y, lr);
        }

        private void trainSample(float[] input, float[] target, float lr) {
            float[] y = forward(input);

            for (int k = 0; k < OUTPUT_SIZE; k++) {
                float err = y[k] - target[k];
                float gradOut = err * (1.0f - y[k] * y[k]);
                this.dOut[k] = gradOut;
            }

            for (int j = 0; j < HIDDEN2; j++) {
                float sum = 0.0f;
                int base = j * OUTPUT_SIZE;
                for (int k = 0; k < OUTPUT_SIZE; k++) {
                    sum += this.dOut[k] * this.w3[base + k];
                }
                this.dH2[j] = (1.0f - this.h2[j] * this.h2[j]) * sum;
            }

            for (int i = 0; i < HIDDEN1; i++) {
                float sum = 0.0f;
                int base = i * HIDDEN2;
                for (int j = 0; j < HIDDEN2; j++) {
                    sum += this.dH2[j] * this.w2[base + j];
                }
                this.dH1[i] = (1.0f - this.h1[i] * this.h1[i]) * sum;
            }

            for (int j = 0; j < HIDDEN2; j++) {
                int base = j * OUTPUT_SIZE;
                for (int k = 0; k < OUTPUT_SIZE; k++) {
                    int idx = base + k;
                    this.w3[idx] -= lr * this.dOut[k] * this.h2[j];
                }
            }
            for (int k = 0; k < OUTPUT_SIZE; k++) {
                this.b3[k] -= lr * this.dOut[k];
            }

            for (int i = 0; i < HIDDEN1; i++) {
                int base = i * HIDDEN2;
                for (int j = 0; j < HIDDEN2; j++) {
                    int idx = base + j;
                    this.w2[idx] -= lr * this.dH2[j] * this.h1[i];
                }
            }
            for (int j = 0; j < HIDDEN2; j++) {
                this.b2[j] -= lr * this.dH2[j];
            }

            for (int i = 0; i < INPUT_SIZE; i++) {
                int base = i * HIDDEN1;
                for (int j = 0; j < HIDDEN1; j++) {
                    int idx = base + j;
                    this.w1[idx] -= lr * this.dH1[j] * input[i];
                }
            }
            for (int j = 0; j < HIDDEN1; j++) {
                this.b1[j] -= lr * this.dH1[j];
            }
        }

        private float[] forward(float[] input) {
            for (int j = 0; j < HIDDEN1; j++) {
                float sum = this.b1[j];
                for (int i = 0; i < INPUT_SIZE; i++) {
                    sum += input[i] * this.w1[i * HIDDEN1 + j];
                }
                this.h1[j] = tanh(sum);
            }

            for (int j = 0; j < HIDDEN2; j++) {
                float sum = this.b2[j];
                for (int i = 0; i < HIDDEN1; i++) {
                    sum += this.h1[i] * this.w2[i * HIDDEN2 + j];
                }
                this.h2[j] = tanh(sum);
            }

            for (int k = 0; k < OUTPUT_SIZE; k++) {
                float sum = this.b3[k];
                for (int j = 0; j < HIDDEN2; j++) {
                    sum += this.h2[j] * this.w3[j * OUTPUT_SIZE + k];
                }
                this.out[k] = tanh(sum);
            }

            return this.out;
        }

        private static float tanh(float x) {
            return (float) Math.tanh(x);
        }

        private static float updateEma(float cur, float value, float alpha) {
            if (cur == 0.0f) return value;
            return cur + (value - cur) * alpha;
        }

        private static float updateGcd(float cur, float step, float alpha) {
            step = MathHelper.clamp(step, 0.0005f, 90.0f);
            if (cur <= 0.0001f) return step;
            float ratio = step / Math.max(cur, 0.0005f);
            float nearest = Math.max(1.0f, Math.round(ratio));
            float cand = step / Math.max(nearest, 1.0f);
            return cur + (cand - cur) * alpha;
        }

        private static int sideBin(Vec3d tv, float playerYawDeg) {
            if (tv == null) return 1;
            double vx = tv.x;
            double vz = tv.z;
            double sp = Math.sqrt(vx * vx + vz * vz);
            if (sp < 0.02) return 1;

            double yaw = Math.toRadians(playerYawDeg);
            double rx = Math.cos(yaw);
            double rz = Math.sin(yaw);

            double s = vx * rx + vz * rz;

            if (s > 0.03) return 2;
            if (s < -0.03) return 0;
            return 1;
        }

        private static File filePath(MinecraftClient mc) {
            File dir = new File(mc.runDirectory, "delta");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, "neuro_aura_learn.json");
        }

        public void save(MinecraftClient mc) {
            if (mc == null) return;
            try {
                int extraFloats = 10;
                int totalFloats = this.w1.length + this.b1.length + this.w2.length + this.b2.length + this.w3.length + this.b3.length + extraFloats;
                int bytes = 4 * 4 + totalFloats * 4;

                ByteBuffer bb = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
                bb.putInt(INPUT_SIZE);
                bb.putInt(HIDDEN1);
                bb.putInt(HIDDEN2);
                bb.putInt(OUTPUT_SIZE);

                for (float v : this.w1) bb.putFloat(v);
                for (float v : this.b1) bb.putFloat(v);
                for (float v : this.w2) bb.putFloat(v);
                for (float v : this.b2) bb.putFloat(v);
                for (float v : this.w3) bb.putFloat(v);
                for (float v : this.b3) bb.putFloat(v);

                bb.putFloat(this.yawSpeedEma);
                bb.putFloat(this.pitchSpeedEma);
                bb.putFloat(this.yawJitterEma);
                bb.putFloat(this.pitchJitterEma);
                bb.putFloat(this.ampEma);
                bb.putFloat(this.posErrEma);
                bb.putFloat(this.camVelEma);
                bb.putFloat(this.gcdYawEma);
                bb.putFloat(this.gcdPitchEma);
                bb.putFloat(this.samples);

                String b64 = Base64.getEncoder().encodeToString(bb.array());
                String json = "{\"v\":11,\"b64\":\"" + b64 + "\"}";

                Files.writeString(
                        filePath(mc).toPath(),
                        json,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING
                );
            } catch (Exception ignored) {
            }
        }

        public void load(MinecraftClient mc) {
            if (mc == null) return;
            try {
                File p = filePath(mc);
                if (!p.exists()) return;

                String json = Files.readString(p.toPath(), StandardCharsets.UTF_8);
                int b0 = json.indexOf("\"b64\":\"");
                if (b0 < 0) return;
                int s = b0 + 7;
                int e = json.indexOf('"', s);
                if (e <= s) return;
                String b64 = json.substring(s, e);
                byte[] data = Base64.getDecoder().decode(b64);
                ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

                int in = bb.getInt();
                int h1 = bb.getInt();
                int h2 = bb.getInt();
                int outSize = bb.getInt();

                if (in != INPUT_SIZE || h1 != HIDDEN1 || h2 != HIDDEN2 || outSize != OUTPUT_SIZE) {
                    return;
                }

                if (data.length < (4 * 4 + (this.w1.length + this.b1.length + this.w2.length + this.b2.length + this.w3.length + this.b3.length + 10) * 4)) {
                    return;
                }

                for (int i = 0; i < this.w1.length; i++) this.w1[i] = bb.getFloat();
                for (int i = 0; i < this.b1.length; i++) this.b1[i] = bb.getFloat();
                for (int i = 0; i < this.w2.length; i++) this.w2[i] = bb.getFloat();
                for (int i = 0; i < this.b2.length; i++) this.b2[i] = bb.getFloat();
                for (int i = 0; i < this.w3.length; i++) this.w3[i] = bb.getFloat();
                for (int i = 0; i < this.b3.length; i++) this.b3[i] = bb.getFloat();

                this.yawSpeedEma = bb.getFloat();
                this.pitchSpeedEma = bb.getFloat();
                this.yawJitterEma = bb.getFloat();
                this.pitchJitterEma = bb.getFloat();
                this.ampEma = bb.getFloat();
                this.posErrEma = bb.getFloat();
                this.camVelEma = bb.getFloat();
                this.gcdYawEma = bb.getFloat();
                this.gcdPitchEma = bb.getFloat();
                this.samples = bb.getFloat();
            } catch (Exception ignored) {
            }
        }
    }
}
