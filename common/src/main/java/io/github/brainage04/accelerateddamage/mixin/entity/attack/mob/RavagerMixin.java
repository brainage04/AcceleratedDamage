package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import io.github.brainage04.accelerateddamage.util.VirtualTime;
import net.minecraft.world.entity.monster.Ravager;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Ravager.class)
public abstract class RavagerMixin {
    @Shadow
    private int roarTick;

    @Shadow
    private int attackTick;

    @Shadow
    private int stunnedTick;

    /** Roar {@code roarTick--} from 20; the damaging roar goes off at exactly 10. */
    @Redirect(
            method = "aiStep",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/monster/Ravager;roarTick:I", opcode = Opcodes.PUTFIELD, ordinal = 0)
    )
    private void acceleratedDamage$roarFaster(Ravager self, int vanillaValue) {
        roarTick = acceleratedDamage$countDown(self, vanillaValue);
    }

    /**
     * Post-attack pause {@code attackTick--} from 10. While it is above 0 the ravager is immobile and its
     * goals (including the melee cooldown) do not tick: 9 frozen ticks in vanilla. Stepping by 9 keeps one
     * frozen server tick, so the next bite comes 30 virtual ticks later, as in vanilla.
     */
    @Redirect(
            method = "aiStep",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/monster/Ravager;attackTick:I", opcode = Opcodes.PUTFIELD, ordinal = 0)
    )
    private void acceleratedDamage$recoverFaster(Ravager self, int vanillaValue) {
        attackTick = VirtualTime.isEnabled(self.level())
                ? Math.max(0, vanillaValue - (VirtualTime.LAST_OFFSET - 1))
                : vanillaValue;
    }

    /** Stun {@code stunnedTick--} from 40 after a shield block, which ends in the roar. */
    @Redirect(
            method = "aiStep",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/monster/Ravager;stunnedTick:I", opcode = Opcodes.PUTFIELD, ordinal = 0)
    )
    private void acceleratedDamage$unstunFaster(Ravager self, int vanillaValue) {
        stunnedTick = acceleratedDamage$countDown(self, vanillaValue);
    }

    private static int acceleratedDamage$countDown(Ravager self, int vanillaValue) {
        return VirtualTime.isEnabled(self.level())
                ? Math.max(0, vanillaValue - VirtualTime.LAST_OFFSET)
                : vanillaValue;
    }
}
