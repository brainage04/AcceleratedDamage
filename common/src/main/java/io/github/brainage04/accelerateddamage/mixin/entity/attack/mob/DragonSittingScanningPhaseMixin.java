package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.boss.enderdragon.phases.DragonSittingScanningPhase;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(DragonSittingScanningPhase.class)
public abstract class DragonSittingScanningPhaseMixin {
    @Shadow
    private int scanningTime;

    /** Perched scan {@code scanningTime++}: after 25 the dragon attacks or charges a player it can see; at 100 it takes off. */
    @Redirect(
            method = "doServerTick",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/boss/enderdragon/phases/DragonSittingScanningPhase;scanningTime:I", opcode = Opcodes.PUTFIELD, ordinal = 0)
    )
    private void acceleratedDamage$up0(DragonSittingScanningPhase self, int vanillaValue) {
        scanningTime = VirtualTime.isEnabled() ? scanningTime + VirtualTime.ACCELERATION : vanillaValue;
    }
}
