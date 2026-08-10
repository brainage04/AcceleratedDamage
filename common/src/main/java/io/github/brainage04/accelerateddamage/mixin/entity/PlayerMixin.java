package io.github.brainage04.accelerateddamage.mixin.entity;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
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
            float partialTick,
            CallbackInfoReturnable<Float> cir
    ) {
        Player self = (Player) (Object) this;
        if (self.level() instanceof ServerLevel level
                && level.getGameRules().get(ModGameRules.DISABLE_ATTACK_COOLDOWN)) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "cannotAttackWithItem", at = @At("HEAD"), cancellable = true)
    private void acceleratedDamage$allowImmediateItemAttacks(
            ItemStack stack,
            int ticksSinceLastAttack,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Player self = (Player) (Object) this;
        if (self.level() instanceof ServerLevel level
                && level.getGameRules().get(ModGameRules.DISABLE_ATTACK_COOLDOWN)) {
            cir.setReturnValue(false);
        }
    }
}
