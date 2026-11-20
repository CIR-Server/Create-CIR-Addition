package com.cirserver.createciraddition.mixin;

import com.cirserver.createciraddition.compat.CompatibilityPatches;
import java.util.List;
import java.util.Set;
import net.neoforged.fml.loading.LoadingModList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Gate optional patches before resolving their target classes. */
public class CirMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Create CIR Addition");

    @Override
    public void onLoad(String mixinPackage) {
        // 不需要初始化
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith(".MixinQuantumBridge")) {
            // 注意：mixin 阶段 ModList 还没初始化完，必须用 LoadingModList
            boolean present = modVersion("ae2") != null;
            LOGGER.info("Create CIR Addition: AE2 present = {}, quantum ring limiter {}",
                present, present ? "enabled" : "skipped");
            return present;
        }
        if (mixinClassName.endsWith(".MixinThroughputDetectionMonitorPart")) {
            boolean enabled = CompatibilityPatches.enableThroughput(
                modVersion("throughput_quest") != null, modVersion("ae2") != null);
            LOGGER.info("Create CIR Addition: ThroughputQuest accessor compatibility {}",
                enabled ? "enabled" : "skipped (ThroughputQuest or AE2 absent)");
            return enabled;
        }
        if (mixinClassName.endsWith(".MixinPressingBehaviour")) {
            String version = modVersion("create");
            boolean enabled = CompatibilityPatches.enablePressingFix(version);
            LOGGER.info("Create CIR Addition: Create version = {}, empty press particle fix {}",
                version, enabled ? "enabled" : "skipped (requires 6.0.10)");
            return enabled;
        }
        return true;
    }

    private static String modVersion(String modId) {
        // Runtime ModList is not ready yet. Never resolve optional mod classes here.
        try {
            LoadingModList list = LoadingModList.get();
            if (list != null) {
                for (var mod : list.getMods()) {
                    if (modId.equals(mod.getModId())) {
                        return mod.getVersion().toString();
                    }
                }
            }
        } catch (RuntimeException | LinkageError error) {
            LOGGER.warn("Create CIR Addition: cannot determine installed version of {}; patch skipped", modId);
        }
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
        if (mixinClassName.endsWith(".MixinThroughputDetectionMonitorPart")) {
            int replaced = CompatibilityPatches.patchThroughput(targetClass);
            LOGGER.info("Create CIR Addition: repaired {} legacy ThroughputQuest accessor calls", replaced);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        // 无
    }
}
