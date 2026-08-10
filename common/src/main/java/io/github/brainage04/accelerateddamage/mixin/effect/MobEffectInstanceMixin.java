package io.github.brainage04.accelerateddamage.mixin.effect;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.DamageAccelerationContext;
import io.github.brainage04.accelerateddamage.util.EffectTickCadence;
import io.github.brainage04.accelerateddamage.util.ServerContext;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MobEffectInstance.class)
public abstract class MobEffectInstanceMixin {
    private static final int ACCELERATION = 10;

    @Shadow
    private int duration;

    @Unique
    private int acceleratedDamage$pendingApplications;

    @Unique
    private int acceleratedDamage$infiniteTick;

    @Unique
    private boolean acceleratedDamage$wasAcceleratingInfinite;

    @Redirect(
            method = "tickServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffect;shouldApplyEffectTickThisTick(II)Z"
            )
    )
    private boolean acceleratedDamage$countEffectApplications(
            MobEffect effect,
            int tick,
            int amplifier
    ) {
        if (!acceleratedDamage$isEnabled()) {
            acceleratedDamage$pendingApplications = 0;
            acceleratedDamage$wasAcceleratingInfinite = false;
            return effect.shouldApplyEffectTickThisTick(tick, amplifier);
        }

        int currentTick = tick;
        int ticksAdvanced = Math.min(ACCELERATION, Math.max(duration, 0));
        boolean ascending = false;
        if (duration == MobEffectInstance.INFINITE_DURATION) {
            if (!acceleratedDamage$wasAcceleratingInfinite) {
                acceleratedDamage$infiniteTick = tick;
                acceleratedDamage$wasAcceleratingInfinite = true;
            }
            currentTick = acceleratedDamage$infiniteTick;
            acceleratedDamage$infiniteTick += ACCELERATION;
            ticksAdvanced = ACCELERATION;
            ascending = true;
        } else {
            acceleratedDamage$wasAcceleratingInfinite = false;
        }

        acceleratedDamage$pendingApplications = 0;
        for (int offset = 0; offset < ticksAdvanced; offset++) {
            int scheduledTick = EffectTickCadence.scheduledTick(currentTick, offset, ascending);
            if (effect.shouldApplyEffectTickThisTick(scheduledTick, amplifier)) {
                acceleratedDamage$pendingApplications++;
            }
        }
        return acceleratedDamage$pendingApplications > 0;
    }

    @Redirect(
            method = "tickServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffect;applyEffectTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;I)Z"
            )
    )
    private boolean acceleratedDamage$applyEffectRepeatedly(
            MobEffect effect,
            ServerLevel level,
            LivingEntity entity,
            int amplifier
    ) {
        int applications = acceleratedDamage$pendingApplications;
        acceleratedDamage$pendingApplications = 0;
        if (applications == 0) {
            return effect.applyEffectTick(level, entity, amplifier);
        }
        boolean previousContext = DamageAccelerationContext.enter();
        try {
            for (int application = 0; application < applications; application++) {
                if (!effect.applyEffectTick(level, entity, amplifier)) {
                    return false;
                }
            }
        } finally {
            DamageAccelerationContext.exit(previousContext);
        }
        return true;
    }

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
        if (acceleratedDamage$isEnabled()) {
            return instance.mapDuration(duration -> Math.max(0, duration - ACCELERATION));
        }
        return instance.mapDuration(vanillaMapper);
    }

    @Unique
    private static boolean acceleratedDamage$isEnabled() {
        MinecraftServer server = ServerContext.get();
        return server != null && server.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING);
    }
}
