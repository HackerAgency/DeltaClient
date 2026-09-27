package ru.expensive.mixin;

import aethereal.math.Rotation;
import aethereal.event.KeyBindingEvent;
import aethereal.event.MovementInputEvent;
import aethereal.math.DirectionalInput;
import aethereal.Expensive;
import aethereal.event.MovementUpdateEvent;
import aethereal.event.MovementUpdateSource;
import aethereal.util.RotationManager;
import aethereal.math.ScheduledRotation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({KeyboardInput.class})
public abstract class KeyboardInputMixin extends InputMixin {

    @Shadow
    @Final
    private GameOptions field_3902;

    @WrapOperation(method = {"tick"}, at = {@At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z")})
    private boolean hookPressed(KeyBinding keyBinding, Operation<Boolean> operation) {
        KeyBindingEvent class035Var = new KeyBindingEvent(keyBinding);
        Expensive.INSTANCE.eventDispatcher().dispatch(class035Var);
        return ((Boolean) operation.call(new Object[]{keyBinding})).booleanValue() || class035Var.isCancelled();
    }

    @ModifyExpressionValue(method = {"tick"}, at = {@At(value = "NEW", target = "(ZZZZZZZ)Lnet/minecraft/util/PlayerInput;")})
    private PlayerInput modifyInput(PlayerInput playerInput) {
        MovementInputEvent class040Var = new MovementInputEvent(DirectionalInput.fromInput(playerInput), playerInput.jump(), playerInput.sneak(), playerInput.sprint());
        Expensive.INSTANCE.eventDispatcher().dispatch(class040Var);
        DirectionalInput class041VarTransformDirection = transformDirection(class040Var.getInput());
        MovementUpdateEvent class308Var = new MovementUpdateEvent(class041VarTransformDirection, playerInput.sprint(), MovementUpdateSource.INPUT);
        Expensive.INSTANCE.eventDispatcher().dispatch(class308Var);
        return new PlayerInput(class041VarTransformDirection.forward(), class041VarTransformDirection.backward(), class041VarTransformDirection.left(), class041VarTransformDirection.right(), class040Var.isJumping(), class040Var.isSneaking(), class308Var.isSprint());
    }

    @Unique
    private DirectionalInput transformDirection(DirectionalInput class041Var) {
        ClientPlayerEntity clientPlayerEntity = MinecraftClient.getInstance().player;
        Rotation currentRotation = RotationManager.INSTANCE.getCurrentRotation();
        ScheduledRotation currentStrategy = RotationManager.INSTANCE.getCurrentStrategy();
        float movementMultiplier = KeyboardInput.getMovementMultiplier(class041Var.forward(), class041Var.backward());
        float movementMultiplier2 = KeyboardInput.getMovementMultiplier(class041Var.left(), class041Var.right());
        if (currentStrategy == null || ((currentStrategy.moveCorrection() && currentStrategy.focusedCorrection()) || currentRotation == null || clientPlayerEntity == null || !currentStrategy.moveCorrection())) {
            return class041Var;
        }
        float radians = (float) Math.toRadians(clientPlayerEntity.getYaw() - currentRotation.getYaw());
        return DirectionalInput.fromMovementForwardAndSideways(Math.round((movementMultiplier * MathHelper.cos(radians)) + (movementMultiplier2 * MathHelper.sin(radians))), Math.round((movementMultiplier2 * MathHelper.cos(radians)) - (movementMultiplier * MathHelper.sin(radians))));
    }
}
