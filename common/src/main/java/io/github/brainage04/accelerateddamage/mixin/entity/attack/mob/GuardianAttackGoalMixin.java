package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.Guardian$GuardianAttackGoal")
public abstract class GuardianAttackGoalMixin {
    @Shadow
    private int attackTime;

    /**
     * Beam charge {@code attackTime++}: from -10 the beam locks on at exactly 0, then hurts at
     * {@code getAttackDuration()} (80, elder 60). Steps by 10 but stops on 0 so the lock-on still fires.
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/monster/Guardian$GuardianAttackGoal;attackTime:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void acceleratedDamage$chargeBeamFaster(@Coerce Object self, int vanillaValue) {
        if (!VirtualTime.isEnabled()) {
            attackTime = vanillaValue;
            return;
        }
        int accelerated = attackTime + VirtualTime.ACCELERATION;
        attackTime = attackTime < 0 ? Math.min(0, accelerated) : accelerated;
    }
}
