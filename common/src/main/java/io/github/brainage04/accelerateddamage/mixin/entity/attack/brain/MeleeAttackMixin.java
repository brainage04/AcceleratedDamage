package io.github.brainage04.accelerateddamage.mixin.entity.attack.brain;

import io.github.brainage04.accelerateddamage.util.CompressedTicks;
import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.ai.behavior.MeleeAttack;
import org.spongepowered.asm.mixin.Mixin;
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
        return VirtualTime.isEnabled() ? CompressedTicks.brainTimer(cooldownBetweenAttacks) : cooldownBetweenAttacks;
    }
}
