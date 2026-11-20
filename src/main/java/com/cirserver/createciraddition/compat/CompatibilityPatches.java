package com.cirserver.createciraddition.compat;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Narrow, optional compatibility patches. Does not load optional mod classes. */
public final class CompatibilityPatches {
    public static final String THROUGHPUT_TARGET =
        "net.yxiao233.throughputquest.common.part.ThroughputDetectionMonitorPart";
    private static final String LEGACY_ACCESSOR =
        "net/pedroksl/advanced_ae/mixins/MixinAbstractMonitorPartAccessor";
    private static final String MONITOR = "appeng/parts/reporting/AbstractMonitorPart";

    private CompatibilityPatches() {}

    public static boolean enableThroughput(boolean throughputPresent, boolean ae2Present) {
        return throughputPresent && ae2Present;
    }

    public static boolean enablePressingFix(String createVersion) {
        return "6.0.10".equals(createVersion);
    }

    public static boolean enableRollingGateFix(String version) {
        return "1.1.0+build.66".equals(version);
    }

    /**
     * ThroughputQuest 1.0 calls an AdvancedAE implementation-detail mixin accessor
     * removed by newer AdvancedAE. AE2 already exposes equivalent public getters.
     * Rewrite both the server watcher and client renderer, retaining their logic.
     * An upstream version without these exact call pairs is left untouched.
     */
    public static int patchThroughput(ClassNode target) {
        if (!THROUGHPUT_TARGET.replace('.', '/').equals(target.name)) {
            return 0;
        }
        int replaced = 0;
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode call)
                        || call.getOpcode() != Opcodes.INVOKEINTERFACE
                        || !LEGACY_ACCESSOR.equals(call.owner)) {
                    continue;
                }
                String getter = switch (call.name + call.desc) {
                    case "getConfiguredItem()Lappeng/api/stacks/AEKey;" -> "getDisplayed";
                    case "getAmount()J" -> "getAmount";
                    case "getCanCraft()Z" -> "canCraft";
                    default -> null;
                };
                AbstractInsnNode previous = instruction.getPrevious();
                while (previous != null && previous.getOpcode() < 0) {
                    previous = previous.getPrevious();
                }
                if (getter == null || !(previous instanceof TypeInsnNode cast)
                        || cast.getOpcode() != Opcodes.CHECKCAST
                        || !LEGACY_ACCESSOR.equals(cast.desc)) {
                    continue;
                }
                method.instructions.remove(cast);
                method.instructions.set(call,
                    new MethodInsnNode(Opcodes.INVOKEVIRTUAL, MONITOR, getter, call.desc, false));
                replaced++;
            }
        }
        return replaced;
    }
}
