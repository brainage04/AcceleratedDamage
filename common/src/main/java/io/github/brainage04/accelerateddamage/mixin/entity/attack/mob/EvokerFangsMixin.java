package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.projectile.EvokerFangs;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EvokerFangs.class)
public abstract class EvokerFangsMixin {
    @Shadow
    private int warmupDelayTicks;

    @Shadow
    private int lifeTicks;

    /** Warm-up {@code --warmupDelayTicks}; the fangs bite at exactly -8. Steps by 10 but stops on -8. */
    @Redirect(
            method = "tick",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/projectile/EvokerFangs;warmupDelayTicks:I", opcode = Opcodes.PUTFIELD)
    )
    private void acceleratedDamage$warmUpFaster(EvokerFangs self, int vanillaValue) {
        if (!VirtualTime.isEnabled(self.level())) {
            warmupDelayTicks = vanillaValue;
            return;
        }
        int accelerated = warmupDelayTicks - VirtualTime.ACCELERATION;
        warmupDelayTicks = warmupDelayTicks > -8 ? Math.max(-8, accelerated) : accelerated;
    }

    /** Server-side lifetime {@code --lifeTicks} (22) after the bite starts. */
    @Redirect(
            method = "tick",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/projectile/EvokerFangs;lifeTicks:I", opcode = Opcodes.PUTFIELD, ordinal = 1)
    )
    private void acceleratedDamage$expireFaster(EvokerFangs self, int vanillaValue) {
        lifeTicks = VirtualTime.isEnabled(self.level()) ? vanillaValue - VirtualTime.LAST_OFFSET : vanillaValue;
    }
}
