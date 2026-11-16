package io.github.brainage04.accelerateddamage.util;

import io.github.brainage04.accelerateddamage.AcceleratedDamage;
import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Gives a player ten times its vanilla {@code ATTACK_SPEED} while {@code faster_effect_ticking} is
 * on.
 *
 * <p>The attack cooldown is {@code 20 / ATTACK_SPEED} ticks ({@code Player.java:1816-1818}, 26.2),
 * and {@code ATTACK_SPEED} is client-syncable ({@code Attributes.java:19-21}), so the modifier
 * reaches a vanilla client and both sides compute the same shorter delay: the attack indicator and
 * the hand-swap animation read it, and so does a spear's stab gate ({@code Minecraft.java:1627}).
 * Dividing the delay inside {@code Player#getCurrentItemAttackStrengthDelay} instead would only
 * reach the server, because a client's level is a {@code ClientLevel} and {@link
 * VirtualTime#isEnabled} is false there.
 *
 * <p>The modifier is transient, so no player data holds it and {@code ServerPlayer.restoreFrom}
 * does not copy it onto a respawned player ({@code ServerPlayer.java:1606-1608}); that player's
 * first tick adds it again.
 */
public final class AttackSpeedSync {
    private static final Identifier MODIFIER_ID =
            Identifier.fromNamespaceAndPath(
                    AcceleratedDamage.MOD_ID, "faster_effect_ticking_attack_speed");

    // ADD_MULTIPLIED_TOTAL multiplies by 1 + amount (AttributeInstance.java:157-159, 26.2), so
    // ACCELERATION - 1 is exactly a tenfold value. The attribute's 1024 cap (Attributes.java:19-21)
    // is far above the largest result, the empty hand's 40.
    //
    // One shared instance, because addOrUpdateTransientModifier marks the attribute dirty only when
    // the modifier it replaces is a different object (AttributeInstance.java:84-89). A new instance
    // each tick would send one attribute packet per player per tick, forever.
    private static final AttributeModifier MODIFIER =
            new AttributeModifier(
                    MODIFIER_ID,
                    VirtualTime.ACCELERATION - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    private AttackSpeedSync() {}

    public static void sync(ServerPlayer player) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (player.level().getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING)) {
            attackSpeed.addOrUpdateTransientModifier(MODIFIER);
        } else {
            attackSpeed.removeModifier(MODIFIER_ID);
        }
    }

    public static boolean isApplied(ServerPlayer player) {
        return player.getAttribute(Attributes.ATTACK_SPEED).getModifier(MODIFIER_ID) != null;
    }
}
