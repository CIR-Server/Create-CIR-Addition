package com.cirserver.createciraddition.cleanup;

import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组用到的菜单类型。
 *
 * <p>注册表要在主类的构造函数里 {@code ModMenuTypes.REGISTER.register(modEventBus)}
 * 挂到 mod 事件总线上，否则打开垃圾桶界面时会找不到 menu type。
 */
public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> REGISTER =
        DeferredRegister.create(Registries.MENU, "create_cir_addition");

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<MenuType<TrashBinMenu>> TRASH_BIN_MENU =
        REGISTER.register("trash_bin", () -> new MenuType(TrashBinMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
