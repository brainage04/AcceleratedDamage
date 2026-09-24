package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.monster.Creeper;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Creeper.class)
public abstract class CreeperMixin {
    @Shadow
    private int swell;

    /** Fuse {@code swell += swellDir} toward {@code maxSwell} (30); vanilla clamps the result right after. */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/monster/Creeper;swell:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0
            )
    )
    private void acceleratedDamage$swellFaster(Creeper self, int vanillaValue) {
        swell = VirtualTime.isEnabled(self.level())
                ? swell + (vanillaValue - swell) * VirtualTime.ACCELERATION
                : vanillaValue;
    }
}
