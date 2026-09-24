package io.github.brainage04.accelerateddamage.mixin.entity;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AreaEffectCloud.class)
public abstract class AreaEffectCloudMixin {
    @Shadow
    protected abstract void serverTick(ServerLevel level);

    /**
     * Lingering potion and dragon's breath clouds keep all their timing on {@code tickCount}:
     * wait time, lifetime, radius change per tick, the 5-tick victim scan and the re-application delay.
     * The server tick runs once per virtual tick, advancing {@code tickCount} between runs.
     */
    @Redirect(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/AreaEffectCloud;serverTick(Lnet/minecraft/server/level/ServerLevel;)V"
            )
    )
    private void acceleratedDamage$serverTickFaster(AreaEffectCloud self, ServerLevel level) {
        if (!VirtualTime.isEnabled(level)) {
            serverTick(level);
            return;
        }
        for (int offset = 0; offset < VirtualTime.ACCELERATION && !self.isRemoved(); offset++) {
            if (offset > 0) {
                self.tickCount++;
            }
            int previousOffset = VirtualTime.enter(offset);
            try {
                serverTick(level);
            } finally {
                VirtualTime.exit(previousOffset);
            }
        }
    }
}
