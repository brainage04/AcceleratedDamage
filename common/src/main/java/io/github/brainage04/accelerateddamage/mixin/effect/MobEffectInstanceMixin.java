package io.github.brainage04.accelerateddamage.mixin.effect;

import io.github.brainage04.accelerateddamage.util.EffectTickCadence;
import io.github.brainage04.accelerateddamage.util.VirtualTime;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
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
    @Shadow
    private int duration;

    /** Bit {@code n} is set when the effect applies at virtual tick offset {@code n} of this server tick. */
    @Unique
    private int acceleratedDamage$pendingOffsets;

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
    private boolean acceleratedDamage$collectApplicationOffsets(
            MobEffect effect,
            int tick,
            int amplifier
    ) {
        acceleratedDamage$pendingOffsets = 0;
        if (!VirtualTime.isEnabled()) {
            acceleratedDamage$wasAcceleratingInfinite = false;
            return effect.shouldApplyEffectTickThisTick(tick, amplifier);
        }

        int currentTick = tick;
        int ticksAdvanced = Math.min(VirtualTime.ACCELERATION, Math.max(duration, 0));
        boolean ascending = false;
        if (duration == MobEffectInstance.INFINITE_DURATION) {
            if (!acceleratedDamage$wasAcceleratingInfinite) {
                acceleratedDamage$infiniteTick = tick;
                acceleratedDamage$wasAcceleratingInfinite = true;
            }
            currentTick = acceleratedDamage$infiniteTick;
            acceleratedDamage$infiniteTick += VirtualTime.ACCELERATION;
            ticksAdvanced = VirtualTime.ACCELERATION;
            ascending = true;
        } else {
            acceleratedDamage$wasAcceleratingInfinite = false;
        }

        for (int offset = 0; offset < ticksAdvanced; offset++) {
            int scheduledTick = EffectTickCadence.scheduledTick(currentTick, offset, ascending);
            if (effect.shouldApplyEffectTickThisTick(scheduledTick, amplifier)) {
                acceleratedDamage$pendingOffsets |= 1 << offset;
            }
        }
        return acceleratedDamage$pendingOffsets != 0;
    }

    @Redirect(
            method = "tickServer",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/effect/MobEffect;applyEffectTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;I)Z"
            )
    )
    private boolean acceleratedDamage$applyAtEachOffset(
            MobEffect effect,
            ServerLevel level,
            LivingEntity entity,
            int amplifier
    ) {
        int offsets = acceleratedDamage$pendingOffsets;
        acceleratedDamage$pendingOffsets = 0;
        if (offsets == 0) {
            return effect.applyEffectTick(level, entity, amplifier);
        }
        while (offsets != 0) {
            int offset = Integer.numberOfTrailingZeros(offsets);
            offsets &= offsets - 1;
            int previousOffset = VirtualTime.enter(offset);
            try {
                if (!effect.applyEffectTick(level, entity, amplifier)) {
                    return false;
                }
            } finally {
                VirtualTime.exit(previousOffset);
            }
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
        if (VirtualTime.isEnabled()) {
            return instance.mapDuration(duration -> Math.max(0, duration - VirtualTime.ACCELERATION));
        }
        return instance.mapDuration(vanillaMapper);
    }
}
