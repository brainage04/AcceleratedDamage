package io.github.brainage04.accelerateddamage.mixin.entity;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    protected abstract int decreaseAirSupply(int currentSupply);

    @Shadow
    protected abstract int increaseAirSupply(int currentSupply);

    @Shadow
    protected abstract boolean shouldTakeDrowningDamage();

    /**
     * On the compressed timeline {@code invulnerableTime} is measured in virtual ticks as of the end
     * of the current server tick. A hit simulated at an earlier virtual tick sees the extra virtual
     * ticks that have not elapsed yet, and records its fresh window relative to the end of the tick.
     */
    @Redirect(
            method = "hurtServer",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;invulnerableTime:I",
                    opcode = Opcodes.GETFIELD
            )
    )
    private int acceleratedDamage$readInvulnerability(LivingEntity self) {
        if (self.level() instanceof ServerLevel level && level.getGameRules().get(ModGameRules.DISABLE_IFRAMES)) {
            self.invulnerableTime = Math.min(self.invulnerableTime, 10);
            self.hurtDuration = 0;
            self.hurtTime = 0;
            return self.invulnerableTime;
        }
        return VirtualTime.isEnabled(self.level())
                ? self.invulnerableTime + VirtualTime.remainingInTick()
                : self.invulnerableTime;
    }

    @Redirect(
            method = "hurtServer",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;invulnerableTime:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void acceleratedDamage$writeInvulnerability(LivingEntity self, int value) {
        self.invulnerableTime = VirtualTime.isEnabled(self.level())
                ? Math.max(0, value - VirtualTime.remainingInTick())
                : value;
    }

    @Redirect(
            method = "baseTick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;invulnerableTime:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void acceleratedDamage$decayInvulnerabilityFaster(LivingEntity self, int vanillaValue) {
        self.invulnerableTime = VirtualTime.isEnabled(self.level())
                ? Math.max(0, vanillaValue - VirtualTime.LAST_OFFSET)
                : vanillaValue;
    }

    @Redirect(
            method = "baseTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;decreaseAirSupply(I)I"
            )
    )
    private int acceleratedDamage$drownFaster(LivingEntity self, int supply) {
        if (!(self.level() instanceof ServerLevel level) || !VirtualTime.isEnabled(level)) {
            return decreaseAirSupply(supply);
        }
        // Replays the vanilla drowning step for all but the last virtual tick, which vanilla runs itself.
        for (int offset = 0; offset < VirtualTime.LAST_OFFSET; offset++) {
            supply = decreaseAirSupply(supply);
            self.setAirSupply(supply);
            if (shouldTakeDrowningDamage()) {
                supply = 0;
                self.setAirSupply(0);
                level.broadcastEntityEvent(self, (byte) 67);
                int previousOffset = VirtualTime.enter(offset);
                try {
                    self.hurtServer(level, self.damageSources().drown(), 2.0F);
                } finally {
                    VirtualTime.exit(previousOffset);
                }
            }
        }
        return decreaseAirSupply(supply);
    }

    @Redirect(
            method = "baseTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;increaseAirSupply(I)I"
            )
    )
    private int acceleratedDamage$refillAirFaster(LivingEntity self, int supply) {
        if (!VirtualTime.isEnabled(self.level())) {
            return increaseAirSupply(supply);
        }
        for (int tick = 0; tick < VirtualTime.ACCELERATION; tick++) {
            supply = increaseAirSupply(supply);
        }
        return supply;
    }

    @ModifyConstant(method = "aiStep", constant = @Constant(intValue = 40))
    private int acceleratedDamage$shortenFreezeInterval(int original) {
        return VirtualTime.isEnabled(((LivingEntity) (Object) this).level())
                ? original / VirtualTime.ACCELERATION
                : original;
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setTicksFrozen(I)V"
            )
    )
    private void acceleratedDamage$thawFaster(LivingEntity entity, int vanillaValue) {
        if (VirtualTime.isEnabled(entity.level())) {
            entity.setTicksFrozen(Math.max(0, entity.getTicksFrozen() - (2 * VirtualTime.ACCELERATION)));
            return;
        }
        entity.setTicksFrozen(vanillaValue);
    }
}
