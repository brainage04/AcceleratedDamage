package io.github.brainage04.accelerateddamage.mixin.entity.regeneration;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {
    /** End crystal healing every 10 ticks ({@code tickCount % 10}). */
    @ModifyConstant(method = "checkCrystals", constant = @Constant(intValue = 10, ordinal = 0))
    private int acceleratedDamage$healFaster(int original) {
        return VirtualTime.isEnabled(((EnderDragon) (Object) this).level())
                ? original / VirtualTime.ACCELERATION
                : original;
    }
}
