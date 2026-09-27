package aethereal.module.render;

import aethereal.config.ThemeInfo;
import aethereal.config.ThemeProcessor;
import aethereal.core.*;
import aethereal.core.Module;
import aethereal.event.DrawEvent;
import aethereal.module.misc.StreamerMode;
import aethereal.render.ColorUtil;
import aethereal.render.Draw2DProcessor;
import aethereal.render.Fonts;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.util.math.MatrixStack;
import aethereal.setting.BooleanSetting;
import aethereal.setting.ColorSetting;
import aethereal.setting.ModeSetting;
import aethereal.setting.MultiModeSetting;
import aethereal.util.InventoryUtil;
import aethereal.util.MathUtil;
import aethereal.util.ProjectUtil;
import aethereal.util.ServerUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleRegister(name = "ESP", description = "Отображает информацию о сущностях и боксы вокруг них", category = Category.Render)
public class ESP extends Module {
    private final MultiModeSetting trackedEntities = new MultiModeSetting("Отслеживаемые сущности", new BooleanSetting("Игроки", true), new BooleanSetting("Животные", false), new BooleanSetting("Мобы", false), new BooleanSetting("Предметы", false), new BooleanSetting("Голые", false), new BooleanSetting("Невидимые", false));
    private final ModeSetting visualMode = new ModeSetting("Тип визуализации", "Квадрат", "Квадрат", "Углы", "Заливка", "Отключен");
    private final ModeSetting colorSource = new ModeSetting("Источник цвета", "Клиентский", "Клиентский", "Статичный");
    private final ModeSetting healthBarMode = new ModeSetting("Бар здоровья", "Отключен", "Отключен", "Стандартный").a(() -> {
        return Boolean.valueOf(this.visualMode.l("Квадрат") || this.visualMode.l("Углы"));
    });
    private final ColorSetting colorSetting = new ColorSetting("Цвет визуализации", Integer.valueOf(ColorUtil.convertToARGB(255, 255, 255, 255))).a(() -> {
        return Boolean.valueOf(this.colorSource.l("Статичный"));
    });
    private final BooleanSetting showName = new BooleanSetting("Имя", true);
    private final BooleanSetting showArmor = new BooleanSetting("Броня", true);
    private final BooleanSetting showHeldItems = new BooleanSetting("Предметы в руках", true);
    private final BooleanSetting showPotions = new BooleanSetting("Зелья", true);
    
    private final List<Tracker> trackers = new ArrayList<>();
    private final List<float[]> nameTagBounds = new ArrayList<>();

    public ESP() {
        a(this.trackedEntities, this.visualMode, this.colorSource, this.healthBarMode, this.colorSetting, this.showName, this.showArmor, this.showHeldItems, this.showPotions);
    }

    public List<Tracker> getTrackers() {
        return this.trackers;
    }

