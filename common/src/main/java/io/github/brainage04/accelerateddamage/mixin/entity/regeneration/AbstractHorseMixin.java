package io.github.brainage04.accelerateddamage.mixin.entity.regeneration;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(AbstractHorse.class)
public abstract class AbstractHorseMixin {
    /** Natural regeneration with a 1-in-900 chance per tick. */
    @ModifyConstant(method = "aiStep", constant = @Constant(intValue = 900))
    private int acceleratedDamage$healFaster(int original) {
        return VirtualTime.isEnabled(((AbstractHorse) (Object) this).level())
                ? original / VirtualTime.ACCELERATION
                : original;
    }
}
