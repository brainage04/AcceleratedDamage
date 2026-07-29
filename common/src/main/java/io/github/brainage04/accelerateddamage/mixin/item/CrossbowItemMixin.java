package io.github.brainage04.accelerateddamage.mixin.item;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CrossbowItem.class)
public class CrossbowItemMixin {
    @Inject(
            method = "getChargeDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void acceleratedDamage$chargeImmediately(
            ItemStack stack,
            LivingEntity user,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (user.level() instanceof ServerLevel level
                && level.getGameRules().get(ModGameRules.INSTANT_SHOOT)) {
            cir.setReturnValue(0);
        }
    }
}
