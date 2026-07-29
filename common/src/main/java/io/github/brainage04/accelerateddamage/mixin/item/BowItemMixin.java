package io.github.brainage04.accelerateddamage.mixin.item;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.ServerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BowItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BowItem.class)
public class BowItemMixin {
    @Inject(method = "getPowerForTime(I)F", at = @At("HEAD"), cancellable = true)
    private static void acceleratedDamage$chargeImmediately(
            int useTicks,
            CallbackInfoReturnable<Float> cir
    ) {
        MinecraftServer server = ServerContext.get();
        if (server != null && server.getGameRules().get(ModGameRules.INSTANT_SHOOT)) {
            cir.setReturnValue(1.0F);
        }
    }
}
