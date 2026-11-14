package io.github.brainage04.accelerateddamage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

/**
 * Compares the compressed timeline with vanilla's, one virtual tick per vanilla tick. Vanilla sources cited from the
 * 26.3 decompilation (~/agent-work/port263-diff/dec/server-26.3).
 */
class CompressedTicksTest {
    private static final int ACCELERATION = VirtualTime.ACCELERATION;
    private static final int LAST_OFFSET = VirtualTime.LAST_OFFSET;

    /**
     * Server ticks from setting a timer to its event. A memory set with a time to live of {@code ttl} is counted
     * down once per brain tick and cleared on the tick after it reaches zero, before behaviours run
     * (MemorySlot.java:16-24, Brain.java:384-389); a behaviour started with a timeout of {@code ttl} has
     * {@code endTimestamp = start + ttl} and stops once the game time passes it (Behavior.java:48,84-86).
     */
    private static long eventAfter(long ttl) {
        return ttl + 1;
    }

    @Property
    void brainTimerFiresOnTheServerTickNearestVanilla(@ForAll @LongRange(max = 1_000_000) long vanillaTicks) {
        long vanillaVirtualTicks = eventAfter(vanillaTicks);
        long serverTicks = eventAfter(CompressedTicks.brainTimer(vanillaTicks));

        assertTrue(serverTicks >= 1);
        long error = Math.abs(serverTicks * ACCELERATION - vanillaVirtualTicks);
        if (serverTicks > 1) {
            assertTrue(error <= Math.abs((serverTicks - 1) * ACCELERATION - vanillaVirtualTicks),
                    "an earlier server tick is nearer: " + serverTicks);
        }
        assertTrue(error <= Math.abs((serverTicks + 1) * ACCELERATION - vanillaVirtualTicks),
                "a later server tick is nearer: " + serverTicks);
    }

    @Property
    void brainTimerNoLaterNeverFiresAfterVanillaOnceVanillaIsAServerTickAway(
            @ForAll @LongRange(max = 1_000_000) long vanillaTicks) {
        long vanillaVirtualTicks = eventAfter(vanillaTicks);
        long serverTicks = eventAfter(CompressedTicks.brainTimerNoLater(vanillaTicks));

        if (vanillaVirtualTicks < ACCELERATION) {
            assertEquals(1, serverTicks);
        } else {
            assertTrue(serverTicks * ACCELERATION <= vanillaVirtualTicks);
            assertTrue(vanillaVirtualTicks < (serverTicks + 1) * ACCELERATION);
        }
    }

    @Property
    void brainTimersKeepTheOrderOfVanillaTimers(
            @ForAll @LongRange(max = 1_000_000) long shorter, @ForAll @LongRange(max = 1_000_000) long longer) {
        if (shorter > longer) {
            long swap = shorter;
            shorter = longer;
            longer = swap;
        }
        assertTrue(CompressedTicks.brainTimer(shorter) <= CompressedTicks.brainTimer(longer));
        assertTrue(CompressedTicks.brainTimerNoLater(shorter) <= CompressedTicks.brainTimerNoLater(longer));
        assertTrue(CompressedTicks.brainTimerNoLater(longer) <= CompressedTicks.brainTimer(longer));
    }

    @Test
    void scaledAttacksStillLandBeforeTheirBehaviourTimesOut() {
        // Breeze Shoot: 15-tick charge, then a 20-tick timeout (Shoot.java:24-26,47,69); the shot is fired on the
        // first tick without BREEZE_SHOOT_CHARGING (Shoot.java:87).
        assertTrue(eventAfter(CompressedTicks.brainTimerNoLater(15)) < eventAfter(CompressedTicks.brainTimer(20)));
        // Warden SonicBoom: a 34-tick sound delay inside a 60-tick timeout (SonicBoom.java:24-25,39,53,60).
        assertTrue(eventAfter(CompressedTicks.brainTimer(34)) < eventAfter(CompressedTicks.brainTimer(60)));
    }

