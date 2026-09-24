package io.github.brainage04.accelerateddamage.mixin.entity.attack.goal;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mob bow draw and crossbow charge. Advancing the use countdown makes {@code getTicksUsingItem()}
 * count virtual ticks, so bow power ({@code BowItem.getPowerForTime}) and crossbow charge
 * ({@code CrossbowItem.getChargeDuration}) come out right for goal and brain users alike.
 * Limited to non-player mobs using a {@link ProjectileWeaponItem}: players, drinking, eating,
 * tridents and kinetic (spear) weapons keep vanilla item-use speed.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityItemUseMixin {
    @Shadow
    protected ItemStack useItem;

    @Shadow
    protected int useItemRemaining;

    /**
     * Item-use countdown {@code useItemRemaining} ({@code --useItemRemaining == 0}): the read
     * feeding the decrement is lowered by a further 9. From a positive value it stops at 1, so the
     * countdown still lands on 0 exactly; past 0 (held longer than the use duration) it keeps going.
     */
    @Redirect(
            method = "updateUsingItem",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/LivingEntity;useItemRemaining:I",
                    opcode = Opcodes.GETFIELD
            )
    )
    private int acceleratedDamage$drawWeaponFaster(LivingEntity self) {
        if (!(self instanceof Mob)
                || !(this.useItem.getItem() instanceof ProjectileWeaponItem)
                || this.useItem.has(DataComponents.KINETIC_WEAPON)
                || !VirtualTime.isEnabled(self.level())) {
            return this.useItemRemaining;
        }
        return this.useItemRemaining > 0
                ? Math.max(1, this.useItemRemaining - VirtualTime.LAST_OFFSET)
                : this.useItemRemaining - VirtualTime.LAST_OFFSET;
    }
}
