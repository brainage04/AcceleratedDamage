package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "net.minecraft.world.entity.monster.Vex$VexChargeAttackGoal")
public abstract class VexChargeAttackGoalMixin {
    /** Idle pause before a charge: a {@code 1 / reducedTickDelay(7)} chance per goal check. */
    @ModifyArg(
            method = "canUse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I")
    )
    private int acceleratedDamage$chargeSooner(int bound) {
        return VirtualTime.isEnabled()
                ? Math.max(1, (bound + VirtualTime.LAST_OFFSET) / VirtualTime.ACCELERATION)
                : bound;
    }
}
