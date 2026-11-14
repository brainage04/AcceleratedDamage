package io.github.brainage04.accelerateddamage.mixin.entity.attack.brain;

import io.github.brainage04.accelerateddamage.util.CompressedTicks;
import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.monster.breeze.Shoot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Shoot.class)
public abstract class BreezeShootMixin {
    /**
     * {@code BREEZE_SHOOT_CHARGING}: the 15-tick inhale before the wind charge. Rounded down so the shot still
     * lands before the scaled behavior timeout ends the attack.
     */
    @ModifyArg(
            method = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/breeze/Breeze;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;setMemoryWithExpiry(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;J)V"
            ),
            index = 2
    )
    private long acceleratedDamage$shortenShootCharging(long vanillaTicks) {
        return VirtualTime.isEnabled() ? CompressedTicks.brainTimerNoLater(vanillaTicks) : vanillaTicks;
    }

    /** {@code BREEZE_SHOOT_RECOVERING} after a shot and {@code BREEZE_SHOOT_COOLDOWN} after the attack ends. */
    @ModifyArg(
            method = {
                    "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/breeze/Breeze;J)V",
                    "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/breeze/Breeze;J)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/Brain;setMemoryWithExpiry(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;J)V"
            ),
            index = 2
    )
    private long acceleratedDamage$shortenShootRecovery(long vanillaTicks) {
        return VirtualTime.isEnabled() ? CompressedTicks.brainTimer(vanillaTicks) : vanillaTicks;
    }
}
