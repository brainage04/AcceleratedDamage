package io.github.brainage04.accelerateddamage.mixin.effect;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.ServerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.PoisonMobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PoisonMobEffect.class)
public abstract class PoisonMobEffectMixin {
    @Inject(method = "shouldApplyEffectTickThisTick", at = @At("HEAD"), cancellable = true)
    private void acceleratedDamage$shortenDamageInterval(
            int duration,
            int amplifier,
            CallbackInfoReturnable<Boolean> cir
    ) {
        MinecraftServer server = ServerContext.get();
        if (server == null || !server.getGameRules().get(ModGameRules.FASTER_EFFECT_DAMAGE_TICKING)) {
            return;
        }
        int interval = (25 >> amplifier) / 10;
        cir.setReturnValue(duration % Math.max(interval, 1) == 0);
    }
}
