package com.cirserver.createciraddition.config;

import com.cirserver.createciraddition.CreateCIRAddition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.IntValue;

@EventBusSubscriber(
   modid = "create_cir_addition",
   bus = Bus.MOD
)
public class CirConfig {
   private static final Builder BUILDER = new Builder();
   public static final BooleanValue NETWORK_ENABLED = BUILDER.comment("Enable network optimization module").define("network", true);
   public static final BooleanValue CREATE_ENABLED = BUILDER.comment("Enable Create mod optimization module").define("create", true);
   public static final IntValue COMPRESSION_THRESHOLD = BUILDER.comment("Minimum packet size (bytes) to compress").defineInRange("threshold", 1024, 256, 65536);
   public static final IntValue ZSTD_THRESHOLD = BUILDER.comment("Minimum packet size (bytes) to use Zstd instead of LZ4")
      .defineInRange("zstdThreshold", 51200, 10240, 262144);
   public static final DoubleValue BENEFIT_THRESHOLD = BUILDER.comment(
         "Compression benefit threshold (0.0-1.0). Skip if compressed size > original * this value"
      )
      .defineInRange("benefitThreshold", 0.9, 0.5, 1.0);
   public static final IntValue SMART_BLOCK_SYNC_INTERVAL = BUILDER.comment("SmartBlockEntity sync interval (ticks)")
      .defineInRange("smartBlockInterval", 3, 1, 20);
   public static final IntValue CHAIN_CONVEYOR_INTERVAL = BUILDER.comment("ChainConveyor tick interval").defineInRange("chainConveyorInterval", 2, 1, 10);
   public static final IntValue BASIN_CHECK_INTERVAL = BUILDER.comment("Basin overflow check interval (ticks)").defineInRange("basinCheckInterval", 5, 1, 20);
   public static final IntValue MOUNTED_STORAGE_SYNC_COOLDOWN = BUILDER.comment(
         "MountedStorageManager sync cooldown (ticks). Default=8 in Create, we increase to reduce sync overhead"
      )
      .defineInRange("mountedStorageSyncCooldown", 20, 8, 40);
   public static final IntValue HOTSPOT_ENTITY_THRESHOLD = BUILDER.comment("Entity count threshold for hotspot alert")
      .defineInRange("entityThreshold", 100, 50, 500);
   public static final IntValue HOTSPOT_CHECK_RADIUS = BUILDER.comment("Radius (blocks) to check for entity hotspots")
      .defineInRange("checkRadius", 32, 16, 128);
   // ---- 量子桥限制（仅当 AE2 已安装时生效）----
   public static final IntValue QUANTUM_LIMIT_NEAR = BUILDER.comment(
         "Max number of ME quantum rings allowed within QUANTUM_RADIUS_NEAR.",
         "Set to 0 to disable this feature. Only active when the AE2 mod is installed."
      )
      .defineInRange("quantumLimitNear", 2, 0, 64);
   public static final IntValue QUANTUM_RADIUS_NEAR = BUILDER.comment(
         "Inner radius in blocks. 1024 = 64 chunks."
      )
      .defineInRange("quantumRadiusNear", 1024, 16, 100000);
   public static final IntValue QUANTUM_LIMIT_FAR = BUILDER.comment(
         "Max number of ME quantum rings allowed within QUANTUM_RADIUS_FAR.",
         "Both the near and far limits have to be satisfied."
      )
      .defineInRange("quantumLimitFar", 4, 0, 256);
   public static final IntValue QUANTUM_RADIUS_FAR = BUILDER.comment(
         "Outer radius in blocks. 4096 = 256 chunks."
      )
      .defineInRange("quantumRadiusFar", 4096, 16, 100000);
   public static final IntValue OTHER_DIMENSION_LIMIT = BUILDER.comment(
         "In dimensions other than the Overworld, the total number of quantum rings is capped at this value,",
         "regardless of distance. Set to 0 to disable."
      )
      .defineInRange("otherDimensionLimit", 4, 0, 256);
   // ---- 基地豁免区 ----
   public static final IntValue HOME_RADIUS = BUILDER.comment(
         "Radius in blocks around the base where quantum ring limits do not apply.",
         "The base is set to the world spawn on first launch, and can be moved with /cir setHome <x> <y> <z>.",
         "Set to 0 to disable the exemption."
      )
      .defineInRange("homeRadius", 256, 0, 100000);
   // ---- MSPT 预警 ----
   public static final BooleanValue MSPT_WARN_ENABLED = BUILDER.comment(
         "Broadcast a warning to all players when the server MSPT crosses 35 / 40 / 45 / 50.",
         "Checked every 30 seconds. Warnings are only sent while MSPT is rising; above 50 they repeat."
      )
      .define("msptWarnEnabled", true);
   // ---- 扫地（清理地面掉落物）----
   public static final BooleanValue CLEANUP_IGNORE_NAMED = BUILDER.comment(
         "Keep items that carry a custom name when the item cleaner runs.",
         "Named items are usually dropped on purpose, so they are protected by default."
      )
      .define("cleanupIgnoreNamed", true);
   public static final BooleanValue CLEANUP_ON_HIGH_MSPT = BUILDER.comment(
         "Automatically clean up ground items once MSPT reaches 50 (the red warning tier).",
         "There is no timer-based cleaning. Use /cir cleanup to run it manually."
      )
      .define("cleanupOnHighMspt", true);
   // ---- 红石终端优化 ----
   public static final BooleanValue REDSTONE_LINK_OPTIMIZATION = BUILDER.comment(
         "Cache LinkBehaviour.isAlive() once per game tick.",
         "Cuts the repeated chunk/block-entity lookups that RedstoneLinkNetworkHandler.updateNetworkOf",
         "does for every node on every scan. Toggle at runtime with /cir redstonelinkoptimization true|false."
      )
      .define("redstoneLinkOptimization", true);
   // ---- 量子环限制总开关 ----
   // 注意：这个值只在服务器启动时读取一次，/reload 和配置热重载都不会改变运行中的状态。
   // 想开关必须重启，这样避免运行中途切换导致集群状态和登记表不一致。
   public static final BooleanValue QUANTUM_LIMITER_ENABLED = BUILDER.comment(
         "Master switch for ME quantum ring limiting.",
         "READ ONCE AT STARTUP. Changing this requires a full game/server restart;",
         "/reload and config hot-reload deliberately ignored so a running world cannot be left",
         "with a half-registered ring table."
      )
      .define("quantumLimiterEnabled", true);
   public static final ModConfigSpec SPEC = BUILDER.build();

