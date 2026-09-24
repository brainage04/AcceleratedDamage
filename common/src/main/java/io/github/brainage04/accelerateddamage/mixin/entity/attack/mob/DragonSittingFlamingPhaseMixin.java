package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonSittingFlamingPhase;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DragonSittingFlamingPhase.class)
public abstract class DragonSittingFlamingPhaseMixin {
    @Shadow
    private int flameTicks;

    /** Breath {@code flameTicks++}: the breath cloud spawns at exactly 10 and the phase ends at 200. */
    @Redirect(
            method = "doServerTick",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonSittingFlamingPhase;flameTicks:I", opcode = Opcodes.PUTFIELD, ordinal = 0)
    )
    private void acceleratedDamage$up0(DragonSittingFlamingPhase self, int vanillaValue) {
        flameTicks = VirtualTime.isEnabled() ? flameTicks + VirtualTime.ACCELERATION : vanillaValue;
    }
}
