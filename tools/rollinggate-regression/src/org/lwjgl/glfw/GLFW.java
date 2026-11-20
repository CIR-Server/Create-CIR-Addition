package org.lwjgl.glfw;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Fake native endpoint: fail immediately on the wrong thread. */
public final class GLFW {
    public static final List<String> calls = new ArrayList<>();
    public static void glfwSetWindowAttrib(long window, int attribute, int value) {
        if (!Minecraft.getInstance().isSameThread()) throw new AssertionError("Off-thread GLFW call");
        calls.add(window + ":" + attribute + ":" + value);
    }
}
