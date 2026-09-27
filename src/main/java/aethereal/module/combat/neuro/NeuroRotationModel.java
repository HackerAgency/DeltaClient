package aethereal.module.combat.neuro;

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
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class NeuroRotationModel {

    public static final float AIM_MAX_YAW_DELTA = 38.0f;
    public static final float AIM_MAX_PITCH_DELTA = 28.0f;
    public static final float GCD_MIN = 0.006f;
    public static final float GCD_MAX = 0.45f;

    private static final int DIST_BINS = 14;
    private static final int SPEED_BINS = 10;
    private static final int AIR_BINS = 2;
    private static final int MODE_BINS = 2;
    private static final int SIDE_BINS = 3;

    private static final int SIZE = DIST_BINS * SPEED_BINS * AIR_BINS * MODE_BINS * SIDE_BINS;

    private static final float DIST_MAX = 6.0f;
    private static final float SPEED_MAX = 0.75f;

    private static final int NN_IN = 7;
    private static final int NN_H1 = 24;
    private static final int NN_H2 = 16;
    private static final int NN_OUT = 13;

    private static final int MAGIC = 0x4F52414E;

    private final NeuralNet net = new NeuralNet(NN_IN, NN_H1, NN_H2, NN_OUT);

    private final int[] count = new int[SIZE];

    private final float[] fbYawAvg = new float[SIZE];
    private final float[] fbPitchAvg = new float[SIZE];
    private final float[] fbErrEma = new float[SIZE];
    private final int[] fbCount = new int[SIZE];

    private float lossEma = 0.0f;

    private java.nio.file.Path filePath(MinecraftClient mc) {
        File dir = new File(mc.runDirectory, "delta");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, "neuro_aura.json").toPath();
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

    private int idxBins(int db, int sb, int ab, int mb, int xb) {
        int i = MathHelper.clamp(db, 0, DIST_BINS - 1);
        i = i * SPEED_BINS + MathHelper.clamp(sb, 0, SPEED_BINS - 1);
        i = i * AIR_BINS + MathHelper.clamp(ab, 0, AIR_BINS - 1);
        i = i * MODE_BINS + MathHelper.clamp(mb, 0, MODE_BINS - 1);
        i = i * SIDE_BINS + MathHelper.clamp(xb, 0, SIDE_BINS - 1);
        return i;
    }

    private int idxFor(LivingEntity target, boolean attackSample) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) return -1;

        float dist = mc.player.distanceTo(target);
        Vec3d v = target.getVelocity();
        float sp = (float) Math.sqrt(v.x * v.x + v.z * v.z);

        boolean air = mc.player.isGliding() || target.isGliding() || !mc.player.isOnGround();
        int ab = air ? 1 : 0;

        float distN = MathHelper.clamp(dist / DIST_MAX, 0.0f, 1.0f);
        float speedN = MathHelper.clamp(sp / SPEED_MAX, 0.0f, 1.0f);

        int db = MathHelper.clamp((int) Math.floor(distN * (DIST_BINS - 1)), 0, DIST_BINS - 1);
        int sb = MathHelper.clamp((int) Math.floor(speedN * (SPEED_BINS - 1)), 0, SPEED_BINS - 1);

        int mode = attackSample ? 1 : 0;
        int side = sideBin(v, mc.player.getYaw());

        return idxBins(db, sb, ab, mode, side);
    }

    private void fillFeatures(float[] x, LivingEntity target, boolean attackSample) {
        MinecraftClient mc = MinecraftClient.getInstance();

        float dist = 3.0f;
        float sp = 0.0f;
        boolean air = false;
        int side = 1;

        if (mc.player != null && target != null) {
            dist = mc.player.distanceTo(target);
            Vec3d v = target.getVelocity();
            sp = (float) Math.sqrt(v.x * v.x + v.z * v.z);
            air = mc.player.isGliding() || target.isGliding() || !mc.player.isOnGround();
            side = sideBin(v, mc.player.getYaw());
        }

        float distN = MathHelper.clamp(dist / DIST_MAX, 0.0f, 1.0f);
        float speedN = MathHelper.clamp(sp / SPEED_MAX, 0.0f, 1.0f);

        x[0] = distN;
        x[1] = speedN;
        x[2] = air ? 1.0f : 0.0f;
        x[3] = attackSample ? 1.0f : 0.0f;

        x[4] = (side == 0) ? 1.0f : 0.0f;
        x[5] = (side == 1) ? 1.0f : 0.0f;
        x[6] = (side == 2) ? 1.0f : 0.0f;
    }

    public boolean learn(LivingEntity target,
                         boolean attackSample,
                         float yawDeltaDeg,
                         float pitchDeltaDeg,
                         float px, float py, float pz,
                         float yawSpeed, float pitchSpeed,
                         float yawJitter, float pitchJitter,
                         float gcdYaw, float gcdPitch,
                         float amp,
                         float rate) {
        int i = idxFor(target, attackSample);
        if (i < 0) return false;

        float r = MathHelper.clamp(rate, 0.01f, 0.40f);

        float dy = MathHelper.clamp(yawDeltaDeg, -AIM_MAX_YAW_DELTA, AIM_MAX_YAW_DELTA);
        float dp = MathHelper.clamp(pitchDeltaDeg, -AIM_MAX_PITCH_DELTA, AIM_MAX_PITCH_DELTA);

        float err = (Math.abs(dy) + Math.abs(dp)) * 0.5f;

        int c = count[i];
        if (c < 2000000000) count[i] = c + 1;

        float fbR = MathHelper.clamp(r * 0.65f, 0.01f, 0.30f);
        fbYawAvg[i] = fbYawAvg[i] + MathHelper.wrapDegrees(dy - fbYawAvg[i]) * fbR;
        fbPitchAvg[i] = fbPitchAvg[i] + MathHelper.wrapDegrees(dp - fbPitchAvg[i]) * fbR;
        fbErrEma[i] = fbErrEma[i] + (err - fbErrEma[i]) * (fbR * 0.85f);
        int fc = fbCount[i];
        if (fc < 2000000000) fbCount[i] = fc + 1;

        float[] x = net.tmpIn();
        fillFeatures(x, target, attackSample);

        float dyN = MathHelper.clamp(dy / AIM_MAX_YAW_DELTA, -1.0f, 1.0f);
        float dpN = MathHelper.clamp(dp / AIM_MAX_PITCH_DELTA, -1.0f, 1.0f);

        float pxx = MathHelper.clamp(px, 0.0f, 1.0f);
        float pyy = MathHelper.clamp(py, 0.0f, 1.0f);
        float pzz = MathHelper.clamp(pz, 0.0f, 1.0f);

        float ySp = MathHelper.clamp(yawSpeed, 0.0f, 240.0f) / 240.0f;
        float pSp = MathHelper.clamp(pitchSpeed, 0.0f, 240.0f) / 240.0f;

        float yJ = MathHelper.clamp(yawJitter / AIM_MAX_YAW_DELTA, -1.0f, 1.0f);
        float pJ = MathHelper.clamp(pitchJitter / AIM_MAX_PITCH_DELTA, -1.0f, 1.0f);

        float gY = (gcdYaw == 0.0f) ? 0.0f : MathHelper.clamp(Math.abs(gcdYaw) / GCD_MAX, 0.0f, 1.0f);
        float gP = (gcdPitch == 0.0f) ? 0.0f : MathHelper.clamp(Math.abs(gcdPitch) / GCD_MAX, 0.0f, 1.0f);

        float aN = MathHelper.clamp(amp / 50.0f, 0.0f, 1.0f);

        float errN = MathHelper.clamp(err / 20.0f, 0.0f, 1.0f);

        float[] t = net.tmpTarget();
        t[0] = dyN;
        t[1] = dpN;
        t[2] = pxx;
        t[3] = pyy;
        t[4] = pzz;
        t[5] = ySp;
        t[6] = pSp;
        t[7] = yJ;
        t[8] = pJ;
        t[9] = gY;
        t[10] = gP;
        t[11] = aN;
        t[12] = errN;

        float lr = 0.0025f + 0.020f * r;
        if (attackSample) lr *= 1.10f;

        float loss = net.train(x, t, lr, 0.00012f);
        if (loss > 0.0f) {
            if (lossEma == 0.0f) lossEma = loss;
            else lossEma = lossEma + (loss - lossEma) * 0.06f;
        }

        return true;
    }

    public Prediction predict(LivingEntity target) {
        return predict(target, true);
    }

    public Prediction predict(LivingEntity target, boolean attackSample) {
        float[] x = net.tmpIn();
        fillFeatures(x, target, attackSample);

        float[] out = net.forward(x);

        float dyN = MathHelper.clamp(out[0], -1.0f, 1.0f);
        float dpN = MathHelper.clamp(out[1], -1.0f, 1.0f);

        float px = MathHelper.clamp(out[2], 0.0f, 1.0f);
        float py = MathHelper.clamp(out[3], 0.0f, 1.0f);
        float pz = MathHelper.clamp(out[4], 0.0f, 1.0f);

        float ySp = MathHelper.clamp(out[5], 0.0f, 1.0f);
        float pSp = MathHelper.clamp(out[6], 0.0f, 1.0f);

        float yJ = MathHelper.clamp(out[7], -1.0f, 1.0f);
        float pJ = MathHelper.clamp(out[8], -1.0f, 1.0f);

        float gY = MathHelper.clamp(out[9], 0.0f, 1.0f);
        float gP = MathHelper.clamp(out[10], 0.0f, 1.0f);

        float aN = MathHelper.clamp(out[11], 0.0f, 1.0f);
        float errN = MathHelper.clamp(out[12], 0.0f, 1.0f);

        int idx = idxFor(target, attackSample);
        float c = 0.0f;
        if (idx >= 0) c = count[idx];

        float base = 1.0f - errN;
        base = MathHelper.clamp(base, 0.0f, 1.0f);

        float cGate = MathHelper.clamp(c / 34.0f, 0.0f, 1.0f);
        if (net.step() < 24) cGate *= MathHelper.clamp(net.step() / 24.0f, 0.0f, 1.0f);

        float conf = base * base * cGate;
        conf = MathHelper.clamp(conf, 0.0f, 1.0f);

        float pc = base * MathHelper.clamp(c / 50.0f, 0.0f, 1.0f);
        pc = MathHelper.clamp(pc, 0.0f, 1.0f);
        int pointConf = (int) (pc * 100.0f);

        Prediction pr = new Prediction();
        pr.yawDeltaDeg = dyN * AIM_MAX_YAW_DELTA;
        pr.pitchDeltaDeg = dpN * AIM_MAX_PITCH_DELTA;
        pr.confidence = conf;

        pr.px = px;
        pr.py = py;
        pr.pz = pz;
        pr.pointConfidence = pointConf;

        pr.yawSpeed = ySp * 240.0f;
        pr.pitchSpeed = pSp * 240.0f;

        pr.yawJitter = yJ * AIM_MAX_YAW_DELTA;
        pr.pitchJitter = pJ * AIM_MAX_PITCH_DELTA;

        if (gY <= 0.001f) pr.gcdYaw = 0.0f;
        else pr.gcdYaw = MathHelper.clamp(gY * GCD_MAX, GCD_MIN, GCD_MAX);

        if (gP <= 0.001f) pr.gcdPitch = 0.0f;
        else pr.gcdPitch = MathHelper.clamp(gP * GCD_MAX, GCD_MIN, GCD_MAX);

        pr.amp = aN * 50.0f;

        return pr;
    }

    public FallbackPrediction fallbackPredict(LivingEntity target, boolean attackSample) {
        MinecraftClient mc = MinecraftClient.getInstance();

        float dist = 3.0f;
        float sp = 0.0f;
        boolean air = false;
        int side = 1;

        if (mc.player != null && target != null) {
            dist = mc.player.distanceTo(target);
            Vec3d v = target.getVelocity();
            sp = (float) Math.sqrt(v.x * v.x + v.z * v.z);
            air = mc.player.isGliding() || target.isGliding() || !mc.player.isOnGround();
            side = sideBin(v, mc.player.getYaw());
        }

        int ab = air ? 1 : 0;
        int mode = attackSample ? 1 : 0;

        float distN = MathHelper.clamp(dist / DIST_MAX, 0.0f, 1.0f);
        float speedN = MathHelper.clamp(sp / SPEED_MAX, 0.0f, 1.0f);

        float fDb = distN * (DIST_BINS - 1);
        float fSb = speedN * (SPEED_BINS - 1);

        int db0 = MathHelper.clamp((int) Math.floor(fDb), 0, DIST_BINS - 1);
        int sb0 = MathHelper.clamp((int) Math.floor(fSb), 0, SPEED_BINS - 1);

        int db1 = Math.min(DIST_BINS - 1, db0 + 1);
        int sb1 = Math.min(SPEED_BINS - 1, sb0 + 1);

        float dFrac = MathHelper.clamp(fDb - db0, 0.0f, 1.0f);
        float sFrac = MathHelper.clamp(fSb - sb0, 0.0f, 1.0f);

        FbSample p00 = fbSample(db0, sb0, ab, mode, side);
        FbSample p10 = fbSample(db1, sb0, ab, mode, side);
        FbSample p01 = fbSample(db0, sb1, ab, mode, side);
        FbSample p11 = fbSample(db1, sb1, ab, mode, side);

        float w00 = (1.0f - dFrac) * (1.0f - sFrac);
        float w10 = dFrac * (1.0f - sFrac);
        float w01 = (1.0f - dFrac) * sFrac;
        float w11 = dFrac * sFrac;

        float yaw = p00.yaw * w00 + p10.yaw * w10 + p01.yaw * w01 + p11.yaw * w11;
        float pitch = p00.pitch * w00 + p10.pitch * w10 + p01.pitch * w01 + p11.pitch * w11;
        float err = p00.err * w00 + p10.err * w10 + p01.err * w01 + p11.err * w11;
        float c = p00.c * w00 + p10.c * w10 + p01.c * w01 + p11.c * w11;

        float conf = MathHelper.clamp((float) Math.exp(-err * 0.10f) * MathHelper.clamp(c / 18.0f, 0.0f, 1.0f), 0.0f, 1.0f);

        if (c < 1.0f) conf = 0.0f;

        FallbackPrediction out = new FallbackPrediction();
        out.yawDeltaDeg = yaw;
        out.pitchDeltaDeg = pitch;
        out.confidence = conf;
        return out;
    }

    public Vec3d pointFromPrediction(LivingEntity target, Prediction pr) {
        if (target == null || pr == null) return null;
        Box hb = target.getBoundingBox();
        double x = hb.minX + (hb.maxX - hb.minX) * pr.px;
        double y = hb.minY + (hb.maxY - hb.minY) * pr.py;
        double z = hb.minZ + (hb.maxZ - hb.minZ) * pr.pz;
        return new Vec3d(x, y, z);
    }

    public int getStepCount() {
        return this.net.step();
    }

    public AngleDeg angleDegFromRotation(Rotation rot) {
        AngleDeg a = new AngleDeg();
        if (rot == null) {
            a.yaw = 0.0f;
            a.pitch = 0.0f;
            return a;
        }
        a.yaw = rot.c();
        a.pitch = rot.d();
        return a;
    }

    public Rotation rotationFromAngleDeg(float yaw, float pitch) {
        return new Rotation(yaw, pitch);
    }

    private FbSample fbSample(int d, int s, int ab, int mb, int side) {
        int idx = idxBins(d, s, ab, mb, side);

        float y = fbYawAvg[idx];
        float p = fbPitchAvg[idx];
        float e = fbErrEma[idx];
        float c = fbCount[idx];

        FbSample out = new FbSample();
        out.yaw = y;
        out.pitch = p;
        out.err = e;
        out.c = c;
        return out;
    }

    public void load() throws Exception {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        File f = filePath(mc).toFile();
        if (!f.exists()) return;

        String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);

        int v = findJsonIntValue(json, "v", 0);

        String b64 = findJsonStringValue(json, "b64");
        if (b64 == null) b64 = findJsonStringValue(json, "data");
        if (b64 == null) b64 = findJsonStringValue(json, "blob");
        if (b64 == null || b64.isEmpty()) return;

        byte[] data;
        try {
            data = Base64.getDecoder().decode(b64);
        } catch (Exception ignored) {
            return;
        }

        if (data.length < 16) return;

        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        if (v >= 7) {
            int magic = bb.getInt();
            int ver = bb.getInt();
            if (magic != MAGIC || ver != 7) {
                return;
            }

            net.read(bb);

            lossEma = (bb.remaining() >= 4) ? bb.getFloat() : 0.0f;

            readIntArray(bb, count);

            readFloatArray(bb, fbYawAvg);
            readFloatArray(bb, fbPitchAvg);
            readFloatArray(bb, fbErrEma);
            readIntArray(bb, fbCount);
        }
    }

    public void save() throws Exception {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;

        int bytes =
                8
                        + net.bytes()
                        + 4
                        + count.length * 4
                        + (fbYawAvg.length + fbPitchAvg.length + fbErrEma.length) * 4
                        + fbCount.length * 4;

        ByteBuffer bb = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);

        bb.putInt(MAGIC);
        bb.putInt(7);

        net.write(bb);

        bb.putFloat(lossEma);

        writeIntArray(bb, count);

        writeFloatArray(bb, fbYawAvg);
        writeFloatArray(bb, fbPitchAvg);
        writeFloatArray(bb, fbErrEma);
        writeIntArray(bb, fbCount);

        byte[] data = new byte[bb.position()];
        bb.rewind();
        bb.get(data);

        String b64 = Base64.getEncoder().encodeToString(data);

        String json = "{\"v\":7,\"b64\":\"" + b64 + "\"}";

        File f = filePath(mc).toFile();
        Files.writeString(f.toPath(), json, StandardCharsets.UTF_8);
    }

    private static void readFloatArray(ByteBuffer bb, float[] arr) {
        for (int i = 0; i < arr.length; i++) {
            if (bb.remaining() < 4) return;
            arr[i] = bb.getFloat();
        }
    }

    private static void readIntArray(ByteBuffer bb, int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            if (bb.remaining() < 4) return;
            arr[i] = bb.getInt();
        }
    }

    private static void writeFloatArray(ByteBuffer bb, float[] arr) {
        for (float v : arr) bb.putFloat(v);
    }

    private static void writeIntArray(ByteBuffer bb, int[] arr) {
        for (int v : arr) bb.putInt(v);
    }

    private static String findJsonStringValue(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return null;
        int colon = json.indexOf(":", k);
        if (colon < 0) return null;
        int q1 = json.indexOf("\"", colon + 1);
        if (q1 < 0) return null;
        int q2 = json.indexOf("\"", q1 + 1);
        if (q2 < 0) return null;
        String s = json.substring(q1 + 1, q2).trim();
        return s.isEmpty() ? null : s;
    }

    private static int findJsonIntValue(String json, String key, int def) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) return def;
        int colon = json.indexOf(":", k);
        if (colon < 0) return def;
        int i = colon + 1;
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '\n' || json.charAt(i) == '\r' || json.charAt(i) == '\t')) i++;
        int s = i;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c >= '0' && c <= '9') {
                i++;
                continue;
            }
            break;
        }
        if (i <= s) return def;
        try {
            return Integer.parseInt(json.substring(s, i));
        } catch (Exception ignored) {
        }
        return def;
    }

    public static final class FbSample {
        public float yaw;
        public float pitch;
        public float err;
        public float c;
    }

    public static final class AngleDeg {
        public float yaw;
        public float pitch;
    }

    public static final class Prediction {
        public float yawDeltaDeg;
        public float pitchDeltaDeg;
        public float confidence;

        public float px;
        public float py;
        public float pz;

        public int pointConfidence;

        public float yawSpeed;
        public float pitchSpeed;
        public float yawJitter;
        public float pitchJitter;
        public float gcdYaw;
        public float gcdPitch;
        public float amp;
    }

    public static final class FallbackPrediction {
        public float yawDeltaDeg;
        public float pitchDeltaDeg;
        public float confidence;
    }

    static final class NeuralNet {
        static final float B1 = 0.90f;
        static final float B2 = 0.999f;
        static final float EPS = 1.0e-8f;

        final int in, h1, h2, out;

        final float[] w1;
        final float[] b1;
        final float[] w2;
        final float[] b2;
        final float[] w3;
        final float[] b3;

        final float[] mw1;
        final float[] vw1;
        final float[] mb1;
        final float[] vb1;

        final float[] mw2;
        final float[] vw2;
        final float[] mb2;
        final float[] vb2;

        final float[] mw3;
        final float[] vw3;
        final float[] mb3;
        final float[] vb3;

        int step = 0;
        float b1Pow = 1.0f;
        float b2Pow = 1.0f;

        final float[] z1;
        final float[] a1;
        final float[] z2;
        final float[] a2;
        final float[] z3;
        final float[] outAct;

        final float[] inTmp;
        final float[] tTmp;

        final float[] dz3;
        final float[] da2;
        final float[] dz2;
        final float[] da1;
        final float[] dz1;

        NeuralNet(int in, int h1, int h2, int out) {
            this.in = in;
            this.h1 = h1;
            this.h2 = h2;
            this.out = out;

            this.w1 = new float[h1 * in];
            this.b1 = new float[h1];
            this.w2 = new float[h2 * h1];
            this.b2 = new float[h2];
            this.w3 = new float[out * h2];
            this.b3 = new float[out];

            this.mw1 = new float[w1.length];
            this.vw1 = new float[w1.length];
            this.mb1 = new float[b1.length];
            this.vb1 = new float[b1.length];

            this.mw2 = new float[w2.length];
            this.vw2 = new float[w2.length];
            this.mb2 = new float[b2.length];
            this.vb2 = new float[b2.length];

            this.mw3 = new float[w3.length];
            this.vw3 = new float[w3.length];
            this.mb3 = new float[b3.length];
            this.vb3 = new float[b3.length];

            this.z1 = new float[h1];
            this.a1 = new float[h1];
            this.z2 = new float[h2];
            this.a2 = new float[h2];
            this.z3 = new float[out];
            this.outAct = new float[out];

            this.inTmp = new float[in];
            this.tTmp = new float[out];

            this.dz3 = new float[out];
            this.da2 = new float[h2];
            this.dz2 = new float[h2];
            this.da1 = new float[h1];
            this.dz1 = new float[h1];

            init();
        }

        int step() {
            return step;
        }

        float[] tmpIn() {
            return inTmp;
        }

        float[] tmpTarget() {
            return tTmp;
        }

        void init() {
            Random r = new Random(System.nanoTime() ^ 0x5F3759DF);
            initMat(w1, in, h1, r);
            initMat(w2, h1, h2, r);
            initMat(w3, h2, out, r);
            for (int i = 0; i < b1.length; i++) b1[i] = 0.0f;
            for (int i = 0; i < b2.length; i++) b2[i] = 0.0f;
            for (int i = 0; i < b3.length; i++) b3[i] = 0.0f;
            step = 0;
            b1Pow = 1.0f;
            b2Pow = 1.0f;
            clearOpt();
        }

        void clearOpt() {
            for (int i = 0; i < mw1.length; i++) {
                mw1[i] = 0.0f;
                vw1[i] = 0.0f;
            }
            for (int i = 0; i < mb1.length; i++) {
                mb1[i] = 0.0f;
                vb1[i] = 0.0f;
            }
            for (int i = 0; i < mw2.length; i++) {
                mw2[i] = 0.0f;
                vw2[i] = 0.0f;
            }
            for (int i = 0; i < mb2.length; i++) {
                mb2[i] = 0.0f;
                vb2[i] = 0.0f;
            }
            for (int i = 0; i < mw3.length; i++) {
                mw3[i] = 0.0f;
                vw3[i] = 0.0f;
            }
            for (int i = 0; i < mb3.length; i++) {
                mb3[i] = 0.0f;
                vb3[i] = 0.0f;
            }
        }

        static void initMat(float[] w, int fanIn, int fanOut, Random r) {
            float scale = (float) Math.sqrt(2.0 / Math.max(1, fanIn));
            for (int i = 0; i < w.length; i++) {
                float u = (r.nextFloat() * 2.0f - 1.0f);
                w[i] = u * scale;
            }
        }

        float[] forward(float[] x) {
            for (int i = 0; i < h1; i++) {
                float s = b1[i];
                int row = i * in;
                for (int j = 0; j < in; j++) s += w1[row + j] * x[j];
                z1[i] = s;
                a1[i] = (s > 0.0f) ? s : 0.0f;
            }

            for (int i = 0; i < h2; i++) {
                float s = b2[i];
                int row = i * h1;
                for (int j = 0; j < h1; j++) s += w2[row + j] * a1[j];
                z2[i] = s;
                a2[i] = (s > 0.0f) ? s : 0.0f;
            }

            for (int i = 0; i < out; i++) {
                float s = b3[i];
                int row = i * h2;
                for (int j = 0; j < h2; j++) s += w3[row + j] * a2[j];
                z3[i] = s;
            }

            outAct[0] = tanh(z3[0]);
            outAct[1] = tanh(z3[1]);

            outAct[2] = sigmoid(z3[2]);
            outAct[3] = sigmoid(z3[3]);
            outAct[4] = sigmoid(z3[4]);

            outAct[5] = sigmoid(z3[5]);
            outAct[6] = sigmoid(z3[6]);

            outAct[7] = tanh(z3[7]);
            outAct[8] = tanh(z3[8]);

            outAct[9] = sigmoid(z3[9]);
            outAct[10] = sigmoid(z3[10]);

            outAct[11] = sigmoid(z3[11]);
            outAct[12] = sigmoid(z3[12]);

            return outAct;
        }

        float train(float[] x, float[] t, float lr, float l2) {
            forward(x);

            float loss = 0.0f;

            for (int i = 0; i < out; i++) dz3[i] = 0.0f;

            loss += sqGradTanh(0, t[0], 1.00f);
            loss += sqGradTanh(1, t[1], 1.00f);

            loss += sqGradSig(2, t[2], 0.80f);
            loss += sqGradSig(3, t[3], 0.80f);
            loss += sqGradSig(4, t[4], 0.80f);

            loss += sqGradSig(5, t[5], 0.15f);
            loss += sqGradSig(6, t[6], 0.15f);

            loss += sqGradTanh(7, t[7], 0.22f);
            loss += sqGradTanh(8, t[8], 0.22f);

            loss += sqGradSig(9, t[9], 0.03f);
            loss += sqGradSig(10, t[10], 0.03f);

            loss += sqGradSig(11, t[11], 0.18f);
            loss += sqGradSig(12, t[12], 0.35f);

            for (int j = 0; j < h2; j++) da2[j] = 0.0f;
            for (int i = 0; i < out; i++) {
                int row = i * h2;
                float g = dz3[i];
                for (int j = 0; j < h2; j++) da2[j] += w3[row + j] * g;
            }

            for (int j = 0; j < h2; j++) dz2[j] = (z2[j] > 0.0f) ? da2[j] : 0.0f;

            for (int j = 0; j < h1; j++) da1[j] = 0.0f;
            for (int i = 0; i < h2; i++) {
                int row = i * h1;
                float g = dz2[i];
                for (int j = 0; j < h1; j++) da1[j] += w2[row + j] * g;
            }

            for (int j = 0; j < h1; j++) dz1[j] = (z1[j] > 0.0f) ? da1[j] : 0.0f;

            step++;
            b1Pow *= B1;
            b2Pow *= B2;

            float corr1 = 1.0f - b1Pow;
            float corr2 = 1.0f - b2Pow;
            if (corr1 < 1.0e-6f) corr1 = 1.0e-6f;
            if (corr2 < 1.0e-6f) corr2 = 1.0e-6f;

            for (int i = 0; i < out; i++) {
                int row = i * h2;
                float gb = dz3[i];
                updateAdamScalar(b3, mb3, vb3, i, gb, lr, l2, corr1, corr2);

                for (int j = 0; j < h2; j++) {
                    float g = dz3[i] * a2[j];
                    int wi = row + j;
                    updateAdamScalar(w3, mw3, vw3, wi, g, lr, l2, corr1, corr2);
                }
            }

            for (int i = 0; i < h2; i++) {
                int row = i * h1;
                float gb = dz2[i];
                updateAdamScalar(b2, mb2, vb2, i, gb, lr, l2, corr1, corr2);

                for (int j = 0; j < h1; j++) {
                    float g = dz2[i] * a1[j];
                    int wi = row + j;
                    updateAdamScalar(w2, mw2, vw2, wi, g, lr, l2, corr1, corr2);
                }
            }

            for (int i = 0; i < h1; i++) {
                int row = i * in;
                float gb = dz1[i];
                updateAdamScalar(b1, mb1, vb1, i, gb, lr, l2, corr1, corr2);

                for (int j = 0; j < in; j++) {
                    float g = dz1[i] * x[j];
                    int wi = row + j;
                    updateAdamScalar(w1, mw1, vw1, wi, g, lr, l2, corr1, corr2);
                }
            }

            return loss;
        }

        float sqGradTanh(int idx, float target, float w) {
            float y = outAct[idx];
            float e = (y - target);
            float we = e * w;
            float d = (1.0f - y * y);
            dz3[idx] = we * d;
            return we * e;
        }

        float sqGradSig(int idx, float target, float w) {
            float y = outAct[idx];
            float e = (y - target);
            float we = e * w;
            float d = y * (1.0f - y);
            dz3[idx] = we * d;
            return we * e;
        }

        static void updateAdamScalar(float[] p, float[] m, float[] v, int i, float g, float lr, float l2, float corr1, float corr2) {
            float pi = p[i];
            float grad = g + l2 * pi;

            float mi = m[i] = B1 * m[i] + (1.0f - B1) * grad;
            float vi = v[i] = B2 * v[i] + (1.0f - B2) * grad * grad;

            float mHat = mi / corr1;
            float vHat = vi / corr2;

            float upd = lr * (mHat / ((float) Math.sqrt(vHat) + EPS));
            p[i] = pi - upd;
        }

        static float tanh(float x) {
            if (x > 6.0f) return 1.0f;
            if (x < -6.0f) return -1.0f;
            return (float) Math.tanh(x);
        }

        static float sigmoid(float x) {
            if (x > 12.0f) return 0.999994f;
            if (x < -12.0f) return 0.000006f;
            return 1.0f / (1.0f + (float) Math.exp(-x));
        }

        int bytes() {
            int f = 4 * 4;
            int w = (w1.length + b1.length + w2.length + b2.length + w3.length + b3.length) * 4;
            int m = (mw1.length + vw1.length + mb1.length + vb1.length
                    + mw2.length + vw2.length + mb2.length + vb2.length
                    + mw3.length + vw3.length + mb3.length + vb3.length) * 4;
            return f + w + m;
        }

        void write(ByteBuffer bb) {
            bb.putInt(step);
            bb.putFloat(b1Pow);
            bb.putFloat(b2Pow);

            writeFloatArray(bb, w1);
            writeFloatArray(bb, b1);
            writeFloatArray(bb, w2);
            writeFloatArray(bb, b2);
            writeFloatArray(bb, w3);
            writeFloatArray(bb, b3);

            writeFloatArray(bb, mw1);
            writeFloatArray(bb, vw1);
            writeFloatArray(bb, mb1);
            writeFloatArray(bb, vb1);

            writeFloatArray(bb, mw2);
            writeFloatArray(bb, vw2);
            writeFloatArray(bb, mb2);
            writeFloatArray(bb, vb2);

            writeFloatArray(bb, mw3);
            writeFloatArray(bb, vw3);
            writeFloatArray(bb, mb3);
            writeFloatArray(bb, vb3);
        }

        void read(ByteBuffer bb) {
            if (bb.remaining() < 4) return;
            step = bb.getInt();
            if (bb.remaining() >= 8) {
                b1Pow = bb.getFloat();
                b2Pow = bb.getFloat();
            } else {
                b1Pow = 1.0f;
                b2Pow = 1.0f;
            }

            readFloatArray(bb, w1);
            readFloatArray(bb, b1);
            readFloatArray(bb, w2);
            readFloatArray(bb, b2);
            readFloatArray(bb, w3);
            readFloatArray(bb, b3);

            readFloatArray(bb, mw1);
            readFloatArray(bb, vw1);
            readFloatArray(bb, mb1);
            readFloatArray(bb, vb1);

            readFloatArray(bb, mw2);
            readFloatArray(bb, vw2);
            readFloatArray(bb, mb2);
            readFloatArray(bb, vb2);

            readFloatArray(bb, mw3);
            readFloatArray(bb, vw3);
            readFloatArray(bb, mb3);
            readFloatArray(bb, vb3);
        }
    }
}
