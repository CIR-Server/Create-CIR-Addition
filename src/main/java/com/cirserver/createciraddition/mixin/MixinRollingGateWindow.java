package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.compat.client.RollingGateWindowFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Client-only, version-gated repair for RollingGate's config-load callback. */
@Pseudo
@Mixin(targets = "dev.anvilcraft.rg.event.listener.client.listener.RGRuleChangeEventListener", remap = false)
public abstract class MixinRollingGateWindow {
    @Redirect(
        method = "onWindowResizableChange",
        at = @At(value = "INVOKE", target = "Lorg/lwjgl/glfw/GLFW;glfwSetWindowAttrib(JII)V"),
        require = 0
    )
    private static void cir$setWindowAttribOnMainThread(long window, int attribute, int value) {
        RollingGateWindowFix.setWindowAttrib(window, attribute, value);
    }
}
