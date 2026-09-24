package io.github.brainage04.accelerateddamage.mixin.entity.attack.brain;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.ai.behavior.PrepareRamNearestTarget;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PrepareRamNearestTarget.class)
public abstract class PrepareRamNearestTargetMixin {
    @Shadow
    @Final
    private int ramPrepareTime;

    /** {@code ramPrepareTime}: the goat's 20-tick wind-up at the ram start position, measured against game time. */
    @Redirect(
            method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/PathfinderMob;J)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/ai/behavior/PrepareRamNearestTarget;ramPrepareTime:I",
                    opcode = Opcodes.GETFIELD
            )
    )
    private int acceleratedDamage$shortenRamWindUp(PrepareRamNearestTarget self) {
        return VirtualTime.isEnabled()
                ? (ramPrepareTime + VirtualTime.ACCELERATION / 2) / VirtualTime.ACCELERATION
                : ramPrepareTime;
    }
}
