package io.github.brainage04.accelerateddamage.mixin.block;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ConduitBlockEntity.class)
public abstract class ConduitBlockEntityMixin {
    /**
     * Every 40 game ticks a conduit refreshes its shape, re-applies Conduit Power and attacks a hostile mob.
     * The refresh has to speed up too, because the effect it re-applies wears off ten times faster.
     */
    @ModifyConstant(method = "serverTick", constant = @Constant(longValue = 40L))
    private static long acceleratedDamage$pulseFaster(
            long original,
            Level level,
            BlockPos pos,
            BlockState state,
            ConduitBlockEntity entity
    ) {
        return VirtualTime.isEnabled(level) ? original / VirtualTime.ACCELERATION : original;
    }
}
