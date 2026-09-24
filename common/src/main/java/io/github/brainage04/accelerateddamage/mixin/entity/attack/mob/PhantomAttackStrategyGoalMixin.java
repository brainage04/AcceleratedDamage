package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.Phantom$PhantomAttackStrategyGoal")
public abstract class PhantomAttackStrategyGoalMixin {
    @Shadow
    private int nextSweepTick;

    /** Time spent circling before the next swoop, {@code nextSweepTick--} (160 to 239 ticks). */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/monster/Phantom$PhantomAttackStrategyGoal;nextSweepTick:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0
            )
    )
    private void acceleratedDamage$countDownFaster(@Coerce Object self, int vanillaValue) {
        nextSweepTick = VirtualTime.isEnabled() ? vanillaValue - VirtualTime.LAST_OFFSET : vanillaValue;
    }
}
