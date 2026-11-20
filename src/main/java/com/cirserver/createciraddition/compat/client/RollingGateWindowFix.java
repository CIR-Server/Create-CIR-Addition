package com.cirserver.createciraddition.compat.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Window mutations must run on the GLFW/main thread, without blocking loading. */
public final class RollingGateWindowFix {
    private RollingGateWindowFix() {}

    public static void setWindowAttrib(long window, int attribute, int value) {
        Minecraft.getInstance().execute(() -> GLFW.glfwSetWindowAttrib(window, attribute, value));
    }
}
