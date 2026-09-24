package io.github.brainage04.accelerateddamage.mixin.entity;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.bee.Bee;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Bee.class)
public abstract class BeeMixin {
    @Shadow
    private int underWaterTicks;

    @Shadow
    private int timeSinceSting;

    /** Bees start drowning after 20 ticks underwater. */
    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/animal/bee/Bee;underWaterTicks:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 0
            )
    )
    private void acceleratedDamage$countUnderwaterTicksFaster(Bee self, int vanillaValue) {
        underWaterTicks = VirtualTime.isEnabled(self.level())
                ? vanillaValue + VirtualTime.LAST_OFFSET
                : vanillaValue;
    }

    /**
     * After stinging, every fifth tick a bee dies with chance {@code 1 / (1200 - timeSinceSting)}.
     * Replays that roll for the first nine virtual ticks; vanilla performs the last one.
     */
    @Redirect(
            method = "customServerAiStep",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/animal/bee/Bee;timeSinceSting:I",
                    opcode = Opcodes.PUTFIELD
            )
    )
    private void acceleratedDamage$ageStingFaster(Bee self, int vanillaValue) {
        if (!(self.level() instanceof ServerLevel level) || !VirtualTime.isEnabled(level)) {
            timeSinceSting = vanillaValue;
            return;
        }
        int ticks = vanillaValue - 1;
        for (int offset = 0; offset < VirtualTime.LAST_OFFSET; offset++) {
            ticks++;
            if (ticks % 5 == 0 && self.getRandom().nextInt(Mth.clamp(1200 - ticks, 1, 1200)) == 0) {
                int previousOffset = VirtualTime.enter(offset);
                try {
                    self.hurtServer(level, self.damageSources().generic(), self.getHealth());
                } finally {
                    VirtualTime.exit(previousOffset);
                }
            }
        }
        timeSinceSting = ticks + 1;
    }
}