    @EventTarget
    public void onDraw(DrawEvent event) {
        String str;
        if (this.visualMode.l("Заливка")) {
            if (event.c()) {
                for (Entity entity : mc.world.getEntities()) {
                    if (shouldRender(entity)) {
                        event.getDraw3DProcessor().a(event.h(), entity.getBoundingBox().offset(MathUtil.a(entity, event.g()).subtract(entity.getPos())), this.colorSource.l("Статичный") ? this.colorSetting.c().intValue() : Delta.getInstance().getModuleProcessor().o().a(ThemeInfo.PRIMARY).toIntColor(), 0.75f);
                    }
                }
                return;
            }
            return;
        }
        if (event.b()) {
            this.nameTagBounds.clear();
            if (this.visualMode.l("Квадрат") || this.visualMode.l("Углы")) {
                Draw2DProcessor draw = event.getDraw2DProcessor();
                for (Entity entity : mc.world.getEntities()) {
                    Box box = shouldRender(entity) ? entity.getBoundingBox().offset(MathUtil.a(entity, event.g()).subtract(entity.getPos())) : null;
                    float[] bounds = box == null ? null : ProjectUtil.getBounds(box);
                    if (bounds != null) {
                        LivingEntity living = entity instanceof LivingEntity ? (LivingEntity) entity : null;
                        boolean healthBar = !this.healthBarMode.l("Отключен") && living != null;
                        float percent = healthBar ? Math.min(Math.max(0.0f, ServerUtil.a.a$(living)) / Math.max(1.0f, living.getMaxHealth()), 1.0f) : 0.0f;
                        int healthColor = ColorUtil.lerpColorValue(ColorUtil.convertToARGB(255, 0, 0, 255), ColorUtil.convertToARGB(0, 255, 0, 255), percent);
                        int color = this.colorSource.l("Статичный") ? this.colorSetting.c().intValue() : ColorUtil.combineColorWithAlpha(Delta.getInstance().getModuleProcessor().o().a(ThemeInfo.PRIMARY).toIntColor(), 255);
                        drawBox(draw, event, bounds[0], bounds[1], bounds[2], bounds[3], color, this.visualMode.l("Углы"), healthBar, percent, healthColor);
                    }
                }
            }
            List<Entity> nametagEntities = new ArrayList<>();
            for (Entity entity : mc.world.getEntities()) {
                if (entity != mc.player) {
                    if (entity instanceof PlayerEntity) {
                        str = "Игроки";
                    } else if (entity instanceof HostileEntity) {
                        str = "Мобы";
                    } else if ((entity instanceof AnimalEntity) || (entity instanceof ShulkerEntity) || (entity instanceof VillagerEntity)) {
                        str = "Животные";
                    } else {
                        str = ((entity instanceof ItemEntity) || (entity instanceof ArrowEntity)) ? "Предметы" : null;
                    }
                    String key = str;
                    
                    // Сначала проверяем основную категорию
                    if (key != null && this.trackedEntities.a(key).c().booleanValue()) {
                        // Если это LivingEntity, применяем фильтры как исключения
                        if (entity instanceof LivingEntity living) {
                            boolean isNaked = isEntityNaked(living);
                            boolean isInvisible = isEntityInvisible(living);
                            boolean nakedEnabled = this.trackedEntities.a("Голые").c().booleanValue();
                            boolean invisibleEnabled = this.trackedEntities.a("Невидимые").c().booleanValue();
                            
                            // Если фильтр выключен - исключаем соответствующие сущности
                            if (!nakedEnabled && isNaked) continue;
                            if (!invisibleEnabled && isInvisible) continue;
                        }
                        nametagEntities.add(entity);
                    }
                }
            }
            if (mc.player != null) {
                nametagEntities.sort(Comparator.comparingDouble((Entity entity) -> mc.player.squaredDistanceTo(entity)));
            }
            for (Entity entity : nametagEntities) {
                renderEntity(entity, event, null);
            }
        }
    }
    
    private void renderEntity(Entity entity, DrawEvent event, String key) {
        int color = ((entity instanceof PlayerEntity) && Delta.getInstance().getModuleProcessor().e().d(entity.getName().getString())) ? ColorUtil.convertToARGB(0, 100, 0, InterfaceC0020Opcode.bN) : ColorUtil.convertToARGB(0, 0, 0, 80);
        Vec3d interpolated = MathUtil.a(entity, event.g());
        Vec3d entityPos = interpolated.add(0.0d, entity.getHeight() + 0.55f, 0.0d);
        Vector2f screenPos = ProjectUtil.project(entityPos.getX(), entityPos.getY(), entityPos.getZ());
        if (ProjectUtil.isOnScreen(screenPos)) {
            if ((entity instanceof ItemEntity) || (entity instanceof ArrowEntity)) {
                drawItem(entity, event, screenPos, 7.5f, 2.0f, color);
            } else {
                if (this.showName.c().booleanValue() || this.showArmor.c().booleanValue() || this.showHeldItems.c().booleanValue()) {
                    drawNameTag(entity, event, screenPos, 7.5f, 2.0f, color);
                }
                if (this.showPotions.c().booleanValue()) {
                    drawEffects(entity, event, ProjectUtil.project(interpolated.x, interpolated.y - 0.25d, interpolated.z), 7.5f, 2.0f, color);
                }
            }
        }
    }

