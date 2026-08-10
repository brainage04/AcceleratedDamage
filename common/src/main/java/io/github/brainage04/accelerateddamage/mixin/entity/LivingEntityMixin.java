package io.github.brainage04.accelerateddamage.mixin.entity;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.DamageAccelerationContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    private static final int ACCELERATION = 10;

    @Inject(
            method = "hurtServer",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;invulnerableTime:I",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 0
            )
    )
    private void acceleratedDamage$disableInvincibilityFrames(
            ServerLevel level,
            DamageSource source,
            float amount,
            CallbackInfoReturnable<Boolean> cir
    ) {
        boolean acceleratedDamage = level.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING)
                && (DamageAccelerationContext.isActive()
                || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypeTags.IS_FREEZING));
        if (!level.getGameRules().get(ModGameRules.DISABLE_IFRAMES) && !acceleratedDamage) {
            return;
        }

        LivingEntity self = (LivingEntity) (Object) this;
        self.invulnerableTime = Math.min(self.invulnerableTime, 10);
        self.hurtDuration = 0;
        self.hurtTime = 0;
    }

    @ModifyConstant(method = "aiStep", constant = @Constant(intValue = 40))
    private int acceleratedDamage$shortenFreezeInterval(int original) {
        LivingEntity self = (LivingEntity) (Object) this;
        return self.level() instanceof ServerLevel level
                && level.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING)
                ? original / ACCELERATION
                : original;
    }

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setTicksFrozen(I)V"
            )
    )
    private void acceleratedDamage$decrementFreezeFaster(LivingEntity entity, int vanillaValue) {
        if (entity.level() instanceof ServerLevel level
                && level.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING)) {
            entity.setTicksFrozen(Math.max(0, entity.getTicksFrozen() - (2 * ACCELERATION)));
            return;
        }
        entity.setTicksFrozen(vanillaValue);
    }
}
