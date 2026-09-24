package io.github.brainage04.accelerateddamage.mixin.entity.air;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.animal.dolphin.Dolphin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Dolphin.class)
public abstract class DolphinMixin {
    /** Out of water, moistness drops by one per tick until the dolphin starts drying out. */
    @ModifyArg(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/dolphin/Dolphin;setMoisntessLevel(I)V",
                    ordinal = 1
            )
    )
    private int acceleratedDamage$dryOutFaster(int vanillaMoistness) {
        return VirtualTime.isEnabled(((Dolphin) (Object) this).level())
                ? vanillaMoistness - VirtualTime.LAST_OFFSET
                : vanillaMoistness;
    }
}
