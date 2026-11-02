package com.cirserver.createciraddition.client;

import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.cleanup.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * 客户端注册。
 *
 * <p>整个类用 {@code value = Dist.CLIENT} 限定，服务端根本不会加载它——
 * 类里引用了 {@code AbstractContainerScreen} 这些纯客户端类，不小心在服务端
 * 加载就会直接 NoClassDefFoundError。
 */
@EventBusSubscriber(
    modid = CreateCIRAddition.MOD_ID,
    bus = EventBusSubscriber.Bus.MOD,
    value = Dist.CLIENT
)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.TRASH_BIN_MENU.get(), TrashBinScreen::new);
        CreateCIRAddition.LOGGER.info("Create CIR Addition: Trash bin screen registered.");
    }
}
