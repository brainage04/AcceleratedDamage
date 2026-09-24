package io.github.brainage04.accelerateddamage.mixin.entity.attack.brain;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.animal.frog.ShootTongue;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ShootTongue.class)
public abstract class ShootTongueMixin {
    @Shadow
    private int eatAnimationTimer;

    /** {@code eatAnimationTimer} during the catch animation: counts virtual ticks, ten per server tick. */
    @Redirect(
            method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/frog/Frog;J)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/animal/frog/ShootTongue;eatAnimationTimer:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 1
            )
    )
    private void acceleratedDamage$countCatchAnimationFaster(ShootTongue self, int vanillaValue) {
        eatAnimationTimer = VirtualTime.isEnabled() ? vanillaValue + VirtualTime.LAST_OFFSET : vanillaValue;
    }

    /**
     * The 7-tick tongue wind-up before the frog eats its target: the catch fires once any virtual tick of this
     * server tick reaches the threshold, i.e. once the tick's first value is within nine of it.
     */
    @ModifyConstant(
            method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/frog/Frog;J)V",
            constant = @Constant(intValue = 6)
    )
    private int acceleratedDamage$shortenCatchWindUp(int vanillaThreshold) {
        return VirtualTime.isEnabled() ? vanillaThreshold - VirtualTime.LAST_OFFSET : vanillaThreshold;
    }
}
