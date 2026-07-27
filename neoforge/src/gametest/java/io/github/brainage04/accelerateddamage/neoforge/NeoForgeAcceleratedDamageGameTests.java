package io.github.brainage04.accelerateddamage.neoforge;

import io.github.brainage04.accelerateddamage.AcceleratedDamage;
import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

@EventBusSubscriber(modid = AcceleratedDamage.MOD_ID)
public final class NeoForgeAcceleratedDamageGameTests {
    private NeoForgeAcceleratedDamageGameTests() {
    }

    @SubscribeEvent
    public static void registerTestFunctions(RegisterEvent event) {
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), Identifier.fromNamespaceAndPath(AcceleratedDamage.MOD_ID, "instant_shoot_changes_bow_and_trident_charge"), () -> NeoForgeAcceleratedDamageGameTests::instantShootChangesBowAndTridentCharge);
        event.register(BuiltInRegistries.TEST_FUNCTION.key(), Identifier.fromNamespaceAndPath(AcceleratedDamage.MOD_ID, "disable_i_frames_allows_consecutive_melee_damage"), () -> NeoForgeAcceleratedDamageGameTests::disableIFramesAllowsConsecutiveMeleeDamage);
    }

    private static void instantShootChangesBowAndTridentCharge(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();
        int vanillaThreshold = TridentItem.THROW_THRESHOLD_TIME;
        try {
            rules.set(ModGameRules.INSTANT_SHOOT, false, server);
            assertTrue(BowItem.getPowerForTime(1) < 1.0F, "Expected vanilla bow charge before enabling instant shooting");
            rules.set(ModGameRules.INSTANT_SHOOT, true, server);
            assertTrue(Float.compare(1.0F, BowItem.getPowerForTime(1)) == 0, "Expected the bow to charge instantly");
            assertEquals(0, TridentItem.THROW_THRESHOLD_TIME, "Expected the trident throw threshold to be zero");
        } finally {
            rules.set(ModGameRules.INSTANT_SHOOT, false, server);
        }
        assertEquals(vanillaThreshold, TridentItem.THROW_THRESHOLD_TIME, "Expected the trident threshold to return to its vanilla value");
        context.succeed();
    }

    private static void disableIFramesAllowsConsecutiveMeleeDamage(GameTestHelper context) {
        ServerLevel level = context.getLevel();
        MinecraftServer server = level.getServer();
        GameRules rules = level.getGameRules();
        Zombie attacker = createZombie(level);
        Zombie victim = createZombie(level);
        try {
            rules.set(ModGameRules.DISABLE_IFRAMES, true, server);
            assertTrue(victim.hurtServer(level, victim.damageSources().mobAttack(attacker), 1.0F), "Expected the first melee hit to deal damage");
            float afterFirst = victim.getHealth();
            assertTrue(victim.hurtServer(level, victim.damageSources().mobAttack(attacker), 1.0F), "Expected consecutive melee hit to bypass invincibility frames");
            assertTrue(victim.getHealth() < afterFirst, "Expected consecutive melee hit to reduce health again");
        } finally {
            rules.set(ModGameRules.DISABLE_IFRAMES, false, server);
        }
        context.succeed();
    }

    private static Zombie createZombie(ServerLevel level) {
        Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        if (zombie == null) throw new AssertionError("Expected to create a zombie");
        return zombie;
    }
    private static void assertTrue(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void assertEquals(int expected, int actual, String message) { if (expected != actual) throw new AssertionError(message + ": expected " + expected + ", found " + actual); }
}
