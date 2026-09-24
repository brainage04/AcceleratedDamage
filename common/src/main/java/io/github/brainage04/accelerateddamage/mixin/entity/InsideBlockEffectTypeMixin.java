package io.github.brainage04.accelerateddamage.mixin.entity;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InsideBlockEffectType.class)
public abstract class InsideBlockEffectTypeMixin {
    /** Powder snow build-up: {@code min(ticksRequiredToFreeze, ticksFrozen + 1)} inside the FREEZE effect. */
    @Redirect(
            method = "lambda$static$0",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I")
    )
    private static int acceleratedDamage$freezeFaster(int ticksRequiredToFreeze, int vanillaTicksFrozen, Entity entity) {
        if (VirtualTime.isEnabled(entity.level())) {
            return Math.min(ticksRequiredToFreeze, vanillaTicksFrozen + VirtualTime.LAST_OFFSET);
        }
        return Math.min(ticksRequiredToFreeze, vanillaTicksFrozen);
    }
}
