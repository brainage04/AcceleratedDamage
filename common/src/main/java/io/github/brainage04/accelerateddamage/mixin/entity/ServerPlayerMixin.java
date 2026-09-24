package io.github.brainage04.accelerateddamage.mixin.entity;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Redirect(
            method = "tick",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/server/level/ServerPlayer;invulnerableTime:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void acceleratedDamage$decayInvulnerabilityFaster(ServerPlayer self, int vanillaValue) {
        self.invulnerableTime = VirtualTime.isEnabled(self.level())
                ? Math.max(0, vanillaValue - VirtualTime.LAST_OFFSET)
                : vanillaValue;
    }

    /** Natural regeneration, starvation and exhaustion all advance through one virtual tick per call. */
    @Redirect(
            method = "doTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/food/FoodData;tick(Lnet/minecraft/server/level/ServerPlayer;)V"
            )
    )
    private void acceleratedDamage$tickFoodFaster(FoodData foodData, ServerPlayer player) {
        if (!VirtualTime.isEnabled(player.level())) {
            foodData.tick(player);
            return;
        }
        for (int offset = 0; offset < VirtualTime.ACCELERATION; offset++) {
            int previousOffset = VirtualTime.enter(offset);
            try {
                foodData.tick(player);
            } finally {
                VirtualTime.exit(previousOffset);
            }
        }
    }

    /** Peaceful regeneration: health every 20 ticks and food every 10 ticks, on the compressed timeline. */
    @ModifyConstant(method = "tickRegeneration", constant = {@Constant(intValue = 20), @Constant(intValue = 10)})
    private int acceleratedDamage$shortenPeacefulRegeneration(int original) {
        return VirtualTime.isEnabled(((ServerPlayer) (Object) this).level())
                ? original / VirtualTime.ACCELERATION
                : original;
    }
}
