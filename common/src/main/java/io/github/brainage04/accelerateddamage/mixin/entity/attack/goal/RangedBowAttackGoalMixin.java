package io.github.brainage04.accelerateddamage.mixin.entity.attack.goal;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Skeleton-family and illusioner bows. The draw itself ({@code getTicksUsingItem() >= 20}) is
 * accelerated by {@link LivingEntityItemUseMixin}. The {@code mob} field is not shadowed because
 * NeoForge widens its erased type from {@code Monster} to {@code Mob}; goals only tick on the
 * server, so the server-global rule check is used instead.
 */
@Mixin(RangedBowAttackGoal.class)
public abstract class RangedBowAttackGoalMixin {
    @Shadow
    private int attackTime;

    /**
     * Pause between shots {@code attackTime} ({@code --attackTime <= 0}): the read feeding the
     * decrement is lowered by a further 9, stopping at 1 so the countdown ends on 0 without overshoot.
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/ai/goal/RangedBowAttackGoal;attackTime:I",
                    opcode = Opcodes.GETFIELD
            )
    )
    private int acceleratedDamage$countDownFaster(RangedBowAttackGoal<?> self) {
        return this.attackTime > 0 && VirtualTime.isEnabled()
                ? Math.max(1, this.attackTime - VirtualTime.LAST_OFFSET)
                : this.attackTime;
    }
}
