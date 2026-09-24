package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonStrafePlayerPhase;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DragonStrafePlayerPhase.class)
public abstract class DragonStrafePlayerPhaseMixin {
    @Shadow
    private int fireballCharge;

    /** Fireball charge {@code fireballCharge++}; the dragon fires once it reaches 5 while facing the target. */
    @Redirect(
            method = "doServerTick",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonStrafePlayerPhase;fireballCharge:I", opcode = Opcodes.PUTFIELD, ordinal = 0)
    )
    private void acceleratedDamage$up0(DragonStrafePlayerPhase self, int vanillaValue) {
        fireballCharge = VirtualTime.isEnabled() ? fireballCharge + VirtualTime.ACCELERATION : vanillaValue;
    }

    /** Charge decay {@code fireballCharge--} when the target is out of sight. */
    @Redirect(
            method = "doServerTick",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonStrafePlayerPhase;fireballCharge:I", opcode = Opcodes.PUTFIELD, ordinal = 2)
    )
    private void acceleratedDamage$down2(DragonStrafePlayerPhase self, int vanillaValue) {
        fireballCharge = VirtualTime.isEnabled() ? Math.max(0, fireballCharge - VirtualTime.ACCELERATION) : vanillaValue;
    }

    /** Charge decay {@code fireballCharge--} when the target is out of range. */
    @Redirect(
            method = "doServerTick",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonStrafePlayerPhase;fireballCharge:I", opcode = Opcodes.PUTFIELD, ordinal = 3)
    )
    private void acceleratedDamage$down3(DragonStrafePlayerPhase self, int vanillaValue) {
        fireballCharge = VirtualTime.isEnabled() ? Math.max(0, fireballCharge - VirtualTime.ACCELERATION) : vanillaValue;
    }
}
