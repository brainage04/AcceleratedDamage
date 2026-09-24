package io.github.brainage04.accelerateddamage.mixin.entity;

import io.github.brainage04.accelerateddamage.util.EffectTickCadence;
import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.Entity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    private int remainingFireTicks;

    @Shadow
    public abstract void setRemainingFireTicks(int remainingFireTicks);

    @Redirect(
            method = "baseTick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/Entity;remainingFireTicks:I",
                    opcode = Opcodes.GETFIELD,
                    ordinal = 1
            )
    )
    private int acceleratedDamage$includeCrossedFireDamageTick(Entity entity) {
        if (VirtualTime.isEnabled(entity.level())
                && EffectTickCadence.crossesScheduledTick(remainingFireTicks, 20, VirtualTime.ACCELERATION)) {
            return remainingFireTicks - Math.floorMod(remainingFireTicks, 20);
        }
        return remainingFireTicks;
    }

    @Redirect(
            method = "baseTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setRemainingFireTicks(I)V"
            )
    )
    private void acceleratedDamage$decrementFireFaster(Entity entity, int vanillaValue) {
        if (VirtualTime.isEnabled(entity.level())) {
            setRemainingFireTicks(Math.max(0, remainingFireTicks - VirtualTime.ACCELERATION));
            return;
        }
        setRemainingFireTicks(vanillaValue);
    }
}
