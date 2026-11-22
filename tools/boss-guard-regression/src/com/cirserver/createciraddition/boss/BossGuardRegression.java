package com.cirserver.createciraddition.boss;

import com.cirserver.createciraddition.network.NetworkCompatibility;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/** Standalone JVM checks; never starts Minecraft. */
public final class BossGuardRegression {
    private static int checks;
    private static void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }
    private static void equal(double actual, double expected, String description) {
        check(Math.abs(actual-expected)<0.0001, description+": "+actual+" != "+expected);
    }
    private static ClassNode read(Path jar, String entry) throws Exception {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ClassNode node = new ClassNode();
            new ClassReader(zip.getInputStream(zip.getEntry(entry)).readAllBytes()).accept(node,0);
            return node;
        }
    }
    private static BossCombatRules.Resolution resolve(float natural, float raw, float max, float current,
            boolean player, boolean active, boolean ready, boolean halfDone,
            BossGuardConfig.Group group, BossGuardConfig.Global global) {
        return BossCombatRules.resolveFinal(natural,raw,max,current,player,active,ready,halfDone,group,global);
    }
    public static void main(String[] args) throws Exception {
        BossGuardConfig.Document defaults = new BossGuardConfig.Document();
        BossGuardConfig.validate(defaults);
        check(defaults.bossGroups.size()==3,"three default mod groups");
        check(defaults.bossGroups.get(0).bossList.size()==8,"eight default Cataclysm bosses");
        check(defaults.bossGroups.get(1).bossList.size()==9,"nine Twilight registry ids");
        var other=defaults.bossGroups.get(0);var vanilla=defaults.bossGroups.get(2);var global=defaults.global;
        equal(other.lowHealthMinDamage,15,"mod floor");equal(vanilla.lowHealthMinDamage,20,"vanilla schema floor");
        equal(BossCombatRules.capIncoming(600,300,global),150,"half max incoming cap");
        equal(resolve(1,100,300,100,false,false,false,true,other,global).healthLoss(),15,"post-armor mod floor");
        equal(resolve(1,100,300,100,false,false,false,true,vanilla,global).healthLoss(),20,"post-armor vanilla floor");
        equal(resolve(2,15,300,100,false,false,false,true,vanilla,global).healthLoss(),2,"weak incoming hit not inflated");
        equal(resolve(0,100,300,100,false,false,false,true,other,global).healthLoss(),15,"floor after absorption");
        check(resolve(1,100,300,1,false,false,false,true,other,global).healthLoss()>=1,"low-HP raw finisher remains killable");
        for (float hp:new float[]{1,2,5,10,15,20,100,500}) {
            float remaining=hp;
            for(int i=0;i<1000 && remaining>0;i++)remaining-=resolve(1,100,1000,remaining,false,false,false,true,other,global).healthLoss();
            check(remaining<=0,"no geometric immortality at "+hp);
        }
        other.lowHealthDamageRatio=0.5;
        equal(resolve(100,100,300,100,false,false,false,true,other,global).healthLoss(),50,"edited group ratio used");
        other.lowHealthDamageRatio=0.10;
        other.lowHealthSkipThreshold=200;
        equal(resolve(100,100,300,100,false,false,false,true,other,global).healthLoss(),100,"edited bypass threshold used");
        other.lowHealthSkipThreshold=15;
        check(BossCombatRules.overkill(600,300,true),"overkill checked before single-hit cap");
        check(!BossCombatRules.overkill(600,300,false),"cooldown prevents overkill trigger");
        check(!BossCombatRules.overkill(300,300,true),"overkill strict greater-than");
        check(!BossCombatRules.burst(100,true,true,global),"100 exactly does not trigger C");
        check(BossCombatRules.burst(101,true,true,global),"player burst triggers C");
        check(!BossCombatRules.burst(101,false,true,global),"non-player cannot trigger C");
        check(!BossCombatRules.burst(101,true,false,global),"C obeys cooldown");
        equal(BossCombatRules.reflectedHealth(300),150,"D halves current HP");
        equal(BossCombatRules.reflectedHealth(1),1,"D health floor");
        int blocked=0;
        for(int attempt=1;attempt<=5;attempt++){blocked=BossCombatRules.nextBlockedCount(blocked,5);check(blocked==attempt,"execution attempt "+attempt);}
        check(BossCombatRules.nextBlockedCount(Integer.MAX_VALUE,5)==5,"counter cannot overflow");
        for(boolean active:new boolean[]{false,true}) for(boolean ready:new boolean[]{false,true}) {
            var atHalf=resolve(1,100,400,201,true,active,ready,false,other,global);
            check(atHalf.kind()==BossCombatRules.Kind.HALF_HEALTH,"mandatory exact half despite immunity/cooldown");
            equal(atHalf.healthLoss(),1,"truncate to exactly 50 percent");
            var below=resolve(20,100,400,201,true,active,ready,false,other,global);
            equal(below.healthLoss(),1,"prevent crossing below half");
            var burstCross=resolve(50,500,400,220,true,active,ready,false,other,global);
            check(burstCross.kind()==BossCombatRules.Kind.HALF_HEALTH,"half phase overrides burst/overkill");
            equal(burstCross.healthLoss(),20,"burst crossing stops exactly at half");
        }
        var overkillFull=resolve(5,1000,400,400,true,false,true,false,other,global);
        check(overkillFull.kind()==BossCombatRules.Kind.HALF_HEALTH,"reflected overkill reaching half cannot consume E");
        equal(overkillFull.healthLoss(),200,"overkill first phase health anchor");
        check(resolve(50,50,400,220,true,true,false,true,other,global).kind()==BossCombatRules.Kind.IMMUNE,"used phase never retriggers through immunity");
        var coolOverkill=resolve(0,1000,400,400,true,true,false,false,other,global);
        check(coolOverkill.kind()==BossCombatRules.Kind.HALF_HEALTH,"overkill cannot lose mandatory phase during green/cooldown");
        equal(coolOverkill.healthLoss(),200,"cooldown overkill stops at exact half");
        for(float max:new float[]{300,333,390,400,420,450,600,800,1000}) {
            float before=max*0.75F;
            var phase=resolve(max,max,max,before,true,true,false,false,other,global);
            check(phase.kind()==BossCombatRules.Kind.HALF_HEALTH,"mandatory phase for boss HP "+max);
            equal(before-phase.healthLoss(),max*0.5F,"exact health anchor for boss HP "+max);
            equal(Math.min(max,before-phase.healthLoss()+BossCombatRules.healAmount(max,global)),
                Math.min(max,max*0.5+BossCombatRules.healAmount(max,global)),"heal after anchoring");
        }
        var actualContainer=new net.neoforged.neoforge.common.damagesource.DamageContainer(null,100);
        actualContainer.setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ARMOR,90);
        actualContainer.setReduction(net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction.ABSORPTION,9);
        equal(actualContainer.getNewDamage(),1,"real container post-reduction damage");
        var fixedFloor=resolve(actualContainer.getNewDamage(),100,300,100,false,false,false,true,other,global);
        actualContainer.setNewDamage(fixedFloor.healthLoss());
        equal(actualContainer.getNewDamage(),15,"floor survives actual native reduction pipeline");
        check(resolve(0,50,400,220,true,true,false,false,other,global).kind()==BossCombatRules.Kind.IMMUNE,"absorbed non-crossing hit remains immune");
        var used=resolve(50,50,400,220,false,false,true,true,other,global);
        check(used.kind()==BossCombatRules.Kind.NORMAL,"phase once-only flag preserved");
        equal(used.healthLoss(),50,"used phase no longer truncates at half");
        var floorSurvivor=resolve(1,16,400,19,false,false,false,true,vanilla,global);
        equal(floorSurvivor.healthLoss(),18,"floor alone cannot kill non-finishing raw hit");
        equal(resolve(20,20,400,19,false,false,false,true,vanilla,global).healthLoss(),20,"raw finisher can kill");
        for(float hp:new float[]{300,400})equal(BossCombatRules.healAmount(hp,global),40,"small heal");
        for(float hp:new float[]{401,700})equal(BossCombatRules.healAmount(hp,global),60,"medium heal");
        equal(BossCombatRules.healAmount(701,global),80,"large heal");
        BerserkState state=new BerserkState(400);
        check(!state.active(-10000)&&state.ready(-10000),"untriggered state handles negative nanoTime");
        state.hasTriggered=true;
        state.berserkUntil=10000;state.readyAt=30000;
        check(state.active(9999)&&!state.active(10000),"berserk end boundary");
        check(!state.ready(10000)&&!state.ready(29999)&&state.ready(30000),"cooldown begins after berserk");
        defaults.global.singleHitCapRatio=Double.NaN;
        try {BossGuardConfig.validate(defaults);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){checks++;}
        defaults.global.singleHitCapRatio=0.5;
        defaults.bossGroups.get(1).lowHealthMinDamage=0;
        try {BossGuardConfig.validate(defaults);throw new AssertionError("zero floor accepted");}catch(IllegalArgumentException expected){checks++;}
        defaults.bossGroups.get(1).lowHealthMinDamage=15;
        Files.writeString(Path.of(args[3]),new GsonBuilder().setPrettyPrinting().create().toJson(defaults)+"\n");
        check(NetworkCompatibility.matches("create_cir_addition","3.5.0","3.5.0"),"strict current protocol accepted");
        for(String old:Arrays.asList(null,"","3.4.7","3.4.8","3.4.9","3.5.0fix","3.5.1","*")) {
            check(!NetworkCompatibility.matches("create_cir_addition","3.5.0",old),"old peer rejected: "+old);
            check(!NetworkCompatibility.matches("create_cir_addition",old,"3.5.0"),"reverse old peer rejected");
        }
        check(NetworkCompatibility.matches("other","v1","v1"),"other namespace equality preserved");
        check(!NetworkCompatibility.matches("other","v1","v2"),"other namespace mismatch preserved");
        Path built=Path.of(args[0]);
        try(ZipFile zip=new ZipFile(built.toFile())) {
            for(var entry:java.util.Collections.list(zip.entries())) {
                if(!entry.getName().endsWith(".class"))continue;
                ClassNode node=read(built,entry.getName());
                for(MethodNode method:node.methods)if((method.access&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_NATIVE))==0)
                    new Analyzer<>(new BasicVerifier()).analyze(node.name,method);
                checks++;
                if(entry.getName().contains("/boss/")&&!entry.getName().endsWith("/ExecutionBlockHandler.class")) {
                    String pool=new String(zip.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.ISO_8859_1);
                    check(!pool.contains("dev/shadowsoffire/apotheosis"),"no optional Apotheosis class linkage in common guard");
                }
            }
        }
        ClassNode original=read(Path.of(args[1]),"dev/shadowsoffire/apotheosis/affix/effect/ExecutingAffix.class");
        MethodNode execute=original.methods.stream().filter(m->m.name.equals("doPostAttack")).findFirst().orElseThrow();
        int hook=-1,sound=-1,health=-1,death=-1,localStrength=-1;int index=0;
        for(AbstractInsnNode insn:execute.instructions) {
            if(insn instanceof MethodInsnNode call) {
                if(call.name.equals("callCheckTotemDeathProtection")) {
                    check(call.owner.equals("dev/shadowsoffire/apotheosis/mixin/LivingEntityInvoker"),"exact injection owner");
                    check(call.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;)Z"),"exact injection descriptor");hook=index;
                }
                if(call.name.equals("getLocalAtkStrength"))localStrength=index;
                if(call.name.equals("playSound"))sound=index;
                if(call.name.equals("setHealth"))health=index;
                if(call.name.equals("die"))death=index;
            }
            index++;
        }
        check(localStrength>=0&&hook>localStrength&&sound>hook&&health>hook&&death>hook,"hook after eligibility before all execution side effects");
        ClassNode guard=read(built,"com/cirserver/createciraddition/boss/BossCombatHandler.class");
        MethodNode registration=guard.methods.stream().filter(m->m.name.equals("register")).findFirst().orElseThrow();
        List<MethodInsnNode> calls=Arrays.stream(registration.instructions.toArray()).filter(i->i instanceof MethodInsnNode).map(i->(MethodInsnNode)i).toList();
        check(calls.getFirst().owner.endsWith("BossGuardConfig")&&calls.getFirst().name.equals("enabled"),"Apotheosis total gate before config/events");
        check(guard.methods.stream().noneMatch(m->m.desc.contains("LivingDeathEvent")),"no death handler resurrection");
        ClassNode patchedLiving=read(Path.of(args[2]),"net/minecraft/world/entity/LivingEntity.class");
        MethodNode hurt=patchedLiving.methods.stream().filter(m->m.name.equals("hurt")&&m.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")).findFirst().orElseThrow();
        int actual=-1,die=-1;index=0;
        for(AbstractInsnNode insn:hurt.instructions){if(insn instanceof MethodInsnNode call){if(call.name.equals("actuallyHurt"))actual=index;if(call.name.equals("die"))die=index;}index++;}
        check(actual>=0&&die>actual,"native damage post path precedes death check");
        MethodNode actualHurt=patchedLiving.methods.stream().filter(m->m.name.equals("actuallyHurt")).findFirst().orElseThrow();
        boolean post=false;
        for(AbstractInsnNode insn:actualHurt.instructions)if(insn instanceof MethodInsnNode call&&call.name.equals("onLivingDamagePost"))post=true;
        for(String version:List.of("21.1.255","21.1.265","21.1.267")) {
            ClassNode nativeLiving=read(Path.of(args[2].replace("21.1.267",version)),"net/minecraft/world/entity/LivingEntity.class");
            MethodNode nativeHurt=nativeLiving.methods.stream().filter(m->m.name.equals("actuallyHurt")).findFirst().orElseThrow();
            int absorptionSet=-1, finalGet=-1, healthSet=-1;index=0;
            for(AbstractInsnNode insn:nativeHurt.instructions) {
                if(insn instanceof MethodInsnNode call) {
                    if(call.name.equals("setAbsorptionAmount"))absorptionSet=index;
                    if(absorptionSet>=0&&finalGet<0&&call.owner.equals("net/neoforged/neoforge/common/damagesource/DamageContainer")&&call.name.equals("getNewDamage"))finalGet=index;
                    if(call.name.equals("setHealth"))healthSet=index;
                }
                index++;
            }
            check(absorptionSet>=0&&finalGet>absorptionSet&&healthSet>finalGet,"slice resolves after absorption before HP on "+version);
        }
        check(post,"NeoForge patched damage post hook exists");
        check(!BossGuardConfig.enabled(),"uninitialized runtime mod list is fail-closed");
        BossCombatHandler.register();
        check(BossGuardConfig.current().bosses().isEmpty(),"missing Apotheosis runtime registers no policy");
        check(!new com.cirserver.createciraddition.mixin.CirMixinPlugin().shouldApplyMixin(
            "dev.shadowsoffire.apotheosis.affix.effect.ExecutingAffix",
            "com.cirserver.createciraddition.mixin.MixinExecutingAffix"),"missing loading mod list skips optional mixin");
        check(!new com.cirserver.createciraddition.mixin.CirMixinPlugin().shouldApplyMixin(
            "net.minecraft.world.entity.LivingEntity",
            "com.cirserver.createciraddition.mixin.MixinBossFinalDamage"),"missing Apotheosis skips final damage hook");
        System.out.println("PASS: "+checks+" checks; combat boundaries, floor/killability, cooldown, config validation, strict protocol, ASM stacks, optional linkage and execution injection order.");
    }
}
