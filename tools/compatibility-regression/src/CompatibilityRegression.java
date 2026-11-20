import com.cirserver.createciraddition.compat.CompatibilityPatches;
import com.cirserver.createciraddition.network.NetworkCompatibility;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.CheckClassAdapter;

public class CompatibilityRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static ClassNode read(Path jar, String name) throws Exception {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ClassNode node = new ClassNode();
            new ClassReader(zip.getInputStream(zip.getEntry(name)).readAllBytes()).accept(node, 0);
            return node;
        }
    }
    private static List<String> fingerprint(MethodNode m, boolean normalizeLegacy) {
        List<String> result = new ArrayList<>();
        for (AbstractInsnNode i : m.instructions) {
            if (i.getOpcode() < 0) continue;
            if (normalizeLegacy && i instanceof TypeInsnNode t && t.desc.contains("MixinAbstractMonitorPartAccessor")) continue;
            if (i instanceof MethodInsnNode call) {
                String owner = call.owner, name = call.name;
                int opcode = i.getOpcode();
                if (normalizeLegacy && owner.contains("MixinAbstractMonitorPartAccessor")) {
                    owner = "appeng/parts/reporting/AbstractMonitorPart";
                    name = switch (name) { case "getConfiguredItem" -> "getDisplayed"; case "getCanCraft" -> "canCraft"; default -> name; };
                    opcode = Opcodes.INVOKEVIRTUAL;
                }
                result.add(opcode + ":" + owner + ":" + name + ":" + call.desc);
            } else if (i instanceof FieldInsnNode f) result.add(i.getOpcode() + ":" + f.owner + ":" + f.name + ":" + f.desc);
            else if (i instanceof LdcInsnNode l) result.add(i.getOpcode() + ":" + l.cst);
            else if (i instanceof VarInsnNode v) result.add(i.getOpcode() + ":" + v.var);
            else if (i instanceof TypeInsnNode t) result.add(i.getOpcode() + ":" + t.desc);
            else result.add(Integer.toString(i.getOpcode()));
        }
        return result;
    }
    public static void main(String[] args) throws Exception {
        Path throughput = Path.of(args[0]), ae2 = Path.of(args[1]), neo = Path.of(args[2]);
        String name = CompatibilityPatches.THROUGHPUT_TARGET.replace('.', '/') + ".class";
        ClassNode original = read(throughput, name), patched = read(throughput, name);
        check(CompatibilityPatches.patchThroughput(patched) == 3, "three real accessor calls must be repaired");
        check(CompatibilityPatches.patchThroughput(patched) == 0, "repair must be idempotent");
        check(original.methods.size() == patched.methods.size(), "no methods added or removed");
        Set<String> changed = new TreeSet<>();
        for (int i = 0; i < original.methods.size(); i++) {
            MethodNode before = original.methods.get(i), after = patched.methods.get(i);
            check(fingerprint(before, true).equals(fingerprint(after, false)), "unrelated behavior changed: " + before.name);
            if (!fingerprint(before, false).equals(fingerprint(after, false))) changed.add(before.name);
            for (AbstractInsnNode instruction : after.instructions) {
                check(!(instruction instanceof MethodInsnNode c && c.owner.contains("MixinAbstractMonitorPartAccessor")), "legacy method reference remains");
                check(!(instruction instanceof TypeInsnNode t && t.desc.contains("MixinAbstractMonitorPartAccessor")), "legacy cast remains");
            }
            if ((after.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                new Analyzer<>(new BasicVerifier()).analyze(patched.name, after);
            }
        }
        check(changed.equals(Set.of("getConfiguredItem", "renderDynamic")), "server and renderer repairs expected: " + changed);
        ClassWriter writer = new ClassWriter(0);
        patched.accept(new CheckClassAdapter(writer, true));
        Files.write(Path.of(args[3]), writer.toByteArray());
        ClassNode monitor = read(ae2, "appeng/parts/reporting/AbstractMonitorPart.class");
        for (String getter : List.of("getDisplayed()Lappeng/api/stacks/AEKey;", "getAmount()J", "canCraft()Z")) {
            check(monitor.methods.stream().anyMatch(m -> getter.equals(m.name + m.desc) && (m.access & Opcodes.ACC_PUBLIC) != 0), "missing AE2 public API: " + getter);
        }
        ClassNode unrelated = new ClassNode(); unrelated.name = "other/Monitor";
        check(CompatibilityPatches.patchThroughput(unrelated) == 0, "unrelated class must remain untouched");
        check(CompatibilityPatches.enableThroughput(true, true), "both mods enable patch");
        check(!CompatibilityPatches.enableThroughput(false, true), "missing ThroughputQuest skips patch");
        check(!CompatibilityPatches.enableThroughput(true, false), "missing AE2 skips patch");
        check(!CompatibilityPatches.enableThroughput(false, false), "absent mods skip patch");
        for (String version : Arrays.asList(null, "6.0.9", "6.0.11", "6.0.100", "6.0.10-beta", "7.0.0")) {
            check(!CompatibilityPatches.enablePressingFix(version), "unexpected Create version enabled: " + version);
        }
        check(CompatibilityPatches.enablePressingFix("6.0.10"), "Create 6.0.10 enables patch");
        for (String left : List.of("3.4.7", "3.4.8")) for (String right : List.of("3.4.7", "3.4.8")) {
            check(NetworkCompatibility.matches("create_cir_addition", left, right), "supported CIR pair rejected");
        }
        for (String bad : Arrays.asList(null, "", "3.4.6", "3.4.6-fix", "3.4.7fix", "3.4.9", "*")) {
            check(!NetworkCompatibility.matches("create_cir_addition", "3.4.8", bad), "unsupported CIR protocol accepted: " + bad);
            check(!NetworkCompatibility.matches("create_cir_addition", bad, "3.4.8"), "reverse unsupported CIR protocol accepted");
        }
        check(!NetworkCompatibility.matches("ae2", "3.4.8", "3.4.7"), "other mod versions must not be relaxed");
        check(NetworkCompatibility.matches("ae2", "1", "1"), "other mod equal versions preserved");
        ClassNode negotiation = read(neo, "net/neoforged/neoforge/network/negotiation/NetworkComponentNegotiator.class");
        MethodNode validate = negotiation.methods.stream().filter(m -> m.name.equals("validateComponent")).findFirst().orElseThrow();
        List<AbstractInsnNode> ops = Arrays.stream(validate.instructions.toArray()).filter(i -> i.getOpcode() >= 0).toList();
        MethodInsnNode equality = (MethodInsnNode) ops.stream().filter(i -> i instanceof MethodInsnNode m && m.owner.equals("java/lang/String") && m.name.equals("equals")).findFirst().orElseThrow();
        int index = ops.indexOf(equality);
        check(ops.get(index - 1) instanceof MethodInsnNode m && m.name.equals("version"), "redirect must target the version comparison");
        check(ops.get(index - 3) instanceof MethodInsnNode m && m.name.equals("version"), "both version operands expected");
        System.out.println("PASS: " + checks + " assertions; real server/client call sites, ASM stack verification, optional gates and protocol allowlist");
    }
}