   /** 配置文件对象，onLoad 时拿到，用于让 /cir 指令写回磁盘 */
   private static ModConfig config = null;

   public CirConfig() {
   }

   @SubscribeEvent
   static void onLoad(ModConfigEvent event) {
      config = event.getConfig();
      ModuleManager.setEnabled("network", (Boolean)NETWORK_ENABLED.get());
      ModuleManager.setEnabled("create", (Boolean)CREATE_ENABLED.get());
      CreateCIRAddition.LOGGER.info("Create CIR Addition: Config loaded");
   }

   /**
    * 把 {@code redstoneLinkOptimization} 的值写回配置文件。
    *
    * <p>NeoForge 的 {@link ModConfig} 没有 save()，所以直接用 {@code getFullPath()}
    * 拿到文件路径后改写对应那一行。只动这一个键，其余内容保持原样。
    */
   public static void save() {
      try {
         if (config == null) {
            return;
         }
         java.nio.file.Path path = config.getFullPath();
         if (path == null || !java.nio.file.Files.exists(path)) {
            return;
         }

         boolean value = REDSTONE_LINK_OPTIMIZATION.get();
         String text = java.nio.file.Files.readString(path);
         String replaced = text.replaceAll(
            "(?m)^redstoneLinkOptimization\\s*=.*$",
            "redstoneLinkOptimization = " + value
         );
         if (!replaced.equals(text)) {
            java.nio.file.Files.writeString(path, replaced);
         }
      } catch (Throwable t) {
         CreateCIRAddition.LOGGER.warn("Create CIR Addition: Failed to save config", t);
      }
   }

   /**
    * 红石终端优化是否开启。
    *
    * <p>mixin 里调用，配置尚未加载时按关闭处理——宁可少优化，不要抛异常。
    */
   public static boolean isRedstoneLinkOptimizationEnabled() {
      try {
         return REDSTONE_LINK_OPTIMIZATION.get();
      } catch (Throwable t) {
         return false;
      }
   }

   static {
      BUILDER.comment("Create CIR Addition Configuration").push("modules");
      BUILDER.pop().push("compression");
      BUILDER.pop().push("sync");
      BUILDER.pop().push("hotspot");
      BUILDER.pop().push("quantum");
      BUILDER.pop();
   }
}