    @Property
    void countDownLosesOneTickPerVirtualTick(@ForAll @IntRange(min = -1_000, max = 1_000_000) int ticks) {
        int vanilla = ticks;
        for (int virtualTick = 0; virtualTick < ACCELERATION && vanilla > 0; virtualTick++) {
            vanilla--;
        }
        assertEquals(Math.max(0, vanilla), CompressedTicks.countDown(ticks));
    }

    @Property
    void remainingAfterServerTickFinishesTheCountdownVanillaStarted(
            @ForAll @IntRange(min = 0, max = 1_000_000) int before) {
        int vanillaValue = before - 1;
        assertEquals(CompressedTicks.countDown(before), CompressedTicks.remainingAfterServerTick(vanillaValue));
    }

    /**
     * Vanilla fire (Entity.java:554-562): while {@code remainingFireTicks > 0}, a tick hurts when it is a multiple of
     * 20 and then counts down by one. On the compressed timeline the damage check sees
     * {@link CompressedTicks#fireTicksForDamageCheck} and the count goes down by {@link CompressedTicks#countDown}.
     */
    @Property
    void fireHurtsInTheServerTickOfEveryVanillaFireDamageTick(
            @ForAll @IntRange(min = -100, max = 20_000) int remainingFireTicks) {
        List<Integer> vanillaHurtServerTicks = new ArrayList<>();
        int vanillaVirtualTicks = 0;
        for (int fire = remainingFireTicks; fire > 0; fire--) {
            if (fire % 20 == 0) {
                vanillaHurtServerTicks.add(vanillaVirtualTicks / ACCELERATION);
            }
            vanillaVirtualTicks++;
        }

        List<Integer> compressedHurtServerTicks = new ArrayList<>();
        int serverTicks = 0;
        for (int fire = remainingFireTicks; fire > 0; fire = CompressedTicks.countDown(fire)) {
            if (CompressedTicks.fireTicksForDamageCheck(fire) % 20 == 0) {
                compressedHurtServerTicks.add(serverTicks);
            }
            serverTicks++;
        }

        assertEquals(vanillaHurtServerTicks, compressedHurtServerTicks);
        assertEquals((vanillaVirtualTicks + ACCELERATION - 1) / ACCELERATION, serverTicks);
    }

    @Property
    void fireDamageCheckKeepsTheFireTicksWhenNoDamageTickIsCrossed(
            @ForAll @IntRange(min = -1_000, max = 1_000_000) int remainingFireTicks) {
        int checked = CompressedTicks.fireTicksForDamageCheck(remainingFireTicks);
        boolean vanillaHurtsThisServerTick = false;
        for (int fire = remainingFireTicks; fire > 0 && fire > remainingFireTicks - ACCELERATION; fire--) {
            vanillaHurtsThisServerTick |= fire % 20 == 0;
        }
        if (vanillaHurtsThisServerTick) {
            assertEquals(0, checked % 20);
            assertTrue(checked > remainingFireTicks - ACCELERATION && checked <= remainingFireTicks);
        } else {
            assertEquals(remainingFireTicks, checked);
        }
    }

    /** A hit on the compressed or vanilla timeline: whole server ticks since the previous hit, and its offset. */
    record Hit(int serverTicksLater, int offset) {}

    @Provide
    Arbitrary<List<Hit>> hits() {
        Arbitrary<Hit> hit = Combinators.combine(
                        Arbitraries.integers().between(0, 3), Arbitraries.integers().between(0, LAST_OFFSET))
                .as(Hit::new);
        return hit.list().ofMaxSize(30);
    }