    private void drawBox(Draw2DProcessor draw, DrawEvent event, float minX, float minY, float maxX, float maxY, int color, boolean corners, boolean healthBar, float healthPercent, int healthColor) {
        float width = maxX - minX;
        float height = maxY - minY;
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        float line = 0.75f;
        float outline = 1.75f;
        int outlineColor = ColorUtil.convertToARGB(0, 0, 0, 255);
        if (corners) {
            float length = Math.min(width, height) * 0.25f;
            drawLine(draw, event, minX, minY, length, 0.0f, line, outline, color, outlineColor);
            drawLine(draw, event, minX, minY, 0.0f, length, line, outline, color, outlineColor);
            drawLine(draw, event, maxX, minY, -length, 0.0f, line, outline, color, outlineColor);
            drawLine(draw, event, maxX, minY, 0.0f, length, line, outline, color, outlineColor);
            drawLine(draw, event, minX, maxY, length, 0.0f, line, outline, color, outlineColor);
            drawLine(draw, event, minX, maxY, 0.0f, -length, line, outline, color, outlineColor);
            drawLine(draw, event, maxX, maxY, -length, 0.0f, line, outline, color, outlineColor);
            drawLine(draw, event, maxX, maxY, 0.0f, -length, line, outline, color, outlineColor);
        } else {
            drawLine(draw, event, minX, minY, width, 0.0f, line, outline, color, outlineColor);
            drawLine(draw, event, minX, maxY, width, 0.0f, line, outline, color, outlineColor);
            drawLine(draw, event, minX, minY, 0.0f, height, line, outline, color, outlineColor);
            drawLine(draw, event, maxX, minY, 0.0f, height, line, outline, color, outlineColor);
        }
        if (healthBar) {
            float barX = minX - 3.0f;
            draw.a(event.h(), barX - 0.5f, minY - 0.5f, 1.75f, height + 1.0f, 0.0f, outlineColor);
            draw.a(event.h(), barX, minY + (height * (1.0f - healthPercent)), 0.75f, height * healthPercent, 0.0f, healthColor);
        }
    }

    private void drawLine(Draw2DProcessor draw, DrawEvent event, float x, float y, float lengthX, float lengthY, float line, float outline, int color, int outlineColor) {
        float left = Math.min(x, x + lengthX);
        float top = Math.min(y, y + lengthY);
        float width = lengthX == 0.0f ? line : Math.abs(lengthX);
        float height = lengthY == 0.0f ? line : Math.abs(lengthY);
        if (lengthX == 0.0f) {
            left -= line * 0.5f;
        }
        if (lengthY == 0.0f) {
            top -= line * 0.5f;
        }
        float outlineOffset = (outline - line) * 0.5f;
        draw.a(event.h(), left - outlineOffset, top - outlineOffset, width + (outlineOffset * 2.0f), height + (outlineOffset * 2.0f), 0.0f, outlineColor);
        draw.a(event.h(), left, top, width, height, 0.0f, color);
    }

