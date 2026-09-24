package io.github.brainage04.accelerateddamage.mixin.entity.attack.goal;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Covers every {@link MeleeAttackGoal} subclass, none of which keeps its own attack timer. */
@Mixin(MeleeAttackGoal.class)
public abstract class MeleeAttackGoalMixin {
    @Shadow
    @Final
    protected PathfinderMob mob;

    @Shadow
    private int ticksUntilNextAttack;

    /** Melee cooldown {@code ticksUntilNextAttack} (20 ticks), stepped by 10 and floored at 0 like vanilla. */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/ai/goal/MeleeAttackGoal;ticksUntilNextAttack:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void acceleratedDamage$coolDownFaster(MeleeAttackGoal self, int vanillaValue) {
        this.ticksUntilNextAttack = VirtualTime.isEnabled(this.mob.level())
                ? Math.max(0, vanillaValue - VirtualTime.LAST_OFFSET)
                : vanillaValue;
    }

    /**
     * Re-engage throttle: {@code canUse} runs at most every 20 ticks. Mobs that stop the goal when their
     * path finishes (vindicators, wither skeletons, spiders) re-enter through it before each swing, so
     * it spaces their attacks just like the 20-tick cooldown does.
     */
    @ModifyConstant(method = "canUse", constant = @Constant(longValue = 20L))
    private long acceleratedDamage$reengageSooner(long original) {
        return VirtualTime.isEnabled(this.mob.level()) ? original / VirtualTime.ACCELERATION : original;
    }
}
