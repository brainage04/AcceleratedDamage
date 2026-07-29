package io.github.brainage04.accelerateddamage.mixin.item;

import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(TridentItem.class)
public abstract class TridentItemMixin {
    @ModifyConstant(
            method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Z",
            constant = @Constant(intValue = 10)
    )
    private int acceleratedDamage$useCurrentThrowThreshold(int original) {
        return TridentItem.THROW_THRESHOLD_TIME;
    }
}
