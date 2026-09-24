package io.github.brainage04.accelerateddamage.mixin.entity.attack.brain;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(MeleeAttack.class)
public abstract class MeleeAttackMixin {
    /** {@code ATTACK_COOLING_DOWN}: the brain melee cooldown (piglin, brute, hoglin, zoglin, warden, axolotl, creaking). */
    @ModifyArg(
            method = "lambda$create$3",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/behavior/declarative/MemoryAccessor;setWithExpiry(Ljava/lang/Object;J)V"
            ),
            index = 1
    )
    private static long acceleratedDamage$shortenMeleeCooldown(long cooldownBetweenAttacks) {
        return VirtualTime.isEnabled() ? acceleratedDamage$scaleTimer(cooldownBetweenAttacks) : cooldownBetweenAttacks;
    }

    /**
     * Scales a vanilla brain timer whose event fires {@code vanillaTicks + 1} ticks after it is set (memory
     * expiry and {@code Behavior} timeouts both behave this way) to the server tick nearest a tenth of that.
     */
    @Unique
    private static long acceleratedDamage$scaleTimer(long vanillaTicks) {
        return Math.max(0L, (vanillaTicks + 1 + VirtualTime.ACCELERATION / 2) / VirtualTime.ACCELERATION - 1);
    }
}