    private void drawNameTag(Entity entity, DrawEvent event, Vector2f screenPos, float fontSize, float padding, int color) {
        if (!(entity instanceof PlayerEntity player) || mc.player == null) {
            return;
        }
        
        ThemeProcessor theme = Delta.getInstance().getModuleProcessor().o();
        
        // Widget-style color, but fully opaque so overlapping nametags hide each other
        int background = ColorUtil.lerpColor(
                theme.a(ThemeInfo.BACKGROUND_HUD).toIntColor(),
                theme.a(ThemeInfo.PRIMARY).toIntColor(),
                theme.a(ThemeInfo.PRIMARY).getAlphaFloat() / 6.0f);
        float alpha = 1.0f;
        int bgColor = ColorUtil.applyAlphaToColor(background, alpha);
        int glowColor = bgColor;
        float glowRadius = 8.0f;
        
        // Uniform 2x scale reduction (everything / 2)
        float scale = 0.5f;
        
        // Collect visible non-empty items (Smart Hiding): Offhand -> Helmet -> Chestplate -> Leggings -> Boots -> Mainhand
        List<ItemStack> visibleItems = new ArrayList<>();
        if (this.showHeldItems.c().booleanValue()) {
            ItemStack offhand = player.getOffHandStack();
            if (!offhand.isEmpty()) {
                visibleItems.add(offhand);
            }
        }
        if (this.showArmor.c().booleanValue()) {
            EquipmentSlot[] armorSlots = {
                    EquipmentSlot.HEAD,
                    EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS,
                    EquipmentSlot.FEET
            };
            for (EquipmentSlot slot : armorSlots) {
                ItemStack armor = player.getEquippedStack(slot);
                if (!armor.isEmpty()) {
                    visibleItems.add(armor);
                }
            }
        }
        if (this.showHeldItems.c().booleanValue()) {
            ItemStack mainhand = player.getMainHandStack();
            if (!mainhand.isEmpty()) {
                visibleItems.add(mainhand);
            }
        }
        
        boolean showTop = !visibleItems.isEmpty();
        boolean showBottom = this.showName.c().booleanValue();
        if (!showTop && !showBottom) {
            return;
        }
        
        // Top capsule sizing (scaled / 2)
        float topHeight = 22.0f * scale;
        float topRadius = 10.0f * scale;
        float itemGap = 4.0f;
        float itemPadding = 5.5f;
        float topWidth = (itemPadding * 2.0f) + (visibleItems.size() * 16.0f) + ((visibleItems.size() - 1) * itemGap);
        float drawTopWidth = topWidth * scale;
        
        // Bottom capsule sizing (scaled / 2, увеличено в 1.2 раза)
        float nameTagScale = scale * 1.2f; // 0.5f * 1.2 = 0.6f
        float bottomHeight = 17.0f * nameTagScale;
        float bottomRadius = 8.5f * nameTagScale;
        float fontSz = 8.25f;
        float textThickness = 0.0f;
        
        StreamerMode streamerMode = Delta.getInstance().getModuleProcessor().t().aE();
        String name = entity.getName().getString();
        if (streamerMode.m() && streamerMode.r().c().booleanValue()) {
            name = streamerMode.a(name);
        }
        
        int hp = (int) Math.ceil(ServerUtil.a.a$(player) > 0 ? ServerUtil.a.a$(player) : (player.getHealth() + player.getAbsorptionAmount()));
        String hpText = String.valueOf(hp);
        
        float nameWidth = Fonts.e.b(name, fontSz, textThickness);
        float hpWidth = Fonts.e.b(hpText, fontSz, textThickness);
        float textGap = 5.0f;
        float bottomPadding = 9.0f;
        float bottomWidth = (bottomPadding * 2.0f) + nameWidth + textGap + hpWidth;
        float drawBottomWidth = bottomWidth * nameTagScale;
        
        int nameColor = ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(theme.a(ThemeInfo.TEXT).toIntColor(), ColorUtil.convertToARGB(255, 255, 255, 255), 0.2f), Math.min(1.0f, alpha + 0.15f));
        int hpColor = ColorUtil.applyAlphaToColor(ColorUtil.lerpColor(theme.a(ThemeInfo.PRIMARY).toIntColor(), ColorUtil.convertToARGB(255, 255, 255, 255), 0.15f), Math.min(1.0f, alpha + 0.15f));
        
        // Calculate vertical positions (scaled / 2)
        float capsuleGap = 3.0f * scale;
        float totalHeight = 0.0f;
        if (showTop) {
            totalHeight += topHeight;
        }
        if (showBottom) {
            totalHeight += (showTop ? capsuleGap : 0.0f) + bottomHeight;
        }
        
        Box box = entity.getBoundingBox().offset(MathUtil.a(entity, event.g()).subtract(entity.getPos()));
        float[] bounds = ProjectUtil.getBounds(box);
        float centerX = (bounds != null) ? (bounds[0] + bounds[2]) / 2.0f : screenPos.x();
        float topY = (bounds != null) ? bounds[1] : screenPos.y();
        
        float startY = topY - totalHeight - (3.0f * scale);
        float layoutWidth = Math.max(showTop ? drawTopWidth : 0.0f, showBottom ? drawBottomWidth : 0.0f);
        float layoutX = centerX - (layoutWidth / 2.0f);
        if (overlapsDrawnNameTag(layoutX, startY, layoutWidth, totalHeight)) {
            return;
        }
        this.nameTagBounds.add(new float[]{layoutX, startY, layoutWidth, totalHeight});
        float currentY = startY;
        
