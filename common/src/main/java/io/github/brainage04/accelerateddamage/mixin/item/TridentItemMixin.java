package io.github.brainage04.accelerateddamage.mixin.item;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import io.github.brainage04.accelerateddamage.util.ServerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(TridentItem.class)
public abstract class TridentItemMixin {
    @ModifyConstant(
            method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Z",
            constant = @Constant(intValue = 10)
    )
    private int acceleratedDamage$useConfiguredThrowThreshold(
            int original,
            ItemStack stack,
            Level level,
            LivingEntity entity,
            int remainingTime
    ) {
        MinecraftServer server = ServerContext.get();
        if (server == null || !server.getGameRules().get(ModGameRules.INSTANT_SHOOT)) {
            return original;
        }
        // Riptide launches the player by movement only the client applies: the server's push is not sent back to the
        // pushing player (ServerEntity.java:177-195, 26.2), and a vanilla client keeps the 10-tick minimum
        // (TridentItem.java:65), so a shorter release would spin the player in place on a dedicated server.
        return EnchantmentHelper.getTridentSpinAttackStrength(stack, entity) > 0.0F ? original : 0;
    }
}
