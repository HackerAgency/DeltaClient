package ru.expensive.mixin;

import aethereal.event.PerspectiveEvent;
import aethereal.Expensive;
import aethereal.event.CrosshairRenderEvent;
import aethereal.event.RenderOverlayEvent;
import aethereal.type.RenderOverlayType;
import aethereal.event.Render2DEvent;
import aethereal.type.Render2DStage;
import aethereal.event.StatusEffectOverlayEvent;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreboardObjective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({InGameHud.class})
public abstract class InGameHudMixin {

    @Shadow
    @Final
    private MinecraftClient field_2035;

    @ModifyExpressionValue(method = {"tick()V"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerInventory;getMainHandStack()Lnet/minecraft/item/ItemStack;")})
    private ItemStack injectSilent(ItemStack itemStack) {
        return this.field_2035.player != null ? (ItemStack) (Object) this.field_2035.player.getInventory().main.get(Expensive.INSTANCE.inventoryService().hotbarSlotSwapper().getClientsideSlot()) : itemStack;
    }

    @Shadow
    protected abstract void method_1757(DrawContext drawContext, ScoreboardObjective scoreboardObjective);

    @Inject(method = {"render"}, at = {@At("HEAD")})
    private void preRender(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
        Expensive.INSTANCE.eventDispatcher().dispatch(new Render2DEvent(drawContext.getMatrices(), Render2DStage.PRE, renderTickCounter, drawContext));
    }

    @Inject(method = {"renderCrosshair"}, at = {@At("HEAD")}, cancellable = true)
    private void crosshair(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
        CrosshairRenderEvent class247Var = new CrosshairRenderEvent();
        Expensive.INSTANCE.eventDispatcher().dispatch(class247Var);
        if (class247Var.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = {"render"}, at = {@At("RETURN")})
    private void postRender(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
        Expensive.INSTANCE.eventDispatcher().dispatch(new Render2DEvent(drawContext.getMatrices(), Render2DStage.POST, renderTickCounter, drawContext));
    }

    @ModifyExpressionValue(method = {"renderCrosshair"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getPerspective()Lnet/minecraft/client/option/Perspective;")})
    private Perspective hookPerspectiveEventOnCrosshair(Perspective perspective) {
        PerspectiveEvent class160Var = new PerspectiveEvent(perspective);
        Expensive.INSTANCE.eventDispatcher().dispatch(class160Var);
        return class160Var.perspective();
    }

    @ModifyExpressionValue(method = {"renderMiscOverlays"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/option/GameOptions;getPerspective()Lnet/minecraft/client/option/Perspective;")})
    private Perspective hookPerspectiveEventOnMiscOverlays(Perspective perspective) {
        PerspectiveEvent class160Var = new PerspectiveEvent(perspective);
        Expensive.INSTANCE.eventDispatcher().dispatch(class160Var);
        return class160Var.perspective();
    }

    @Inject(method = {"renderStatusEffectOverlay"}, at = {@At("HEAD")}, cancellable = true)
    private void renderStatusEffectOverlay(DrawContext drawContext, RenderTickCounter renderTickCounter, CallbackInfo callbackInfo) {
        StatusEffectOverlayEvent class315Var = new StatusEffectOverlayEvent();
        Expensive.INSTANCE.eventDispatcher().dispatch(class315Var);
        if (class315Var.isCancelled()) {
            callbackInfo.cancel();
        }
    }

    @Redirect(method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"))
    private void renderScoreboard(InGameHud inGameHud, DrawContext drawContext, ScoreboardObjective scoreboardObjective) {
        RenderOverlayEvent class252Var = new RenderOverlayEvent(RenderOverlayType.SCOREBOARD);
        Expensive.INSTANCE.eventDispatcher().dispatch(class252Var);
        if (class252Var.isCancelled()) {
            return;
        }
        method_1757(drawContext, scoreboardObjective);
    }
}
