package io.github.brainage04.accelerateddamage.mixin.entity.attack.brain;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SonicBoom.class)
public abstract class SonicBoomMixin {
    /**
     * Sonic boom timers set while the attack runs: {@code ATTACK_COOLING_DOWN} and {@code SONIC_BOOM_SOUND_DELAY}
     * (the 34-tick charge-up) in {@code start}, and {@code SONIC_BOOM_SOUND_COOLDOWN} in {@code tick}.
     */
    @ModifyArg(
            method = {
                    "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/warden/Warden;J)V",
                    "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/warden/Warden;J)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;setMemoryWithExpiry(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;J)V"
            ),
            index = 2
    )
    private long acceleratedDamage$shortenSonicBoomTimer(long vanillaTicks) {
        return VirtualTime.isEnabled() ? acceleratedDamage$scaleTimer(vanillaTicks) : vanillaTicks;
    }

    /**
     * {@code SONIC_BOOM_COOLDOWN}: the 40-tick cooldown after a boom and the 200-tick melee-only window after the
     * warden acquires a target.
     */
    @ModifyArg(
            method = "setCooldown(Lnet/minecraft/world/entity/LivingEntity;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;setMemoryWithExpiry(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;J)V"
            ),
            index = 2
    )
    private static long acceleratedDamage$shortenSonicBoomCooldown(long vanillaTicks) {
        return VirtualTime.isEnabled() ? acceleratedDamage$scaleTimer(vanillaTicks) : vanillaTicks;
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
