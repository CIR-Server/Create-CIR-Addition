package com.cirserver.createciraddition;

import com.cirserver.createciraddition.config.CirConfig;
import com.cirserver.createciraddition.config.ModuleManager;
import com.cirserver.createciraddition.network.ClientNetworkHandler;
import com.cirserver.createciraddition.network.CompressedPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Create CIR Addition - Create-only build.
 *
 * Derived from an earlier, unmaintained upstream optimization mod that the team
 * has since retired; all identifying names have been replaced.
 *
 * Removed: entity optimizer, performance
 * monitor, and most chat commands.
 *
 * Added: ME quantum ring limiting (only active when AE2 is installed), a global
 * base zone that exempts nearby rings from that limit, MSPT load warnings, and a
 * manual / emergency item cleaner backed by a 1-hour trash bin. All commands now
 * live under "/cir".
 *
 * Kept: the Create module (7 mixins) plus the network compression layer it
 * depends on.
 */
@Mod("create_cir_addition")
public class CreateCIRAddition {
   public static final String MOD_ID = "create_cir_addition";
   public static final String VERSION = "3.4.6-fix";
   public static final Logger LOGGER = LogManager.getLogger();

   public CreateCIRAddition(IEventBus modEventBus, ModContainer modContainer) {
      modContainer.registerConfig(Type.COMMON, CirConfig.SPEC, "create_cir_addition.toml");
      modEventBus.addListener(this::setup);
      modEventBus.addListener(this::registerPayloads);
      //Menutype
      com.cirserver.createciraddition.cleanup.ModMenuTypes.REGISTER.register(modEventBus);
      LOGGER.info("Create CIR Addition v{}: Create-only build initialized.", VERSION);
      LOGGER.info(
         "Create CIR Addition: Modules - Network: {}, Create: {}",
         ModuleManager.isEnabled("network"),
         ModuleManager.isEnabled("create")
      );
   }

   private void setup(FMLCommonSetupEvent event) {
      LOGGER.info("Create CIR Addition: Core Logic Ready. Targeting NeoForge 1.21.1 + Create 6.x");
   }

   private void registerPayloads(RegisterPayloadHandlersEvent event) {
      if (!ModuleManager.isEnabled("network")) {
         LOGGER.info("Create CIR Addition: Network module disabled, skipping payload registration.");
      } else {
         PayloadRegistrar registrar = event.registrar("create_cir_addition").versioned(VERSION);
         registrar.playBidirectional(CompressedPayload.TYPE, CompressedPayload.STREAM_CODEC, ClientNetworkHandler::handle);

         // 兑换码：客户端提交、服务端回执。回执的处理器放在 lambda 里，
         // 服务端加载这个类时不会去解析 Minecraft，只有真正收到包才会走进来。
         registrar.playToServer(
            com.cirserver.createciraddition.network.GiftSubmitPayload.TYPE,
            com.cirserver.createciraddition.network.GiftSubmitPayload.STREAM_CODEC,
            com.cirserver.createciraddition.network.GiftServerHandler::handleSubmit
         );
         registrar.playToClient(
            com.cirserver.createciraddition.network.GiftResultPayload.TYPE,
            com.cirserver.createciraddition.network.GiftResultPayload.STREAM_CODEC,
            (payload, context) -> com.cirserver.createciraddition.client.GiftClientHandler.handleResult(payload, context)
         );

         registrar.playToServer(
            com.cirserver.createciraddition.network.GiftManageActionPayload.TYPE,
            com.cirserver.createciraddition.network.GiftManageActionPayload.STREAM_CODEC,
            com.cirserver.createciraddition.network.GiftServerHandler::handleManageAction
         );
         registrar.playToServer(
            com.cirserver.createciraddition.network.GiftCreatePayload.TYPE,
            com.cirserver.createciraddition.network.GiftCreatePayload.STREAM_CODEC,
            com.cirserver.createciraddition.network.GiftServerHandler::handleCreate
         );
         registrar.playToServer(
            com.cirserver.createciraddition.network.GiftDisclaimerAckPayload.TYPE,
            com.cirserver.createciraddition.network.GiftDisclaimerAckPayload.STREAM_CODEC,
            com.cirserver.createciraddition.network.GiftServerHandler::handleDisclaimerAck
         );
         registrar.playToClient(
            com.cirserver.createciraddition.network.GiftManageDataPayload.TYPE,
            com.cirserver.createciraddition.network.GiftManageDataPayload.STREAM_CODEC,
            (payload, context) -> com.cirserver.createciraddition.client.GiftClientHandler.handleManageData(payload, context)
         );
         registrar.playToClient(
            com.cirserver.createciraddition.network.GiftCreateResultPayload.TYPE,
            com.cirserver.createciraddition.network.GiftCreateResultPayload.STREAM_CODEC,
            (payload, context) -> com.cirserver.createciraddition.client.GiftClientHandler.handleCreateResult(payload, context)
         );

         LOGGER.info("Create CIR Addition: Network handlers registered.");
      }
   }
}
