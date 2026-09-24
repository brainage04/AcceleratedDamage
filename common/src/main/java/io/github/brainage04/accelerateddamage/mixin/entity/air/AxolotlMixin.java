package io.github.brainage04.accelerateddamage.mixin.entity.air;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Axolotl.class)
public abstract class AxolotlMixin {
    @Shadow
    protected abstract void handleAirSupply(ServerLevel level, int preTickAirSupply);

    /** Runs the drying-out step once per virtual tick; the first step uses the vanilla pre-tick air supply. */
    @Redirect(
            method = "baseTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/axolotl/Axolotl;handleAirSupply(Lnet/minecraft/server/level/ServerLevel;I)V"
            )
    )
    private void acceleratedDamage$handleAirSupplyFaster(Axolotl self, ServerLevel level, int preTickAirSupply) {
        if (!VirtualTime.isEnabled(self.level())) {
            handleAirSupply(level, preTickAirSupply);
            return;
        }
        for (int offset = 0; offset < VirtualTime.ACCELERATION; offset++) {
            int previousOffset = VirtualTime.enter(offset);
            try {
                handleAirSupply(level, preTickAirSupply);
            } finally {
                VirtualTime.exit(previousOffset);
            }
            preTickAirSupply = self.getAirSupply();
        }
    }
}
