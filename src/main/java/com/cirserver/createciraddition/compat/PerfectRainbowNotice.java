package com.cirserver.createciraddition.compat;

import com.cirserver.createciraddition.CreateCIRAddition;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * 玩家登录时检查「PERFECT 彩虹名字」的配置。
 *
 * <p>物品列表默认留空——新装这个模组、还没往配置里写过物品时，
 * 在聊天栏给一条黄色提示，告诉玩家去 {@code config/create_cir_addition.toml}
 * 的 {@code perfectRainbowItems} 里填物品 ID。
 *
 * <p>功能本身只在 Apotheosis + KubeJS 同时安装时才有意义，
 * 缺其中一个就不打扰玩家。
 */
@EventBusSubscriber(
   modid = CreateCIRAddition.MOD_ID,
   bus = Bus.GAME
)
public final class PerfectRainbowNotice {
   private PerfectRainbowNotice() {
   }

   @SubscribeEvent
   public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
      if (!ModList.get().isLoaded("apotheosis") || !ModList.get().isLoaded("kubejs")) {
         return;
      }
      if (PerfectItemNames.isConfigured()) {
         return;
      }
      Player player = event.getEntity();
      if (player == null) {
         return;
      }
      player.sendSystemMessage(
         Component.translatable("cir.perfect_rainbow.unconfigured").withStyle(ChatFormatting.YELLOW)
      );
   }
}
