package io.github.brainage04.accelerateddamage.mixin.effect;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.ServerContext;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MobEffectInstance.class)
public class MobEffectInstanceMixin {
    @Redirect(
            method = "tickDownDuration",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffectInstance;mapDuration(Lit/unimi/dsi/fastutil/ints/Int2IntFunction;)I"
            )
    )
    private int acceleratedDamage$decrementDurationFaster(
            MobEffectInstance instance,
            Int2IntFunction vanillaMapper
    ) {
        MinecraftServer server = ServerContext.get();
        if (server != null && server.getGameRules().get(ModGameRules.FASTER_EFFECT_DAMAGE_TICKING)) {
            return instance.mapDuration(duration -> duration - 10);
        }
        return instance.mapDuration(vanillaMapper);
    }
}
