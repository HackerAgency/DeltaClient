package aethereal.ui;
import aethereal.render.AnimatedFloat;
import aethereal.module.AttackAuraModule;
import aethereal.ui.setting.BooleanSetting;
import aethereal.resource.ClasspathResource;
import aethereal.render.ColorStack;
import aethereal.model.DragRenderContext;
import aethereal.util.DrawEngine;
import aethereal.math.Easings;
import aethereal.Expensive;
import aethereal.render.Fonts;
import aethereal.render.GlTexture;
import aethereal.type.ItemSpriteManager;
import aethereal.util.IteratorUtil;
import aethereal.Lang;
import aethereal.util.MathUtil;
import aethereal.type.Mc;
import aethereal.model.MouseButtonInput2;
import aethereal.model.MouseMoveInput;
import aethereal.render.MsdfFont;
import aethereal.module.NameProtectModule;
import aethereal.util.ScoreboardHelper;
import aethereal.util.ServerUtil;
import aethereal.util.StencilBufferUtil;
import aethereal.render.ThemePalette;
import aethereal.render.ToggleAnimator;
import aethereal.model.UvBounds;
import aethereal.util.WeightedEngine;
import aethereal.model.WidgetBounds;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class TargetHudWidget extends Draggable {
    public float width;
    public float height;
    public final AnimatedFloat fadeAnimation;
    public final ToggleAnimator toggleAnimatorA;
    public final ToggleAnimator toggleAnimatorB;
    public final AnimatedFloat healthAnimation;
    public final AnimatedFloat absorptionAnimation;
    public final GlTexture backgroundTexture;
    public final GlTexture heartTexture;
    public final GlTexture gappleTexture;
    public final MsdfFont nameFont;
    public final MsdfFont valueFont;
    public LivingEntity target;

    public final WidgetBounds avatarBounds;
    public final WidgetBounds contentBounds;
    public final WidgetBounds itemsBounds;
    public final WidgetBounds nameBounds;
    static final float padding = 10.0f;
    static final float cornerRadius = 8.0f;
    static final float itemSize = 12.0f;
    static final float itemSpacing = 3.0f;
    static final int iconSize = 10;
    static final float halfDivisor = 2.0f;
    static final float sectionGap = 4.0f;
    static final float minWidth = 180.0f;
    public final BooleanSetting showInRaycast;
    static final int spacingTwo = 2;

    static final double roundIncrement = 0.5d;

    static final double raycastExpand = 0.7d;

    public TargetHudWidget(BooleanSupplier booleanSupplier) {
        super("TargetHud", booleanSupplier);
        this.width = 200.0f;
        this.height = 70.0f;
        this.fadeAnimation = new AnimatedFloat(200, Easings.LINEAR);
        this.toggleAnimatorA = new ToggleAnimator(200, Easings.EASE_IN_OUT_CUBIC);
        this.toggleAnimatorB = new ToggleAnimator(200, Easings.EASE_IN_OUT_CUBIC);
        this.healthAnimation = new AnimatedFloat(160, Easings.LINEAR);
        this.absorptionAnimation = new AnimatedFloat(180, Easings.LINEAR);
        this.backgroundTexture = new GlTexture(new ClasspathResource("/textures/hud_background.png"));
        this.heartTexture = new GlTexture(new ClasspathResource("/icons/menu/new/heartpulse.png"));
        this.gappleTexture = new GlTexture(new ClasspathResource("/icons/menu/new/gapple.png"));
        this.nameFont = Fonts.INTER_SEMIBOLD.get();
        this.valueFont = Fonts.INTER_BOLD.get();
        this.avatarBounds = new WidgetBounds(0.0f, 0.0f, 50.0f, 50.0f);
        this.contentBounds = new WidgetBounds(0.0f, 0.0f, 0.0f, 50.0f);
        this.itemsBounds = new WidgetBounds(0.0f, 0.0f, 0.0f, 0.0f);
        this.nameBounds = new WidgetBounds(0.0f, 0.0f, 0.0f, 0.0f);
        this.showInRaycast = new BooleanSetting(Lang.WIDGET_TARGETHUD_SHOW_IN_RAYCAST);
        addSettings(this.showInRaycast);
        this.x = 604.0f;
        this.y = 469.0f;
    }

    @Override
    public void layout(DragRenderContext class809Var) {
        float fMax;
        if (isVisible()) {
            List<ItemStack> listMethod009 = getEquipment(this.target);
            boolean z = !listMethod009.isEmpty();
            float f = z ? 70.0f : 50.0f;
            if (f != this.height) {
                this.y -= (f - this.height) / halfDivisor;
                this.height = f;
            }
            float fMax2 = Math.max(padding, this.height - 20.0f);
            this.avatarBounds.withSize(fMax2, fMax2).withPosition(this.x + padding, (this.y + (this.height / halfDivisor)) - (fMax2 / halfDivisor));
            this.contentBounds.withPosition(this.avatarBounds.right() + cornerRadius, this.avatarBounds.y()).withSize(((width() - 20.0f) - this.avatarBounds.width()) - cornerRadius, this.avatarBounds.height());
            float size = z ? (listMethod009.size() * itemSize) + ((listMethod009.size() - 1) * itemSpacing) : 0.0f;
            float fMin = Math.min(this.nameFont.getWidth(getDisplayName(this.target), 13.0f), Math.max(0.0f, (160.0f - fMax2) - cornerRadius));
            float fMax3 = Math.max(this.valueFont.getWidth("20.0", padding), this.valueFont.getWidth("Unknown", padding));
            float width = this.valueFont.getWidth("20.0", padding);
            float f2 = fMax3 + halfDivisor + padding;
            float f3 = width + halfDivisor + padding;
            boolean zMethod012 = shouldShowAbsorption(this.target);
            if (z) {
                fMax = Math.max(f2, f3);
            } else {
                fMax = f2 + (zMethod012 ? sectionGap + f3 : 0.0f);
            }
            float fWidth = padding + this.avatarBounds.width() + cornerRadius + Math.max(size, fMin) + cornerRadius + fMax + padding;
            this.width = z ? Math.max(minWidth, fWidth) : fWidth;
        }
    }

    @Override
    public void render(DragRenderContext class809Var) {
        if (isVisible()) {
            float fAnimatedValue = this.fadeAnimation.animatedValue();
            if (this.fadeAnimation.isZero() || this.target == null) {
                return;
            }
            DrawEngine class154VarDrawEngine = class809Var.drawEngine();
            MatrixStack matrixStack = class809Var.matrixStack();
            ColorStack class115VarColorStack = class154VarDrawEngine.colorStack();
            ThemePalette class764VarPalette = class809Var.theme().palette();
            List<ItemStack> listMethod009 = getEquipment(this.target);
            boolean z = !listMethod009.isEmpty();
            class115VarColorStack.push();
            class115VarColorStack.alpha(fAnimatedValue);
            int iComputeColor = class115VarColorStack.computeColor(class764VarPalette.surfaceBackground().tone(801).argb());
            int iComputeColor2 = class115VarColorStack.computeColor(class764VarPalette.surfaceBackground().tone(900).argb());
            class154VarDrawEngine.roundedRectangle(matrixStack.peek().getPositionMatrix(), this.x, this.y, width(), height(), cornerRadius, 2.5f, class115VarColorStack.computeColor(class764VarPalette.surfaceOutline().tone(600).argb()), iComputeColor2, iComputeColor2, iComputeColor, iComputeColor);
            class154VarDrawEngine.texture(matrixStack.peek().getPositionMatrix(), this.x, this.y + (z ? 15.0f : padding), width(), Math.max(0.0f, height() - (z ? 40.0f : 20.0f)), class154VarDrawEngine.bindTexture(this.backgroundTexture.textureWithSTB()), class115VarColorStack.white());
            renderAvatar(class154VarDrawEngine, matrixStack.peek().getPositionMatrix(), class115VarColorStack, this.avatarBounds.x(), this.avatarBounds.y(), this.avatarBounds.width(), this.avatarBounds.height(), z ? cornerRadius : 6.0f);
            if (z) {
                float fX = this.contentBounds.x();
                float fY = this.contentBounds.y();
                for (ItemStack itemStack : listMethod009) {
                    if (!itemStack.isEmpty()) {
                        class154VarDrawEngine.itemStack(matrixStack.peek().getPositionMatrix(), itemStack, fX, fY, 0.375f, 1.0f);
                        fX += 15.0f;
                    }
                }
                this.itemsBounds.withSize((listMethod009.size() * itemSize) + ((listMethod009.size() - 1) * itemSpacing), itemSize).withPosition(this.contentBounds.x(), this.contentBounds.y());
            } else {
                this.itemsBounds.withSize(0.0f, 0.0f).withPosition(this.contentBounds.x(), this.contentBounds.y());
            }
            String strMethod008 = getDisplayName(this.target);
            float fMax = Math.max(this.valueFont.getWidth("20.0", padding), this.valueFont.getWidth("Unknown", padding));
            float width = this.valueFont.getWidth("20.0", padding);
            float f = fMax + halfDivisor + padding;
            float f2 = width + halfDivisor + padding;
            float fWidth = this.contentBounds.width() - (z ? Math.max(f, f2) : f + (shouldShowAbsorption(this.target) ? sectionGap + f2 : 0.0f));
            float fMin = Math.min(this.nameFont.getWidth(strMethod008, 13.0f), fWidth);
            if (z) {
                this.nameBounds.withPosition(this.contentBounds.x(), this.itemsBounds.bottom() + halfDivisor).withSize(fMin, this.nameFont.getHeight(13.0f));
            } else {
                this.nameBounds.withPosition(this.contentBounds.x(), this.contentBounds.y()).withSize(fMin, this.nameFont.getHeight(13.0f));
            }
            int iComputeColor3 = class115VarColorStack.computeColor(class764VarPalette.text().tone(50).argb());
            if (this.nameFont.getWidth(strMethod008, 13.0f) > fWidth) {
                class154VarDrawEngine.msdfFontWithHorizontalGradient(matrixStack.peek().getPositionMatrix(), this.nameFont, truncateToWidth(this.nameFont, strMethod008, 13, fWidth), this.nameBounds.x(), this.nameBounds.y(), 13.0f, 0.05f, iComputeColor3, iComputeColor3 & 16777215);
            } else {
                class154VarDrawEngine.msdfFont(matrixStack.peek().getPositionMatrix(), this.nameFont, strMethod008, this.nameBounds.x(), this.nameBounds.y(), 13.0f, 0.05f, iComputeColor3);
            }
            float healthBelowName = ScoreboardHelper.INSTANCE.getHealthBelowName(this.target);
            float fMethod007 = getEffectiveMaxHealth(this.target);
            float f3 = fMethod007 > 0.0f ? healthBelowName / fMethod007 : 0.0f;
            this.absorptionAnimation.destination(MathUtil.clamp(this.target.getAbsorptionAmount() / 20.0f, 0.0f, 1.0f));
            this.healthAnimation.destination(MathUtil.clamp(f3, 0.0f, 1.0f));
            boolean zMethod012 = shouldShowAbsorption(this.target);
            float fX2 = this.contentBounds.x();
            float fWidth2 = this.contentBounds.width() - sectionGap;
            float fBottom = z ? (this.contentBounds.bottom() - 7.0f) - itemSpacing : (this.contentBounds.bottom() - itemSpacing) - itemSpacing;
            class154VarDrawEngine.roundedRectangle(matrixStack.peek().getPositionMatrix(), fX2, fBottom, fWidth2, itemSpacing, halfDivisor, class115VarColorStack.computeColor(class764VarPalette.surfaceBackground().tone(500).argb()));
            class154VarDrawEngine.roundedRectangle(matrixStack.peek().getPositionMatrix(), fX2, fBottom, fWidth2 * this.healthAnimation.animatedValue(), itemSpacing, halfDivisor, class115VarColorStack.computeColor(class764VarPalette.accent().argb()));
            if (zMethod012) {
                class154VarDrawEngine.roundedRectangle(matrixStack.peek().getPositionMatrix(), fX2, fBottom, fWidth2 * this.absorptionAnimation.animatedValue(), itemSpacing, halfDivisor, class115VarColorStack.computeColor(ThemePalette.darkGolden.argb()));
            }
            boolean z2 = healthBelowName > this.target.getMaxHealth() + 20.0f;
            float fAnimatedValue2 = this.healthAnimation.animatedValue() * getEffectiveMaxHealth(this.target);
            float fAnimatedValue3 = this.absorptionAnimation.animatedValue() * (this.target.getMaxAbsorption() != 0.0f ? this.target.getMaxAbsorption() : 20.0f);
            String strMethod002 = formatValue(fAnimatedValue2, z2);
            String strMethod003 = (!zMethod012 || fAnimatedValue3 <= 0.0f) ? "" : formatValue(fAnimatedValue3, false);
            if (z) {
                float fY2 = zMethod012 ? this.contentBounds.y() : (((this.contentBounds.bottom() - 7.0f) - itemSpacing) - cornerRadius) - this.valueFont.getHeight(padding);
                float fX3 = (this.contentBounds.x() + this.contentBounds.width()) - this.valueFont.getWidth(strMethod002, padding);
                class154VarDrawEngine.msdfFont(matrixStack.peek().getPositionMatrix(), this.valueFont, strMethod002, fX3, fY2, padding, 0.05f, class115VarColorStack.computeColor(class764VarPalette.accent().argb()));
                class154VarDrawEngine.textureVerticalC(matrixStack.peek().getPositionMatrix(), this.heartTexture, (fX3 - halfDivisor) - padding, fY2 + (this.valueFont.getHeight(padding) / halfDivisor), iconSize, iconSize, class115VarColorStack.computeColor(class764VarPalette.accent().argb()));
                if (!strMethod003.isEmpty()) {
                    float fBottom2 = (((this.contentBounds.bottom() - 7.0f) - itemSpacing) - cornerRadius) - this.valueFont.getHeight(padding);
                    float fX4 = (this.contentBounds.x() + this.contentBounds.width()) - this.valueFont.getWidth(strMethod003, padding);
                    class154VarDrawEngine.msdfFont(matrixStack.peek().getPositionMatrix(), this.valueFont, strMethod003, fX4, fBottom2, padding, 0.05f, class115VarColorStack.computeColor(ThemePalette.darkGolden.argb()));
                    class154VarDrawEngine.textureVerticalC(matrixStack.peek().getPositionMatrix(), this.gappleTexture, (fX4 - halfDivisor) - padding, fBottom2 + (this.valueFont.getHeight(padding) / halfDivisor), iconSize, iconSize, class115VarColorStack.white());
                }
            } else {
                float fBottom3 = this.nameBounds.bottom() - this.valueFont.getHeight(padding);
                float f4 = fX2 + fWidth2;
                float width2 = this.valueFont.getWidth(strMethod002, padding);
                float f5 = width2 + halfDivisor + padding;
                if (strMethod003.isEmpty()) {
                    class154VarDrawEngine.msdfFont(matrixStack.peek().getPositionMatrix(), this.valueFont, strMethod002, f4 - width2, fBottom3, padding, 0.05f, class115VarColorStack.computeColor(class764VarPalette.accent().argb()));
                    class154VarDrawEngine.textureVerticalC(matrixStack.peek().getPositionMatrix(), this.heartTexture, ((f4 - width2) - halfDivisor) - padding, fBottom3 + (this.valueFont.getHeight(padding) / halfDivisor), iconSize, iconSize, class115VarColorStack.computeColor(class764VarPalette.accent().argb()));
                } else {
                    float width3 = this.valueFont.getWidth(strMethod003, padding);
                    float f6 = f4 - ((width3 + halfDivisor) + padding);
                    class154VarDrawEngine.msdfFont(matrixStack.peek().getPositionMatrix(), this.valueFont, strMethod003, f4 - width3, fBottom3, padding, 0.05f, class115VarColorStack.computeColor(ThemePalette.darkGolden.argb()));
                    class154VarDrawEngine.textureVerticalC(matrixStack.peek().getPositionMatrix(), this.gappleTexture, ((f4 - width3) - halfDivisor) - padding, fBottom3 + (this.valueFont.getHeight(padding) / halfDivisor), iconSize, iconSize, class115VarColorStack.white());
                    float f7 = f6 - sectionGap;
                    class154VarDrawEngine.msdfFont(matrixStack.peek().getPositionMatrix(), this.valueFont, strMethod002, f7 - width2, fBottom3, padding, 0.05f, class115VarColorStack.computeColor(class764VarPalette.accent().argb()));
                    class154VarDrawEngine.textureVerticalC(matrixStack.peek().getPositionMatrix(), this.heartTexture, ((f7 - width2) - halfDivisor) - padding, fBottom3 + (this.valueFont.getHeight(padding) / halfDivisor), iconSize, iconSize, class115VarColorStack.computeColor(class764VarPalette.accent().argb()));
                }
            }
            class115VarColorStack.pop();
        }
    }

    @Override
    public boolean click(MouseButtonInput2 class807Var, boolean z) {
        return false;
    }

    @Override
    public boolean cursor(MouseMoveInput class808Var, boolean z) {
        return z && !class808Var.intercepted();
    }

    @Override
    public void animate(WeightedEngine class141Var) {
        this.healthAnimation.animate(class141Var);
        this.absorptionAnimation.animate(class141Var);
        this.fadeAnimation.animate(class141Var);
        this.toggleAnimatorA.animate(class141Var);
        this.toggleAnimatorB.animate(class141Var);
    }

    public void updateTarget() {
        LivingEntity livingEntityMethod001 = findTarget();
        if (livingEntityMethod001 == null) {
            if (this.target != null) {
                this.fadeAnimation.destination(0.0f);
            }
            if (this.fadeAnimation.isZero()) {
                this.target = null;
                this.healthAnimation.set(0.0f);
                this.absorptionAnimation.set(0.0f);
                return;
            }
            return;
        }
        if (!Objects.equals(this.target, livingEntityMethod001)) {
            this.target = livingEntityMethod001;
            float healthBelowName = ScoreboardHelper.INSTANCE.getHealthBelowName(livingEntityMethod001);
            float fMethod007 = getEffectiveMaxHealth(livingEntityMethod001);
            this.healthAnimation.set(MathUtil.clamp(fMethod007 > 0.0f ? healthBelowName / fMethod007 : 0.0f, 0.0f, 1.0f));
            this.absorptionAnimation.set(MathUtil.clamp(livingEntityMethod001.getAbsorptionAmount() / 20.0f, 0.0f, 1.0f));
        }
        this.fadeAnimation.destination(1.0f);
    }

    public void renderAvatar(DrawEngine class154Var, Matrix4f matrix4f, ColorStack class115Var, float f, float f2, float f3, float f4, float f5) {
        Mc class815Var = Mc.INSTANCE;
        if (class815Var.getEntityRenderDispatcher().getRenderer(this.target) instanceof LivingEntityRenderer renderer ) {
            LivingEntityRenderer livingEntityRenderer = renderer;
            float tickDelta = class815Var.getRenderTickCounter().getTickDelta(false);
            AbstractTexture texture = (AbstractTexture) (class815Var.getTextureManager().getTexture(livingEntityRenderer.getTexture((net.minecraft.client.render.entity.state.LivingEntityRenderState) livingEntityRenderer.getAndUpdateRenderState(this.target, tickDelta))));
            ItemSpriteManager class219Var = ItemSpriteManager.INSTANCE;
            GlTexture orCreateTexture = class219Var.getOrCreateTexture(texture);
            UvBounds faceUV = class219Var.getFaceUV(livingEntityRenderer);
            int i = this.target.hurtTime;
            float f6 = 1.0f - ((i - (i != 0 ? tickDelta : 0.0f)) / padding);
            int iComputeColor = class115Var.computeColor(this.fadeAnimation.animatedValue(), StencilBufferUtil.STENCIL_MASK, (int) (100.0f + (155.0f * f6)), (int) (100.0f + (155.0f * f6)));
            class154Var.roundedTexture(matrix4f, f, f2, f3, f4, f5, f5, f5, f5, faceUV != null ? faceUV.u0() : 0.125f, faceUV != null ? faceUV.v0() : 0.125f, faceUV != null ? faceUV.u1() : 0.25f, faceUV != null ? faceUV.v1() : 0.25f, class154Var.bindTexture(orCreateTexture.textureWithSTB()), iComputeColor, iComputeColor, iComputeColor, iComputeColor);
            int iComputeColor2 = class115Var.computeColor(395026, 0.0f);
            int iComputeColor3 = class115Var.computeColor(395026, 0.5f);
            class154Var.roundedRectangle(matrix4f, f, f2, f3, f4, cornerRadius, iComputeColor3, iComputeColor3, iComputeColor2, iComputeColor2);
        }
    }

    public LivingEntity findTarget() {
        LivingEntity livingEntityMethod005;
        Mc class815Var = Mc.INSTANCE;
        AttackAuraModule class878Var = (AttackAuraModule) Expensive.INSTANCE.moduleRepository().get(AttackAuraModule.class);
        if (class878Var.isState() && class878Var.target() != null && !class878Var.getName().isEmpty()) {
            return class878Var.target();
        }
        if (this.showInRaycast.isValue() && (livingEntityMethod005 = raycastTarget(class815Var)) != null && !livingEntityMethod005.getName().getString().isEmpty()) {
            return livingEntityMethod005;
        }
        if (!(class815Var.getCurrentScreen() instanceof ChatScreen) || getDisplayName(class815Var.getPlayer()).isEmpty()) {
            return null;
        }
        return class815Var.getPlayer();
    }

    @Override
    public void update() {
        if (isVisible()) {
            updateTarget();
        }
    }

    @Override
    public float width() {
        return this.width;
    }

    @Override
    public float height() {
        return this.height;
    }

    public static float getEffectiveMaxHealth(LivingEntity livingEntity) {
        float maxHealth = livingEntity.getMaxHealth();
        float healthBelowName = ScoreboardHelper.INSTANCE.getHealthBelowName(livingEntity);
        return (healthBelowName > (maxHealth + 20.0f) ? 1 : (healthBelowName == (maxHealth + 20.0f) ? 0 : -1)) > 0 ? maxHealth : Math.max(maxHealth, healthBelowName);
    }

    public static boolean shouldShowAbsorption(LivingEntity livingEntity) {
        return livingEntity != null && !ServerUtil.isConnectedToServer("funtime") && livingEntity.getAbsorptionAmount() > 0.0f && livingEntity.getMaxAbsorption() <= 20.0f;
    }

    public static String formatValue(float f, boolean z) {
        return z ? "Unknown" : String.valueOf(MathUtil.round(f, roundIncrement));
    }

    public static List<ItemStack> getEquipment(LivingEntity livingEntity) {
        if (livingEntity == null) {
            return Collections.emptyList();
        }
        List<ItemStack> list = IteratorUtil.toList(livingEntity.getAllArmorItems().iterator());
        Collections.reverse(list);
        list.removeIf((v0) -> {
            return v0.isEmpty();
        });
        ItemStack mainHandStack = livingEntity.getMainHandStack();
        ItemStack offHandStack = livingEntity.getOffHandStack();
        if (!mainHandStack.isEmpty()) {
            list.add(mainHandStack);
        }
        if (!offHandStack.isEmpty()) {
            list.add(offHandStack);
        }
        return list;
    }

    public static String truncateToWidth(MsdfFont class161Var, String str, int i, float f) {
        if (str == null || str.isEmpty()) {
            return "";
        }
        if (class161Var.getWidth(str, i) <= f) {
            return str;
        }
        int i2 = 0;
        int length = str.length();
        int i3 = 0;
        while (i2 <= length) {
            int i4 = (i2 + length) >>> 1;
            if (class161Var.getWidth(str.substring(0, i4), i) <= f) {
                i3 = i4;
                i2 = i4 + 1;
            } else {
                length = i4 - 1;
            }
        }
        return i3 <= 0 ? "" : str.substring(0, i3);
    }

    public String getDisplayName(LivingEntity livingEntity) {
        if (livingEntity == null) {
            return "";
        }
        String strStripTextFormat = StringHelper.stripTextFormat(livingEntity.getName().getString());
        NameProtectModule class512Var = (NameProtectModule) Expensive.INSTANCE.moduleRepository().get(NameProtectModule.class);
        return class512Var.isState() ? class512Var.replace(strStripTextFormat) : strStripTextFormat;
    }

    public LivingEntity raycastTarget(Mc class815Var) {
        ClientPlayerEntity player = class815Var.getPlayer();
        Entity cameraEntity = class815Var.getCameraEntity();
        if (player == null || cameraEntity == null || class815Var.getWorld() == null) {
            return null;
        }
        double entityInteractionRange = player.getEntityInteractionRange();
        Vec3d cameraPosVec = cameraEntity.getCameraPosVec(1.0f);
        Vec3d rotationVec = cameraEntity.getRotationVec(1.0f);
        Vec3d vec3dAdd = cameraPosVec.add(rotationVec.multiply(entityInteractionRange));
        List<LivingEntity> entitiesByClass = class815Var.getWorld().getEntitiesByClass(LivingEntity.class, cameraEntity.getBoundingBox().stretch(rotationVec.multiply(entityInteractionRange)).expand(1.0d), livingEntity -> {
            return (livingEntity == player || !livingEntity.isAlive() || livingEntity.isSpectator()) ? false : true;
        });
        LivingEntity livingEntityMethod010 = raycastSingle(this.target, raycastExpand, cameraPosVec, vec3dAdd);
        if (livingEntityMethod010 != null) {
            return livingEntityMethod010;
        }
        if (entitiesByClass.size() <= 2) {
            return findClosestRaycast(entitiesByClass, roundIncrement, cameraPosVec, vec3dAdd);
        }
        return null;
    }

    public LivingEntity findClosestRaycast(List<LivingEntity> list, double d, Vec3d vec3d, Vec3d vec3d2) {
        LivingEntity livingEntity = null;
        double d2 = Double.MAX_VALUE;
        for (LivingEntity livingEntity2 : list) {
            Optional optionalRaycast = livingEntity2.getBoundingBox().expand(d).raycast(vec3d, vec3d2);
            if (!optionalRaycast.isEmpty()) {
                double dSquaredDistanceTo = vec3d.squaredDistanceTo((Vec3d) optionalRaycast.get());
                if (dSquaredDistanceTo < d2) {
                    d2 = dSquaredDistanceTo;
                    livingEntity = livingEntity2;
                }
            }
        }
        return livingEntity;
    }

    public LivingEntity raycastSingle(LivingEntity livingEntity, double d, Vec3d vec3d, Vec3d vec3d2) {
        if (livingEntity != null && livingEntity.isAlive() && livingEntity.getBoundingBox().expand(d).raycast(vec3d, vec3d2).isPresent()) {
            return livingEntity;
        }
        return null;
    }
}
