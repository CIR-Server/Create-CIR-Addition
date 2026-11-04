package com.cirserver.createciraddition.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Mixin 加载门控。
 *
 * 核心作用：**只有在 AE2 已安装时才加载 {@link MixinQuantumBridge}**。
 */
public class CirMixinPlugin implements IMixinConfigPlugin {

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
            boolean present = isModPresentEarly("ae2");
            org.apache.logging.log4j.LogManager
                .getLogger("Create CIR Addition")
                .info("Create CIR Addition: AE2 present = {}, quantum ring limiter {}", present, present ? "enabled" : "skipped");
            return present;
        }
        return true;
    }

    /**
 * mixin 阶段的 mod 存在性检测。
 *
 * {@code ModList} 在这个阶段还没准备好，用它判断会永远得到 false，
 */
    private static boolean isModPresentEarly(String modId) {
        try {
            Object modFile = net.neoforged.fml.loading.LoadingModList.get().getModFileById(modId);
            if (modFile != null) {
                return true;
            }
        } catch (Throwable t) {
            // 继续走兜底
        }
        try {
            // 兜底：直接看 AE2 的核心类能不能加载
            Class.forName("appeng.blockentity.qnb.QuantumBridgeBlockEntity", false,
                CirMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 无
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
        // 无
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
        // 无
    }
}
