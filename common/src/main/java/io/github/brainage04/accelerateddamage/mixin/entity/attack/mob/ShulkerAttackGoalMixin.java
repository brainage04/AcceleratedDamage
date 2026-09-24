package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.Shulker$ShulkerAttackGoal")
public abstract class ShulkerAttackGoalMixin {
    @Shadow
    private int attackTime;

    /** Bullet interval {@code attackTime--}, reset to {@code 20 + 10k} after each shot. */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/monster/Shulker$ShulkerAttackGoal;attackTime:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0
            )
    )
    private void acceleratedDamage$countDownFaster(@Coerce Object self, int vanillaValue) {
        attackTime = VirtualTime.isEnabled() ? vanillaValue - VirtualTime.LAST_OFFSET : vanillaValue;
    }
}
