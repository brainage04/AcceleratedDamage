package io.github.brainage04.accelerateddamage.mixin.entity.attack.goal;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.OcelotAttackGoal;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(OcelotAttackGoal.class)
public abstract class OcelotAttackGoalMixin {
    @Shadow
    @Final
    private Mob mob;

    @Shadow
    private int attackTime;

    /** Ocelot/cat melee cooldown {@code attackTime} (20 ticks), stepped by 10 and floored at 0 like vanilla. */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/ai/goal/OcelotAttackGoal;attackTime:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0
            )
    )
    private void acceleratedDamage$coolDownFaster(OcelotAttackGoal self, int vanillaValue) {
        this.attackTime = VirtualTime.isEnabled(this.mob.level())
                ? Math.max(0, vanillaValue - VirtualTime.LAST_OFFSET)
                : vanillaValue;
    }
}
