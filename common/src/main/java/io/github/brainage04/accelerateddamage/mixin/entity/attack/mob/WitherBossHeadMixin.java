package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Side heads: {@code nextHeadUpdate = tickCount + 10 + nextInt(10)} while idle and
 * {@code tickCount + 40 + nextInt(20)} after a skull. The fixed part moves into the random call,
 * which returns the whole delay divided by 10 and rounded, so the random spread is kept.
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossHeadMixin {
    @ModifyConstant(method = "customServerAiStep", constant = @Constant(intValue = 10, ordinal = 1))
    private int acceleratedDamage$dropIdleBase(int original) {
        return VirtualTime.isEnabled(((WitherBoss) (Object) this).level()) ? 0 : original;
    }

    @Redirect(
            method = "customServerAiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I", ordinal = 0)
    )
    private int acceleratedDamage$idleDelay(RandomSource random, int bound) {
        return acceleratedDamage$delay(random, 10, bound);
    }

    @ModifyConstant(method = "customServerAiStep", constant = @Constant(intValue = 40, ordinal = 0))
    private int acceleratedDamage$dropShotBase(int original) {
        return VirtualTime.isEnabled(((WitherBoss) (Object) this).level()) ? 0 : original;
    }

    @Redirect(
            method = "customServerAiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I", ordinal = 1)
    )
    private int acceleratedDamage$shotDelay(RandomSource random, int bound) {
        return acceleratedDamage$delay(random, 40, bound);
    }

    private int acceleratedDamage$delay(RandomSource random, int base, int bound) {
        if (!VirtualTime.isEnabled(((WitherBoss) (Object) this).level())) {
            return random.nextInt(bound);
        }
        return (base + random.nextInt(bound) + VirtualTime.ACCELERATION / 2) / VirtualTime.ACCELERATION;
    }
}
