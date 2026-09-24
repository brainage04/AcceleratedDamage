package io.github.brainage04.accelerateddamage.mixin.entity.attack.mob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.entity.monster.illager.SpellcasterIllager$SpellcasterUseSpellGoal")
public interface SpellcasterUseSpellGoalInvoker {
    @Invoker("getCastWarmupTime")
    int acceleratedDamage$getCastWarmupTime();

    @Invoker("getCastingTime")
    int acceleratedDamage$getCastingTime();

    @Invoker("getCastingInterval")
    int acceleratedDamage$getCastingInterval();
}
