package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.Blaze$BlazeAttackGoal")
public abstract class BlazeAttackGoalMixin {
    @Shadow
    private int attackTime;

    /**
     * {@code attackTime} counts down every tick; melee resets it to 20, the fireball burst to 60, 6, 6, 6
     * and then 100. The countdown steps by 10, and each reset keeps the virtual ticks that the last step
     * overshot (at most 9), so a burst's 6-tick spacing is paid back later and the full cycle keeps the
     * vanilla length. At most one fireball fires per server tick.
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/monster/Blaze$BlazeAttackGoal;attackTime:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void acceleratedDamage$attackFaster(@Coerce Object self, int vanillaValue) {
        if (!VirtualTime.isEnabled()) {
            attackTime = vanillaValue;
            return;
        }
        if (vanillaValue == attackTime - 1) {
            attackTime -= VirtualTime.ACCELERATION;
            return;
        }
        attackTime = vanillaValue + Math.max(Math.min(attackTime, 0), -VirtualTime.LAST_OFFSET);
    }
}
