package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.world.entity.monster.illager.SpellcasterIllager$SpellcasterUseSpellGoal")
public abstract class SpellcasterUseSpellGoalMixin {
    @Unique
    private static final String ACCELERATED_DAMAGE$EVOKER = "net.minecraft.world.entity.monster.illager.Evoker$";

    /** Warm-up before the spell lands (evoker fangs 20, vex summoning 20). */
    @Redirect(
            method = "start",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/illager/SpellcasterIllager$SpellcasterUseSpellGoal;getCastWarmupTime()I"
            )
    )
    private int acceleratedDamage$warmUpFaster(@Coerce Object self) {
        return acceleratedDamage$scale(self, ((SpellcasterUseSpellGoalInvoker) self).acceleratedDamage$getCastWarmupTime());
    }

    /** Time the illager stays in its casting pose (fangs 40, vex summoning 100). */
    @Redirect(
            method = "start",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/illager/SpellcasterIllager$SpellcasterUseSpellGoal;getCastingTime()I"
            )
    )
    private int acceleratedDamage$castFaster(@Coerce Object self) {
        return acceleratedDamage$scale(self, ((SpellcasterUseSpellGoalInvoker) self).acceleratedDamage$getCastingTime());
    }

    /** Cooldown until the same spell may be cast again (fangs 100, vex summoning 340). */
    @Redirect(
            method = "start",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/monster/illager/SpellcasterIllager$SpellcasterUseSpellGoal;getCastingInterval()I"
            )
    )
    private int acceleratedDamage$recastSooner(@Coerce Object self) {
        return acceleratedDamage$scale(self, ((SpellcasterUseSpellGoalInvoker) self).acceleratedDamage$getCastingInterval());
    }

    /** Only the evoker's attack spells (fangs, vex summoning); wololo and the illusioner's spells deal no damage. */
    @Unique
    private static int acceleratedDamage$scale(Object goal, int ticks) {
        String name = goal.getClass().getName();
        boolean attackSpell = name.equals(ACCELERATED_DAMAGE$EVOKER + "EvokerAttackSpellGoal")
                || name.equals(ACCELERATED_DAMAGE$EVOKER + "EvokerSummonSpellGoal");
        return attackSpell && VirtualTime.isEnabled()
                ? Math.max(1, (ticks + VirtualTime.ACCELERATION / 2) / VirtualTime.ACCELERATION)
                : ticks;
    }
}
