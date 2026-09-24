package io.github.brainage04.accelerateddamage.mixin.entity.attack.goal;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Snow golem, witch, llama spit, drowned trident and the wither's main head. */
@Mixin(RangedAttackGoal.class)
public abstract class RangedAttackGoalMixin {
    @Shadow
    @Final
    private Mob mob;

    @Shadow
    private int attackTime;

    /**
     * Shot interval {@code attackTime} ({@code --attackTime == 0}): the read feeding the decrement
     * is lowered by a further 9, stopping at 1 so the countdown still lands exactly on 0.
     * Non-positive values (unset, or a missed shot waiting to reroll) keep vanilla's single step.
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/ai/goal/RangedAttackGoal;attackTime:I",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0
            )
    )
    private int acceleratedDamage$countDownFaster(RangedAttackGoal self) {
        return this.attackTime > 0 && VirtualTime.isEnabled(this.mob.level())
                ? Math.max(1, this.attackTime - VirtualTime.LAST_OFFSET)
                : this.attackTime;
    }
}