    /**
     * Hurt cooldown, for hits that all land at the same point of the living entity's tick (all before the cooldown
     * counts down, like fire and drowning, or all after it, like effects; LivingEntity.java:424,452,476-477,503).
     * Vanilla blocks a hit while the cooldown is above 10 and sets it to 20 after a hit it lets through
     * (LivingEntity.java:1219,1229). The compressed timeline must let through the same hits.
     */
    @Property
    void hurtCooldownLetsThroughTheHitsVanillaLetsThrough(
            @ForAll("hits") List<Hit> hits, @ForAll boolean hitsBeforeTheCountdown) {
        List<Boolean> vanilla = vanillaCooldownDecisions(hits, hitsBeforeTheCountdown);
        List<Boolean> compressed = compressedCooldownDecisions(hits, hitsBeforeTheCountdown);
        assertEquals(vanilla, compressed);
    }

    private static List<Boolean> vanillaCooldownDecisions(List<Hit> hits, boolean hitsBeforeTheCountdown) {
        List<Boolean> letThrough = new ArrayList<>();
        int cooldown = 0;
        int virtualTick = 0;
        int serverTick = 0;
        for (Hit hit : sortedWithinEachServerTick(hits)) {
            serverTick += hit.serverTicksLater();
            int hitTick = serverTick * ACCELERATION + hit.offset();
            for (; virtualTick < hitTick; virtualTick++) {
                cooldown = Math.max(0, cooldown - 1);
            }
            if (!hitsBeforeTheCountdown && virtualTick == hitTick) {
                cooldown = Math.max(0, cooldown - 1);
                virtualTick++;
            }
            boolean through = !(cooldown > 10);
            if (through) {
                cooldown = 20;
            }
            letThrough.add(through);
        }
        return letThrough;
    }

    private static List<Boolean> compressedCooldownDecisions(List<Hit> hits, boolean hitsBeforeTheCountdown) {
        List<Boolean> letThrough = new ArrayList<>();
        int stored = 0;
        int serverTick = 0;
        boolean countedDownThisServerTick = false;
        for (Hit hit : sortedWithinEachServerTick(hits)) {
            for (int tick = 0; tick < hit.serverTicksLater(); tick++) {
                if (!countedDownThisServerTick) {
                    stored = countDownOnce(stored);
                }
                countedDownThisServerTick = false;
                serverTick++;
            }
            if (!hitsBeforeTheCountdown && !countedDownThisServerTick) {
                stored = countDownOnce(stored);
                countedDownThisServerTick = true;
            }
            int remainingInTick = LAST_OFFSET - hit.offset();
            boolean through = !(CompressedTicks.cooldownSeenAt(stored, remainingInTick) > 10);
            if (through) {
                stored = CompressedTicks.cooldownStoredAfterHit(20, remainingInTick);
            }
            letThrough.add(through);
        }
        return letThrough;
    }

    /** One living entity tick: vanilla's own step of the cooldown, then the mixin's other nine. */
    private static int countDownOnce(int stored) {
        return stored > 0 ? CompressedTicks.remainingAfterServerTick(stored - 1) : stored;
    }

    /** Hits within one server tick are replayed in offset order. */
    private static List<Hit> sortedWithinEachServerTick(List<Hit> hits) {
        List<Hit> sorted = new ArrayList<>();
        List<Hit> sameTick = new ArrayList<>();
        for (Hit hit : hits) {
            if (hit.serverTicksLater() > 0 && !sameTick.isEmpty()) {
                flush(sameTick, sorted);
            }
            sameTick.add(hit);
        }
        flush(sameTick, sorted);
        return sorted;
    }

    private static void flush(List<Hit> sameTick, List<Hit> sorted) {
        if (sameTick.isEmpty()) {
            return;
        }
        int gap = sameTick.getFirst().serverTicksLater();
        List<Integer> offsets = new ArrayList<>(sameTick.stream().map(Hit::offset).sorted().toList());
        for (int i = 0; i < offsets.size(); i++) {
            sorted.add(new Hit(i == 0 ? gap : 0, offsets.get(i)));
        }
        sameTick.clear();
    }
}
