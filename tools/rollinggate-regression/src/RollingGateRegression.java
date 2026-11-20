import com.cirserver.createciraddition.compat.CompatibilityPatches;
import com.cirserver.createciraddition.compat.client.RollingGateWindowFix;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

public final class RollingGateRegression {
    private static int checks;
    private static void check(boolean passed, String description) {
        checks++;
        if (!passed) throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread loading = new Thread(() -> {
            try {
                RollingGateWindowFix.setWindowAttrib(42L, 131075, 1);
                RollingGateWindowFix.setWindowAttrib(42L, 131075, 0);
            } catch (Throwable error) { failure.set(error); }
        }, "simulated-modloading-sync-worker");
        loading.setDaemon(true);
        loading.start();
        loading.join(2000);
        check(!loading.isAlive(), "loading thread must return without waiting for main thread");
        check(failure.get() == null, "no worker-thread native call");
        check(GLFW.calls.isEmpty(), "native endpoint must not run before main-thread drain");
        minecraft.runQueued();
        check(GLFW.calls.equals(List.of("42:131075:1", "42:131075:0")), "preserve handle, attribute, values and order");
        RollingGateWindowFix.setWindowAttrib(84L, 131075, 1);
        check(GLFW.calls.get(2).equals("84:131075:1"), "main-thread use still works");
        check(CompatibilityPatches.enableRollingGateFix("1.1.0+build.66"), "known version enabled");
        for (String version : Arrays.asList(null, "", "1.1.0+build.65", "1.1.0+build.67", "1.2.0")) {
            check(!CompatibilityPatches.enableRollingGateFix(version), "missing/other versions skipped");
        }
        try (ZipFile zip = new ZipFile(Path.of(args[0]).toFile())) {
            ClassNode node = new ClassNode();
            new ClassReader(zip.getInputStream(zip.getEntry("dev/anvilcraft/rg/event/listener/client/listener/RGRuleChangeEventListener.class")).readAllBytes()).accept(node, 0);
            MethodNode target = node.methods.stream().filter(m -> m.name.equals("onWindowResizableChange")).findFirst().orElseThrow();
            int calls = 0;
            for (AbstractInsnNode i : target.instructions) {
                if (i instanceof MethodInsnNode m && m.owner.equals("org/lwjgl/glfw/GLFW") && m.name.equals("glfwSetWindowAttrib")) {
                    check(m.desc.equals("(JII)V"), "redirect descriptor matches installed mod");
                    calls++;
                }
            }
            check(calls == 2, "both true/false branches must be redirected");
        }
        System.out.println("PASS: " + checks + " RollingGate assertions; non-blocking main-thread dispatch and real target signatures");
    }
}
