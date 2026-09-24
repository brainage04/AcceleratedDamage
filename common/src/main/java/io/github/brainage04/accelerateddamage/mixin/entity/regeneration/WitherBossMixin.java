package io.github.brainage04.accelerateddamage.mixin.entity.regeneration;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(WitherBoss.class)
public abstract class WitherBossMixin {
    /** Self-healing every 20 ticks ({@code tickCount % 20}). */
    @ModifyConstant(method = "customServerAiStep", constant = @Constant(intValue = 20, ordinal = 1))
    private int acceleratedDamage$healFaster(int original) {
        return VirtualTime.isEnabled(((WitherBoss) (Object) this).level())
                ? original / VirtualTime.ACCELERATION
                : original;
    }
}
