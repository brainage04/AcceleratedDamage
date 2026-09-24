package io.github.brainage04.accelerateddamage.mixin.block;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {
    /**
     * Every 80 game ticks a beacon re-checks its pyramid and re-applies its effects for
     * {@code (9 + 2 * levels) * 20} ticks. Those effects now wear off ten times faster, so the pulse
     * has to come ten times as often to keep them from lapsing between pulses.
     */
    @ModifyConstant(method = "tick", constant = @Constant(longValue = 80L))
    private static long acceleratedDamage$pulseFaster(
            long original,
            Level level,
            BlockPos pos,
            BlockState state,
            BeaconBlockEntity entity
    ) {
        return VirtualTime.isEnabled(level) ? original / VirtualTime.ACCELERATION : original;
    }
}
