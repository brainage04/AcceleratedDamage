package io.github.brainage04.accelerateddamage.mixin.block;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {
    /** The vanilla pulse period; {@link #acceleratedDamage$pulseFaster} fails to apply if vanilla changes it. */
    @Unique
    private static final long ACCELERATED_DAMAGE$VANILLA_PULSE = 80L;

    /**
     * Every 80 game ticks a beacon re-checks its pyramid and re-applies its effects for
     * {@code (9 + 2 * levels) * 20} ticks. Those effects now wear off ten times faster, so the pulse
     * has to come ten times as often to keep them from lapsing between pulses.
     */
    @ModifyConstant(method = "tick", constant = @Constant(longValue = ACCELERATED_DAMAGE$VANILLA_PULSE))
    private static long acceleratedDamage$pulseFaster(
            long original,
            Level level,
            BlockPos pos,
            BlockState state,
            BeaconBlockEntity entity
    ) {
        return VirtualTime.isEnabled(level) ? original / VirtualTime.ACCELERATION : original;
    }

    /** The ambient hum shares the pulse block but is not a timer, so it keeps the vanilla period. */
    @WrapWithCondition(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/entity/BeaconBlockEntity;playSound(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/sounds/SoundEvent;)V"
            )
    )
    private static boolean acceleratedDamage$humOnVanillaPulse(Level level, BlockPos pos, SoundEvent event) {
        return event != SoundEvents.BEACON_AMBIENT || level.getGameTime() % ACCELERATED_DAMAGE$VANILLA_PULSE == 0L;
    }
}
