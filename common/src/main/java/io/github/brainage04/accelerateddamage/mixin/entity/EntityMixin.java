package io.github.brainage04.accelerateddamage.mixin.entity;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class EntityMixin {
    private static final int ACCELERATION = 10;

    @Shadow
    private int remainingFireTicks;

    @Shadow
    public abstract void setRemainingFireTicks(int remainingFireTicks);

    @ModifyConstant(method = "baseTick", constant = @Constant(intValue = 20))
    private int acceleratedDamage$shortenFireInterval(int original) {
        Entity self = (Entity) (Object) this;
        return self.level() instanceof ServerLevel level
                && level.getGameRules().get(ModGameRules.FASTER_EFFECT_DAMAGE_TICKING)
                ? original / ACCELERATION
                : original;
    }

    @Redirect(
            method = "baseTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setRemainingFireTicks(I)V"
            )
    )
    private void acceleratedDamage$decrementFireFaster(Entity entity, int vanillaValue) {
        if (entity.level() instanceof ServerLevel level
                && level.getGameRules().get(ModGameRules.FASTER_EFFECT_DAMAGE_TICKING)) {
            setRemainingFireTicks(remainingFireTicks - ACCELERATION);
            return;
        }
        setRemainingFireTicks(vanillaValue);
    }
}
