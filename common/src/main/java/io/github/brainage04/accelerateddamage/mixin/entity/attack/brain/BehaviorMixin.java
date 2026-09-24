package io.github.brainage04.accelerateddamage.mixin.entity.attack.brain;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.monster.breeze.Shoot;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Behavior.class)
public abstract class BehaviorMixin {
    @Shadow
    private long endTimestamp;

    /**
     * The run length of attack behaviors whose timeout ends the attack: the warden's 60-tick sonic boom and the
     * breeze's 20-tick shot. Other behaviors keep their vanilla timeout.
     */
    @Inject(
            method = "tryStart",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/ai/behavior/Behavior;endTimestamp:J",
                    opcode = Opcodes.PUTFIELD,
                    shift = At.Shift.AFTER
            )
    )
    private void acceleratedDamage$shortenAttackDuration(ServerLevel level, LivingEntity body, long timestamp, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if ((self instanceof SonicBoom || self instanceof Shoot) && VirtualTime.isEnabled(level)) {
            endTimestamp = timestamp + acceleratedDamage$scaleTimer(endTimestamp - timestamp);
        }
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
