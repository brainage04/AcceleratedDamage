package io.github.brainage04.accelerateddamage.mixin.entity.regeneration;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(HappyGhast.class)
public abstract class HappyGhastMixin {
    /** Self-healing every 20 ticks in clouds or precipitation, otherwise every 600 ticks. */
    @ModifyConstant(method = "continuousHeal", constant = {@Constant(intValue = 20), @Constant(intValue = 600)})
    private int acceleratedDamage$healFaster(int original) {
        return VirtualTime.isEnabled(((HappyGhast) (Object) this).level())
                ? original / VirtualTime.ACCELERATION
                : original;
    }
}
