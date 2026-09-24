package io.github.brainage04.accelerateddamage.mixin.entity.attack.goal;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Pillager crossbow. The charge itself ({@code getTicksUsingItem() >= getChargeDuration}) is
 * accelerated by {@link LivingEntityItemUseMixin}. The {@code mob} field is not shadowed because
 * NeoForge widens its erased type from {@code Monster} to {@code Mob}; goals only tick on the
 * server, so the server-global rule check is used instead.
 */
@Mixin(RangedCrossbowAttackGoal.class)
public abstract class RangedCrossbowAttackGoalMixin {
    @Shadow
    private int attackDelay;

    /** Virtual ticks the accelerated charge ran past the charge duration before it was released. */
    @Unique
    private int acceleratedDamage$chargeOverrun;

    /** Charge check {@code pullTime >= getChargeDuration}: records the overrun for the aim delay. */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/CrossbowItem;getChargeDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I"
            )
    )
    private int acceleratedDamage$measureChargeOverrun(ItemStack crossbow, LivingEntity user) {
        int chargeDuration = CrossbowItem.getChargeDuration(crossbow, user);
        this.acceleratedDamage$chargeOverrun = Math.max(0, user.getTicksUsingItem() - chargeDuration);
        return chargeDuration;
    }

    /**
     * Post-charge aim delay {@code attackDelay = 20 + nextInt(20)}. Besides the charge overrun, the
     * READY_TO_ATTACK and UNCHARGED states each hold for one server tick (10 virtual ticks) where
     * vanilla spends 1, so both overheads come off the delay, which keeps at least 1. The reload
     * cycle then ends in the server tick vanilla's would, unless the delay bottoms out.
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/ai/goal/RangedCrossbowAttackGoal;attackDelay:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0
            )
    )
    private void acceleratedDamage$shortenAimDelay(RangedCrossbowAttackGoal<?> self, int vanillaDelay) {
        this.attackDelay = VirtualTime.isEnabled()
                ? Math.max(1, vanillaDelay - 2 * VirtualTime.LAST_OFFSET - this.acceleratedDamage$chargeOverrun)
                : vanillaDelay;
    }

    /** Aim delay countdown {@code attackDelay--} (checked {@code == 0}), stepped by 10 and floored at 0. */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/ai/goal/RangedCrossbowAttackGoal;attackDelay:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 1
            )
    )
    private void acceleratedDamage$aimFaster(RangedCrossbowAttackGoal<?> self, int vanillaValue) {
        this.attackDelay = vanillaValue > 0 && VirtualTime.isEnabled()
                ? Math.max(0, vanillaValue - VirtualTime.LAST_OFFSET)
                : vanillaValue;
    }
}
