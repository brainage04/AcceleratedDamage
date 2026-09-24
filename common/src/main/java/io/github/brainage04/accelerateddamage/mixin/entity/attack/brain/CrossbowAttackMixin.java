package io.github.brainage04.accelerateddamage.mixin.entity.attack.brain;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.ai.behavior.CrossbowAttack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CrossbowAttack.class)
public abstract class CrossbowAttackMixin {
    @Shadow
    private int attackDelay;

    /**
     * {@code attackDelay}: the 20-39 tick pause between loading and firing, counted down once per tick. Vanilla
     * fires one tick after it reaches zero; it steps by ten here and zeroes as soon as that firing tick falls
     * inside the next server tick, so the shot lands in the server tick that contains the vanilla shot.
     */
    @Redirect(
            method = "crossbowAttack",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/entity/ai/behavior/CrossbowAttack;attackDelay:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 1
            )
    )
    private void acceleratedDamage$countDownAttackDelayFaster(CrossbowAttack self, int vanillaValue) {
        if (!VirtualTime.isEnabled()) {
            attackDelay = vanillaValue;
            return;
        }
        int remaining = vanillaValue - VirtualTime.LAST_OFFSET;
        attackDelay = remaining >= VirtualTime.ACCELERATION ? remaining : 0;
    }
}
