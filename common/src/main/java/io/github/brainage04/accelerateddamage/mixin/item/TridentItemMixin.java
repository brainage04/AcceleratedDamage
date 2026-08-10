package io.github.brainage04.accelerateddamage.mixin.item;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.ServerContext;
import net.minecraft.server.MinecraftServer;
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
    private int acceleratedDamage$useConfiguredThrowThreshold(int original) {
        MinecraftServer server = ServerContext.get();
        return server != null && server.getGameRules().get(ModGameRules.INSTANT_SHOOT) ? 0 : original;
    }
}
