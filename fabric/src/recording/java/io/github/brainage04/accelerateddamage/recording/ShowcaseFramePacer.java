package io.github.brainage04.accelerateddamage.recording;

import io.github.brainage04.minecraftmodconventions.GameCapture;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Spaces the presents of a recorded showcase so the recorder gets a new picture for every frame the
 * cut keeps. The recorder samples one slot per capture frame and repeats the old picture when no new
 * one arrived (MinecraftModConventions 3.5.0, {@code GameCapture.java:26-31}); a slow frame followed
 * by a fast one can present twice inside one 33 ms slot even with the client's 30 fps cap. Waiting
 * after each present leaves a 50 ms gap, as in the DimensionStructureVariants film, so no frame is
 * hidden by the slot that follows it.
 *
 * <p>The film plays the kept frames at the capture rate, so the world moves 1.5 times as fast as in
 * the game. The GameTest's checks read game ticks and stay valid. Nothing is paced unless a game
 * capture is running, so an ordinary GameTest is unaffected.
 */
public final class ShowcaseFramePacer {
    /** The least gap between two presents while a shot runs, in milliseconds. */
    public static final long PRESENT_GAP_MILLIS = 50L;

    private static volatile boolean active;

    public static void enableIfCaptured() {
        Properties evidence = new Properties();
        GameCapture.describe(evidence);
        active = evidence.getProperty("gameCaptureFrames") != null;
    }

    public static void disable() {
        active = false;
    }

    /** Called by {@code FramerateLimiterMixin} after a frame was presented; client thread only. */
    public static void spaceFrames() {
        if (!active) return;
        long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PRESENT_GAP_MILLIS);
        long left;
        while ((left = until - System.nanoTime()) > 0L) LockSupport.parkNanos(left);
    }

    /** The recorder's dropped-frame count, or 0 when nothing is being captured. */
    public static int droppedFrames() {
        Properties evidence = new Properties();
        GameCapture.describe(evidence);
        return Integer.parseInt(evidence.getProperty("gameCaptureDroppedFrames", "0"));
    }
}
