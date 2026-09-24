package io.github.brainage04.accelerateddamage.util;

import io.github.brainage04.accelerateddamage.gamerule.ModGameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The compressed timeline used by {@code faster_effect_ticking}: every server tick covers
 * {@link #ACCELERATION} virtual ticks, numbered by offset {@code 0..LAST_OFFSET}.
 *
 * <p>Code that replays several virtual ticks inside one server tick enters the offset of the
 * virtual tick it is simulating, so the damage-immunity window can be measured in virtual ticks.
 * Everything outside such a replay runs at {@link #LAST_OFFSET}, the end of the server tick.
 */
public final class VirtualTime {
    public static final int ACCELERATION = 10;
    public static final int LAST_OFFSET = ACCELERATION - 1;

    private static final ThreadLocal<int[]> OFFSET = ThreadLocal.withInitial(() -> new int[]{LAST_OFFSET});

    private VirtualTime() {
    }

    public static boolean isEnabled(Level level) {
        return level instanceof ServerLevel serverLevel
                && serverLevel.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING);
    }

    public static boolean isEnabled() {
        MinecraftServer server = ServerContext.get();
        return server != null && server.getGameRules().get(ModGameRules.FASTER_EFFECT_TICKING);
    }

    /** Enters the virtual tick {@code offset}; returns the previous offset for {@link #exit}. */
    public static int enter(int offset) {
        int[] current = OFFSET.get();
        int previous = current[0];
        current[0] = offset;
        return previous;
    }

    public static void exit(int previousOffset) {
        OFFSET.get()[0] = previousOffset;
    }

    /** Virtual ticks that still elapse in the current server tick after the running virtual tick. */
    public static int remainingInTick() {
        return LAST_OFFSET - OFFSET.get()[0];
    }
}
