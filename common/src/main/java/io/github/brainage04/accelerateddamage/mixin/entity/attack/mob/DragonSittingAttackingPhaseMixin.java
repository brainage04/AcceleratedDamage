package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonSittingAttackingPhase;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DragonSittingAttackingPhase.class)
public abstract class DragonSittingAttackingPhaseMixin {
    @Shadow
    private int attackingTicks;

    /** Perched roar {@code attackingTicks++ >= 40} before it switches back to breathing. */
    @Redirect(
            method = "doServerTick",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonSittingAttackingPhase;attackingTicks:I", opcode = Opcodes.PUTFIELD, ordinal = 0)
    )
    private void acceleratedDamage$up0(DragonSittingAttackingPhase self, int vanillaValue) {
        attackingTicks = VirtualTime.isEnabled() ? attackingTicks + VirtualTime.ACCELERATION : vanillaValue;
    }
}
