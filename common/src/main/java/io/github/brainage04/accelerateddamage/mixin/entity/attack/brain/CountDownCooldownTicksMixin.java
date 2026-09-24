package io.github.brainage04.accelerateddamage.mixin.entity.attack.brain;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.CountDownCooldownTicks;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CountDownCooldownTicks.class)
public abstract class CountDownCooldownTicksMixin {
    @Shadow
    @Final
    private MemoryModuleType<Integer> cooldownTicks;

    /**
     * {@code RAM_COOLDOWN_TICKS} (goat ram) and {@code CHARGE_COOLDOWN_TICKS} (nautilus and zombie nautilus dash)
     * count down one per tick. Vanilla has already stepped once; this adds the other nine virtual ticks, and erases
     * the memory now when vanilla would have erased it (one tick after reaching zero) within this server tick.
     */
    @Inject(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;J)V", at = @At("TAIL"))
    private void acceleratedDamage$countDownAttackCooldownFaster(ServerLevel level, LivingEntity body, long timestamp, CallbackInfo ci) {
        if (cooldownTicks != MemoryModuleType.RAM_COOLDOWN_TICKS && cooldownTicks != MemoryModuleType.CHARGE_COOLDOWN_TICKS
                || !VirtualTime.isEnabled(level)) {
            return;
        }
        Brain<?> brain = body.getBrain();
        Optional<Integer> remaining = brain.getMemory(cooldownTicks);
        if (remaining.isEmpty()) {
            return;
        }
        int value = remaining.get() - VirtualTime.LAST_OFFSET;
        if (value < 0) {
            brain.eraseMemory(cooldownTicks);
        } else {
            brain.setMemory(cooldownTicks, value);
        }
    }
}
