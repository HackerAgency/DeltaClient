package aethereal.module.combat.neuro;

import aethereal.util.ChatUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

public final class NeuroAimPolicy {

    public static final int MIN_SAMPLES = 80;

    private static final int IN = 8;
    private static final int H = 16;
    private static final int OUT = 2;
    private static final float YAW_SCALE = 28.0f;
    private static final float PITCH_SCALE = 20.0f;

    private final float[] w1 = new float[IN * H];
    private final float[] b1 = new float[H];
    private final float[] w2 = new float[H * OUT];
    private final float[] b2 = new float[OUT];
    private final float[] hid = new float[H];
    private final float[] out = new float[OUT];
    private final float[] x = new float[IN];
    private final float[] dH = new float[H];

    private int samples;
    private boolean dirty;
    private boolean announced;

    public NeuroAimPolicy() {
        init();
    }

    public int samples() {
        return this.samples;
    }

    public boolean ready() {
        return this.samples >= MIN_SAMPLES;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public File file(MinecraftClient mc) {
        File dir = new File(mc.runDirectory, "delta");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, "neuro_aim_policy.json");
    }

    public void observe(LivingEntity target, float errYaw, float errPitch, boolean attack, float dYaw, float dPitch) {
        if (target == null) {
            return;
        }
        fill(target, errYaw, errPitch, attack);
        float ty = MathHelper.clamp(dYaw / YAW_SCALE, -1.0f, 1.0f);
        float tp = MathHelper.clamp(dPitch / PITCH_SCALE, -1.0f, 1.0f);
        train(ty, tp, attack ? 0.045f : 0.018f);
        this.samples++;
        this.dirty = true;
        if (!this.announced && this.samples == MIN_SAMPLES) {
            this.announced = true;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.player != null) {
                ChatUtil.sendMessage("§aНейро готова (" + this.samples + "). §7Режим Выполняющий. Файл: §f" + file(mc).getAbsolutePath());
            }
        }
    }

    public Step act(LivingEntity target, float errYaw, float errPitch, boolean attack) {
        fill(target, errYaw, errPitch, attack);
        forward();
        Step step = new Step();
        step.dy = MathHelper.clamp(this.out[0] * YAW_SCALE, -40.0f, 40.0f);
        step.dp = MathHelper.clamp(this.out[1] * PITCH_SCALE, -30.0f, 30.0f);
        return step;
    }

    public void save(MinecraftClient mc) {
        if (mc == null) {
            return;
        }
        try {
            int floats = this.w1.length + this.b1.length + this.w2.length + this.b2.length;
            ByteBuffer bb = ByteBuffer.allocate(16 + floats * 4).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt(IN);
            bb.putInt(H);
            bb.putInt(OUT);
            bb.putInt(this.samples);
            for (float v : this.w1) bb.putFloat(v);
            for (float v : this.b1) bb.putFloat(v);
            for (float v : this.w2) bb.putFloat(v);
            for (float v : this.b2) bb.putFloat(v);
            String json = "{\"v\":1,\"b64\":\"" + Base64.getEncoder().encodeToString(bb.array()) + "\"}";
            Files.writeString(file(mc).toPath(), json, StandardCharsets.UTF_8);
            this.dirty = false;
        } catch (Exception ignored) {
        }
    }

    public void load(MinecraftClient mc) {
        if (mc == null) {
            return;
        }
        try {
            File f = file(mc);
            if (!f.exists()) {
                return;
            }
            String json = Files.readString(f.toPath(), StandardCharsets.UTF_8);
            int b0 = json.indexOf("\"b64\":\"");
            if (b0 < 0) {
                return;
            }
            int s = b0 + 7;
            int e = json.indexOf('"', s);
            if (e <= s) {
                return;
            }
            ByteBuffer bb = ByteBuffer.wrap(Base64.getDecoder().decode(json.substring(s, e))).order(ByteOrder.LITTLE_ENDIAN);
            if (bb.getInt() != IN || bb.getInt() != H || bb.getInt() != OUT) {
                return;
            }
            this.samples = bb.getInt();
            for (int i = 0; i < this.w1.length; i++) this.w1[i] = bb.getFloat();
            for (int i = 0; i < this.b1.length; i++) this.b1[i] = bb.getFloat();
            for (int i = 0; i < this.w2.length; i++) this.w2[i] = bb.getFloat();
            for (int i = 0; i < this.b2.length; i++) this.b2[i] = bb.getFloat();
            this.announced = this.samples >= MIN_SAMPLES;
            this.dirty = false;
        } catch (Exception ignored) {
        }
    }

    private void init() {
        java.util.Random rnd = new java.util.Random(0xA17);
        float s1 = (float) Math.sqrt(2.0 / IN);
        for (int i = 0; i < this.w1.length; i++) this.w1[i] = (float) rnd.nextGaussian() * s1 * 0.5f;
        float s2 = (float) Math.sqrt(2.0 / H);
        for (int i = 0; i < this.w2.length; i++) this.w2[i] = (float) rnd.nextGaussian() * s2 * 0.5f;
    }

    private void fill(LivingEntity target, float errYaw, float errPitch, boolean attack) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float dist = 3.0f;
        float speed = 0.0f;
        float rel = 0.0f;
        boolean air = false;
        int side = 1;
        if (mc.player != null && target != null) {
            dist = mc.player.distanceTo(target);
            Vec3d v = target.getVelocity();
            speed = (float) Math.sqrt(v.x * v.x + v.z * v.z);
            rel = (float) (target.getEyeY() - mc.player.getEyeY());
            air = mc.player.isGliding() || target.isGliding() || !mc.player.isOnGround();
            side = sideBin(v, mc.player.getYaw());
        }
        this.x[0] = MathHelper.clamp(errYaw / 80.0f, -1.0f, 1.0f);
        this.x[1] = MathHelper.clamp(errPitch / 50.0f, -1.0f, 1.0f);
        this.x[2] = MathHelper.clamp(dist / 6.0f, 0.0f, 1.0f);
        this.x[3] = MathHelper.clamp(speed / 0.8f, 0.0f, 1.0f);
        this.x[4] = air ? 1.0f : 0.0f;
        this.x[5] = attack ? 1.0f : 0.0f;
        this.x[6] = side == 0 ? -1.0f : (side == 2 ? 1.0f : 0.0f);
        this.x[7] = MathHelper.clamp(rel / 3.0f, -1.0f, 1.0f);
    }

    private void forward() {
        for (int j = 0; j < H; j++) {
            float sum = this.b1[j];
            for (int i = 0; i < IN; i++) sum += this.x[i] * this.w1[i * H + j];
            this.hid[j] = tanh(sum);
        }
        for (int k = 0; k < OUT; k++) {
            float sum = this.b2[k];
            for (int j = 0; j < H; j++) sum += this.hid[j] * this.w2[j * OUT + k];
            this.out[k] = tanh(sum);
        }
    }

    private void train(float ty, float tp, float lr) {
        forward();
        float[] tgt = { ty, tp };
        float[] dO = new float[OUT];
        for (int k = 0; k < OUT; k++) {
            float e = this.out[k] - tgt[k];
            dO[k] = e * (1.0f - this.out[k] * this.out[k]);
        }
        for (int j = 0; j < H; j++) {
            float sum = 0.0f;
            for (int k = 0; k < OUT; k++) sum += dO[k] * this.w2[j * OUT + k];
            this.dH[j] = (1.0f - this.hid[j] * this.hid[j]) * sum;
        }
        for (int j = 0; j < H; j++) {
            for (int k = 0; k < OUT; k++) this.w2[j * OUT + k] -= lr * dO[k] * this.hid[j];
        }
        for (int k = 0; k < OUT; k++) this.b2[k] -= lr * dO[k];
        for (int i = 0; i < IN; i++) {
            for (int j = 0; j < H; j++) this.w1[i * H + j] -= lr * this.dH[j] * this.x[i];
        }
        for (int j = 0; j < H; j++) this.b1[j] -= lr * this.dH[j];
    }

    private static int sideBin(Vec3d tv, float yawDeg) {
        if (tv == null) return 1;
        double sp = Math.sqrt(tv.x * tv.x + tv.z * tv.z);
        if (sp < 0.02) return 1;
        double yaw = Math.toRadians(yawDeg);
        double s = tv.x * Math.cos(yaw) + tv.z * Math.sin(yaw);
        if (s > 0.03) return 2;
        if (s < -0.03) return 0;
        return 1;
    }

    private static float tanh(float v) {
        return (float) Math.tanh(v);
    }

    public static final class Step {
        public float dy;
        public float dp;
    }
}