        RenderSystem.disableDepthTest();
        
        // 1. Draw Top Capsule (Items)
        if (showTop) {
            float topX = centerX - (drawTopWidth / 2.0f);
            drawCapsuleBackground(event, topX, currentY, drawTopWidth, topHeight, topRadius, bgColor, alpha, glowColor, glowRadius);
            
            MatrixStack matrices = event.i().getMatrices();
            matrices.push();
            matrices.translate(topX, currentY, 0.0f);
            matrices.scale(scale, scale, 1.0f);
            for (int i = 0; i < visibleItems.size(); i++) {
                ItemStack stack = visibleItems.get(i);
                float itemX = itemPadding + (i * (16.0f + itemGap));
                event.getDraw3DProcessor().a(event.i(), InventoryUtil.a(stack), itemX, 3.0f, 0, alpha, 1.0f, true);
            }
            matrices.pop();
            
            currentY += topHeight + capsuleGap;
        }
        
        // 2. Draw Bottom Capsule (Name & Health)
        if (showBottom) {
            float bottomX = centerX - (drawBottomWidth / 2.0f);
            drawCapsuleBackground(event, bottomX, currentY, drawBottomWidth, bottomHeight, bottomRadius, bgColor, alpha, glowColor, glowRadius);
            
            MatrixStack matrices = event.i().getMatrices();
            matrices.push();
            matrices.translate(bottomX, currentY, 0.0f);
            matrices.scale(nameTagScale, nameTagScale, 1.0f);
            float textY = ((bottomHeight / nameTagScale) - fontSz) / 2.0f - 0.5f;
            Fonts.e.a(matrices, name, bottomPadding, textY, fontSz, nameColor, textThickness);
            Fonts.e.a(matrices, hpText, bottomPadding + nameWidth + textGap, textY, fontSz, hpColor, textThickness);
            matrices.pop();
        }
        
