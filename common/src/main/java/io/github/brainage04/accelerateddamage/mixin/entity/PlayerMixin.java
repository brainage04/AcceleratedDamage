package io.github.brainage04.accelerateddamage.mixin.entity;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void acceleratedDamage$fullyChargeAttacks(
            float partialTick, CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (self.level() instanceof ServerLevel level
                && level.getGameRules().get(ModGameRules.DISABLE_ATTACK_COOLDOWN)) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "cannotAttackWithItem", at = @At("HEAD"), cancellable = true)
    private void acceleratedDamage$allowImmediateItemAttacks(
            ItemStack stack, int ticksSinceLastAttack, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (self.level() instanceof ServerLevel level
                && level.getGameRules().get(ModGameRules.DISABLE_ATTACK_COOLDOWN)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * The attack cooldown is {@code 20 / ATTACK_SPEED} ticks ({@code Player.java:1816-1818}, 26.2).
     * On the compressed timeline it is a tenth of that, so every weapon recharges ten times as
     * often. The delay is a float, so the tenth is exact: a sword's 12.5 ticks become 1.25 and a
     * mace's 33.3 become 3.33, and no weapon reaches a delay the game cannot express. The strength
     * scale, the minimum-charge check and the hand animation all read this one delay.
     */
    @Inject(method = "getCurrentItemAttackStrengthDelay", at = @At("RETURN"), cancellable = true)
    private void acceleratedDamage$rechargeFaster(CallbackInfoReturnable<Float> cir) {
        Player self = (Player) (Object) this;
        if (VirtualTime.isEnabled(self.level())) {
            cir.setReturnValue(cir.getReturnValueF() / VirtualTime.ACCELERATION);
        }
    }
}
