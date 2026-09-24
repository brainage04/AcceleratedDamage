package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.Ghast$GhastShootFireballGoal")
public abstract class GhastShootFireballGoalMixin {
    @Shadow
    public int chargeTime;

    /** Fireball charge {@code chargeTime++}, which fires at exactly 20 and then restarts from -40. */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/monster/Ghast$GhastShootFireballGoal;chargeTime:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0
            )
    )
    private void acceleratedDamage$chargeFaster(@Coerce Object self, int vanillaValue) {
        chargeTime = VirtualTime.isEnabled() && chargeTime < 20
                ? Math.min(20, chargeTime + VirtualTime.ACCELERATION)
                : vanillaValue;
    }

    /** Charge decay {@code chargeTime--} while the target is out of sight. */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/monster/Ghast$GhastShootFireballGoal;chargeTime:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 2
            )
    )
    private void acceleratedDamage$decayChargeFaster(@Coerce Object self, int vanillaValue) {
        chargeTime = VirtualTime.isEnabled()
                ? Math.max(0, chargeTime - VirtualTime.ACCELERATION)
                : vanillaValue;
    }
}