        RenderSystem.enableDepthTest();
    }

    private void drawCapsuleBackground(DrawEvent event, float x, float y, float width, float height, float radius, int bgColor, float alpha, int glowColor, float glowRadius) {
        event.getDraw2DProcessor().a(event.h(), x, y, width, height, radius, bgColor);
    }

    private boolean overlapsDrawnNameTag(float x, float y, float width, float height) {
        if (width <= 0.0f || height <= 0.0f) {
            return false;
        }
        for (float[] bounds : this.nameTagBounds) {
            if (x < bounds[0] + bounds[2] && x + width > bounds[0] && y < bounds[1] + bounds[3] && y + height > bounds[1]) {
                return true;
            }
        }
        return false;
    }

    private void drawEffects(Entity entity, DrawEvent event, Vector2f screenPos, float fontSize, float padding, int color) {
        List<StatusEffectInstance> effects;
        if (entity instanceof LivingEntity living) {
            List<Tracker> matchingTrackers = new ArrayList<>();
            for (int i = this.trackers.size() - 1; i >= 0; i--) {
                Tracker entry = this.trackers.get(i);
                if (entry.entityId() == living.getId()) {
                    if (living.age < entry.age()) {
                        this.trackers.remove(i);
                    } else {
                        matchingTrackers.add(entry);
                    }
                }
            }
            if (!matchingTrackers.isEmpty()) {
                effects = new ArrayList<>();
                for (Tracker tracker : matchingTrackers) {
                    for (StatusEffectInstance effectInstance : tracker.effects()) {
                        int remaining = effectInstance.getDuration() - Math.max(0, living.age - tracker.age());
                        if (remaining > 0) {
                            StatusEffectInstance remainingEffect = effectInstance.getDuration() > 1000000 ? effectInstance : new StatusEffectInstance(effectInstance.getEffectType(), remaining, effectInstance.getAmplifier());
                            StatusEffectInstance existing = null;
                            for (StatusEffectInstance instance : effects) {
                                if (instance.getEffectType().equals(effectInstance.getEffectType())) {
                                    existing = instance;
                                    break;
                                }
                            }
                            if (existing == null) {
                                effects.add(remainingEffect);
                            } else if (remainingEffect.getAmplifier() > existing.getAmplifier() || (remainingEffect.getAmplifier() == existing.getAmplifier() && remainingEffect.getDuration() > existing.getDuration())) {
                                effects.remove(existing);
                                effects.add(remainingEffect);
                            }
                        }
                    }
                }
                if (effects.isEmpty()) {
                    effects = new ArrayList<>(living.getStatusEffects());
                }
            } else {
                effects = new ArrayList<>(living.getStatusEffects());
            }
            float lineHeight = Fonts.e.d().lineHeight() * fontSize;
            float maxWidth = 0.0f;
            for (StatusEffectInstance effect : effects) {
                int seconds = effect.getDuration() / 20;
                maxWidth = Math.max(maxWidth, Fonts.e.a(Text.translatable(effect.getEffectType().value().getTranslationKey()).getString() + " " + MathUtil.a(effect.getAmplifier()) + (effect.getDuration() > 1000000 ? " ∞" : " - " + (seconds / 60) + ":" + String.format("%02d", Integer.valueOf(seconds % 60))), fontSize));
            }
            float textX = screenPos.x() - (maxWidth / 2.0f);
            float textY = screenPos.y() + padding;
            event.getDraw2DProcessor().a(event.i().getMatrices(), textX - padding, textY, maxWidth + (padding * 2.0f), effects.size() * lineHeight, 0.0f, color);
            float lineY = textY;
            for (StatusEffectInstance effect2 : effects) {
                String duration = effect2.getDuration() > 1000000 ? " ∞" : " - " + ((effect2.getDuration() / 20) / 60) + ":" + String.format("%02d", Integer.valueOf((effect2.getDuration() / 20) % 60));
                String line = Text.translatable(effect2.getEffectType().value().getTranslationKey()).getString() + " " + MathUtil.a(effect2.getAmplifier()) + duration;
                Fonts.e.a(event.i().getMatrices(), line, screenPos.x() - (Fonts.e.a(line, fontSize) / 2.0f), lineY, fontSize, ColorUtil.applyAlphaToColor(effect2.getEffectType().value().getColor(), 1.0f), 0.0f);
                lineY += lineHeight;
            }
        }
    }

    private void drawItem(Entity entity, DrawEvent event, Vector2f screenPos, float fontSize, float padding, int color) {
        MutableText text = entity instanceof ItemEntity ? ((ItemEntity) entity).getStack().getName().copy() : entity.getName().copy();
        if (entity instanceof ItemEntity item) {
            if (item.getStack().getCount() > 1) {
                text.append(Text.literal(" x" + item.getStack().getCount()));
            }
        }
        float textWidth = Fonts.e.a(text, fontSize);
        float textHeight = Fonts.e.d().lineHeight() * fontSize;
        float textX = screenPos.x() - (textWidth / 2.0f);
        float textY = screenPos.y();
        event.getDraw2DProcessor().a(event.i().getMatrices(), textX - padding, textY, textWidth + (padding * 2.0f), textHeight, 0.0f, color);
        Fonts.e.a(event.i().getMatrices(), text, textX, textY, fontSize);
    }

    private boolean shouldRender(Entity entity) {
        if ((entity instanceof PlayerEntity) || (entity instanceof ItemEntity)) {
            return entity != mc.player || !mc.options.getPerspective().isFirstPerson();
        }
        return false;
    }
    
    private boolean isEntityNaked(LivingEntity entity) {
        if (!(entity instanceof PlayerEntity player)) return false;
        
        boolean hasArmor = false;
        EquipmentSlot[] armorSlots = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        };
        for (EquipmentSlot slot : armorSlots) {
            ItemStack stack = player.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                hasArmor = true;
                break;
            }
        }
        return !hasArmor;
    }
    
    private boolean isEntityInvisible(LivingEntity entity) {
        return entity.isInvisible() || entity.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY);
    }

    public record Tracker(List<StatusEffectInstance> effects, int entityId, int age) {
    }
}
