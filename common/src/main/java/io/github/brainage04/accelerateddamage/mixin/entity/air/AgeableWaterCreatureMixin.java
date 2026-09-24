package io.github.brainage04.accelerateddamage.mixin.entity.air;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AgeableWaterCreature.class)
public abstract class AgeableWaterCreatureMixin {
    @Shadow
    protected abstract void handleAirSupply(int preTickAirSupply);

    /** Runs the drowning-out-of-water step once per virtual tick; the first step uses the vanilla pre-tick air supply. */
    @Redirect(
            method = "baseTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/AgeableWaterCreature;handleAirSupply(I)V"
            )
    )
    private void acceleratedDamage$handleAirSupplyFaster(AgeableWaterCreature self, int preTickAirSupply) {
        if (!VirtualTime.isEnabled(self.level())) {
            handleAirSupply(preTickAirSupply);
            return;
        }
        for (int offset = 0; offset < VirtualTime.ACCELERATION; offset++) {
            int previousOffset = VirtualTime.enter(offset);
            try {
                handleAirSupply(preTickAirSupply);
            } finally {
                VirtualTime.exit(previousOffset);
            }
            preTickAirSupply = self.getAirSupply();
        }
    }
}
