package io.github.brainage04.accelerateddamage.recording.mixin;

import io.github.brainage04.accelerateddamage.recording.ShowcaseFramePacer;
import net.minecraft.client.FramerateLimiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Spaces the presents of a filmed showcase; inert when no game capture is running. */
@Mixin(FramerateLimiter.class)
abstract class FramerateLimiterMixin {
    @Inject(method = "limitDisplayFPS", at = @At("HEAD"))
    private static void accelerateddamage$spaceFrames(int framerateLimit, CallbackInfo ci) {
        ShowcaseFramePacer.spaceFrames();
    }
}
