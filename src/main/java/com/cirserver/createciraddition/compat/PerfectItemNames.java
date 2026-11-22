package com.cirserver.createciraddition.compat;

import com.cirserver.createciraddition.config.CirConfig;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 让指定物品的名字显示成 Apotheosis 的 PERFECT 彩虹色。
 *
 * <p>PERFECT 用的是 Placebo 的 {@code GradientColor.RAINBOW}（运行时渐变），
 * 不能用 lang 文件还原，只能运行时设置。这里通过反射拿，避免对 Placebo 的编译期依赖。
 *
 * <p>目标物品来自配置 {@code perfectRainbowItems}（默认三个）。
 * 列表配置只在启动时加载，改完必须重启游戏。
 *
 * <p>门控：Apotheosis 与 KubeJS 都必须安装（由 mixin plugin 在加载期判定），
 * 运行期再兜一层物品存在检查——配置里写了不存在的 ID 只是不命中，不会报错。
 */
public final class PerfectItemNames {
    /** 缓存的目标物品 ID 集合；配置重载时清空。 */
    private static volatile Set<String> cachedTargets;

    private static Boolean available;
    private static TextColor rainbow;

    private PerfectItemNames() {
    }

    /** 配置重载后调用，下次访问时重新读配置。 */
    public static void invalidate() {
        cachedTargets = null;
    }

    /** 配置里是否已经填了物品。为空时玩家登录会给一条提示。 */
    public static boolean isConfigured() {
        try {
            List<? extends String> list = CirConfig.PERFECT_RAINBOW_ITEMS.get();
            return list != null && !list.isEmpty();
        } catch (Throwable notLoadedYet) {
            return false;
        }
    }

    private static Set<String> targets() {
        Set<String> cached = cachedTargets;
        if (cached != null) {
            return cached;
        }
        Set<String> built;
        try {
            List<? extends String> list = CirConfig.PERFECT_RAINBOW_ITEMS.get();
            built = new HashSet<>();
            if (list != null) {
                for (String id : list) {
                    if (id != null && !id.isBlank()) {
                        built.add(id.trim());
                    }
                }
            }
        } catch (Throwable notLoadedYet) {
            // 配置尚未加载（例如极早期调用）——不缓存，等下次再试
            return Set.of();
        }
        cachedTargets = built;
        return built;
    }

    /** 该物品是否要套彩虹名字。 */
    public static boolean isTarget(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Set<String> ids = targets();
        if (ids.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && ids.contains(id.toString());
    }

    /**
     * 给名字套上彩虹色。
     *
     * @return 带彩虹色的副本；拿不到彩虹色时返回原组件
     */
    public static Component apply(Component original) {
        TextColor color = rainbowColor();
        if (color == null) {
            return original;
        }
        return original.copy().withStyle(Style.EMPTY.withColor(color).withItalic(false));
    }

    /** 反射取 Placebo 的 GradientColor.RAINBOW；只尝试一次，失败后缓存 null。 */
    private static synchronized TextColor rainbowColor() {
        if (available != null) {
            return rainbow;
        }
        available = Boolean.TRUE;
        try {
            Class<?> gradient = Class.forName("dev.shadowsoffire.placebo.color.GradientColor");
            Field field = gradient.getField("RAINBOW");
            Object value = field.get(null);
            if (value instanceof TextColor textColor) {
                rainbow = textColor;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Placebo 不在或字段改名 —— 退化为无颜色，不影响物品本身
            rainbow = null;
        }
        return rainbow;
    }
}
