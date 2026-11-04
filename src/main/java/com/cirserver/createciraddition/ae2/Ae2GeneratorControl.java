package com.cirserver.createciraddition.ae2;

import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.config.CirConfig;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * AE2 两个自发电方块的运行时调节。
 *
 * AE2 把自己的配置挂在 {@code appeng.core.AEConfig} 上，内部是 NeoForge 的
 */
public final class Ae2GeneratorControl {

   /** 谐振仓档位 */
   public enum VibrationTier {
      /** 基准的 1/4 */
      LOW,
      /** 基准值本身 */
      NORMAL,
      /** 基准的 4 倍 */
      BOOST,
      /** 当前值和基准对不上 */
      CUSTOM
   }

   private static final String AE_CONFIG_CLASS = "appeng.core.AEConfig";
   private static final String FIELD_COMMON = "common";
   private static final String FIELD_CRYSTAL_RATE = "crystalResonanceGeneratorRate";
   private static final String FIELD_VIB_BASE = "vibrationChamberBaseEnergyPerFuelTick";
   private static final String FIELD_VIB_MIN = "vibrationChamberMinEnergyPerTick";
   private static final String FIELD_VIB_MAX = "vibrationChamberMaxEnergyPerTick";

   /** 以下是 AE2 自己声明的合法区间，clamp 用 */
   private static final double BASE_AE_LOWER = 0.1D;
   private static final double BASE_AE_UPPER = 1000.0D;
   private static final int MIN_AE_LOWER = 0;
   private static final int MIN_AE_UPPER = 1000;
   private static final int MAX_AE_LOWER = 1;
   private static final int MAX_AE_UPPER = 1000;
   private static final double RESONANCE_UPPER = 1_000_000.0D;

   /** 没有快照时恢复到的默认值 */
   private static final double DEFAULT_RESONANCE_RATE = 20.0D;

   private Ae2GeneratorControl() {
   }

   // ================= 可用性 =================

   public static boolean isAvailable() {
      return readCommonConfig() != null;
   }

   // ================= 水晶谐振发电机 =================

   public static boolean isCrystalGeneratorOff() {
      ModConfigSpec.DoubleValue rate = crystalRate();
      return rate != null && rate.get() <= 0.0D;
   }

   /** 当前发电速率，AE2 未装返回 NaN */
   public static double crystalGeneratorRate() {
      ModConfigSpec.DoubleValue rate = crystalRate();
      return rate == null ? Double.NaN : rate.get();
   }

   /** 开关水晶谐振发电机。 */
   public static boolean setCrystalGeneratorEnabled(boolean enabled) {
      ModConfigSpec.DoubleValue rate = crystalRate();
      if (rate == null) {
         return false;
      }
      try {
         if (enabled) {
            double restore = CirConfig.AE2_RESONANCE_SNAPSHOT_VALID.get()
               ? CirConfig.AE2_RESONANCE_RATE_SNAPSHOT.get()
               : DEFAULT_RESONANCE_RATE;
            rate.set(clampDouble(restore, 0.0D, RESONANCE_UPPER));
         } else {
            // 首次禁用时先记下原值
            if (!CirConfig.AE2_RESONANCE_SNAPSHOT_VALID.get()) {
               CirConfig.AE2_RESONANCE_RATE_SNAPSHOT.set(clampDouble(rate.get(), 0.0D, RESONANCE_UPPER));
               CirConfig.AE2_RESONANCE_SNAPSHOT_VALID.set(true);
               CirConfig.saveSpec();
            }
            rate.set(0.0D);
         }
         rate.save();
         return true;
      } catch (Throwable t) {
         CreateCIRAddition.LOGGER.warn("Create CIR Addition: Failed to set AE2 crystal resonance rate", t);
         return false;
      }
   }

   // ================= 谐振仓 =================

   /**
 * 切换到指定档位。
 *
 * 基准是第一次切档时快照下来的那组值，不是写死的 AE2 默认值。
 */
   public static boolean setVibrationTier(VibrationTier tier) {
      return setVibrationTier(tier, null) >= 0;
   }

   /** 切档。 */
   public static int setVibrationTier(VibrationTier tier, MinecraftServer server) {
      ModConfigSpec.DoubleValue base = doubleField(FIELD_VIB_BASE);
      ModConfigSpec.IntValue min = intField(FIELD_VIB_MIN);
      ModConfigSpec.IntValue max = intField(FIELD_VIB_MAX);
      if (base == null || min == null || max == null) {
         return -1;
      }

      try {
         boolean hasSnapshot = CirConfig.AE2_VIBRATION_SNAPSHOT_VALID.get();

         // 没快照 + 只是切回 normal：配置不用动，但机器里缓存的 final 字段还是得顶一遍
         if (!hasSnapshot && tier == VibrationTier.NORMAL) {
            return server == null ? 0 : refreshLoadedChambers(server);
         }

         double refBase;
         int refMin;
         int refMax;
         if (hasSnapshot) {
            refBase = CirConfig.AE2_VIBRATION_BASE_SNAPSHOT.get();
            refMin = CirConfig.AE2_VIBRATION_MIN_SNAPSHOT.get();
            refMax = CirConfig.AE2_VIBRATION_MAX_SNAPSHOT.get();
         } else {
            refBase = base.get();
            refMin = min.get();
            refMax = max.get();
            CirConfig.AE2_VIBRATION_BASE_SNAPSHOT.set(clampDouble(refBase, BASE_AE_LOWER, BASE_AE_UPPER));
            CirConfig.AE2_VIBRATION_MIN_SNAPSHOT.set(clampInt(refMin, MIN_AE_LOWER, MIN_AE_UPPER));
            CirConfig.AE2_VIBRATION_MAX_SNAPSHOT.set(clampInt(refMax, MAX_AE_LOWER, MAX_AE_UPPER));
            CirConfig.AE2_VIBRATION_SNAPSHOT_VALID.set(true);
            CirConfig.saveSpec();
         }

         double newBase = switch (tier) {
            case LOW -> refBase / 4.0D;
            case BOOST -> refBase * 4.0D;
            default -> refBase;
         };
         int newMin = switch (tier) {
            case LOW -> refMin / 4;
            case BOOST -> refMin * 4;
            default -> refMin;
         };
         int newMax = switch (tier) {
            case LOW -> refMax / 4;
            case BOOST -> refMax * 4;
            default -> refMax;
         };

         base.set(clampDouble(newBase, BASE_AE_LOWER, BASE_AE_UPPER));
         min.set(clampInt(newMin, MIN_AE_LOWER, MIN_AE_UPPER));
         max.set(clampInt(newMax, MAX_AE_LOWER, MAX_AE_UPPER));
         base.save();
         min.save();
         max.save();
         // AE2 把 min/max/基准三个值缓存在方块实体的 final 字段里（构造时读一次），
         // 光改配置对已经摆着的机器不生效，必须逐个把缓存字段顶掉。
         return server == null ? 0 : refreshLoadedChambers(server);
      } catch (Throwable t) {
         CreateCIRAddition.LOGGER.warn("Create CIR Addition: Failed to set AE2 vibration chamber tier", t);
         return -1;
      }
   }

   /** 当前数值：{base, min, max}，AE2 未装返回空数组 */
   public static double[] currentVibrationValues() {
      ModConfigSpec.DoubleValue base = doubleField(FIELD_VIB_BASE);
      ModConfigSpec.IntValue min = intField(FIELD_VIB_MIN);
      ModConfigSpec.IntValue max = intField(FIELD_VIB_MAX);
      if (base == null || min == null || max == null) {
         return new double[0];
      }
      return new double[] { base.get(), min.get(), max.get() };
   }

   /** 反推当前处于哪一档；没有快照说明一直是原始值，算 NORMAL */
   public static VibrationTier currentVibrationTier() {
      double[] cur = currentVibrationValues();
      if (cur.length < 3) {
         return VibrationTier.CUSTOM;
      }
      if (!CirConfig.AE2_VIBRATION_SNAPSHOT_VALID.get()) {
         return VibrationTier.NORMAL;
      }

      double refBase = CirConfig.AE2_VIBRATION_BASE_SNAPSHOT.get();
      int refMin = CirConfig.AE2_VIBRATION_MIN_SNAPSHOT.get();
      int refMax = CirConfig.AE2_VIBRATION_MAX_SNAPSHOT.get();

      if (matches(cur, refBase, refMin, refMax)) {
         return VibrationTier.NORMAL;
      }
      if (matches(cur, refBase / 4.0D, refMin / 4, refMax / 4)) {
         return VibrationTier.LOW;
      }
      if (matches(cur, refBase * 4.0D, refMin * 4, refMax * 4)) {
         return VibrationTier.BOOST;
      }
      return VibrationTier.CUSTOM;
   }

   private static boolean matches(double[] cur, double refBase, int refMin, int refMax) {
      return Math.abs(cur[0] - clampDouble(refBase, BASE_AE_LOWER, BASE_AE_UPPER)) < 1.0E-6D
         && (int) cur[1] == clampInt(refMin, MIN_AE_LOWER, MIN_AE_UPPER)
         && (int) cur[2] == clampInt(refMax, MAX_AE_LOWER, MAX_AE_UPPER);
   }

   // ================= 已加载机器刷新 =================

   /**
 * 把新配置顶进所有已加载的谐振仓。
 *
 * AE2 的 {@code VibrationChamberBlockEntity} 在构造函数里把
 */
   public static int refreshLoadedChambers(MinecraftServer server) {
      if (server == null) {
         return 0;
      }
      Class<?> chamberClass;
      try {
         chamberClass = Class.forName("appeng.blockentity.misc.VibrationChamberBlockEntity");
      } catch (Throwable t) {
         return 0;
      }

      double[] values = currentVibrationValues();
      if (values.length < 3) {
         return 0;
      }

      Set<Object> seen = new HashSet<>();
      Set<Long> visitedChunks = new HashSet<>();
      int count = 0;
      for (ServerPlayer player : server.getPlayerList().getPlayers()) {
         if (!(player.level() instanceof ServerLevel level)) {
            continue;
         }
         var cache = level.getChunkSource();
         var center = player.chunkPosition();
         int radius = server.getPlayerList().getViewDistance() + 1;
         for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
               int cx = center.x + dx;
               int cz = center.z + dz;
               long chunkKey = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
               if (!visitedChunks.add(chunkKey)) {
                  continue;
               }
               LevelChunk chunk = cache.getChunkNow(cx, cz);
               if (chunk == null) {
                  continue;
               }
               for (var blockEntity : chunk.getBlockEntities().values()) {
                  if (!chamberClass.isInstance(blockEntity) || !seen.add(blockEntity)) {
                     continue;
                  }
                  if (rewriteChamberRates(blockEntity, values)) {
                     count++;
                  }
               }
            }
         }
      }
      if (count > 0) {
         CreateCIRAddition.LOGGER.info(
            "Create CIR Addition: Refreshed {} loaded vibration chamber(s) to the new tier", count
         );
      }
      return count;
   }

   /** 用 Unsafe 覆盖三个 final 缓存字段：values = {base, min, max} */
   private static boolean rewriteChamberRates(Object chamber, double[] values) {
      Object unsafe = unsafe();
      if (unsafe == null) {
         return false;
      }
      try {
         Class<?> type = chamber.getClass();
         writeDoubleField(unsafe, chamber, type, "minEnergyRate", values[1]);
         writeDoubleField(unsafe, chamber, type, "baseMaxEnergyRate", values[2]);
         writeDoubleField(unsafe, chamber, type, "initialEnergyRate", values[0]);
         return true;
      } catch (Throwable t) {
         CreateCIRAddition.LOGGER.warn("Create CIR Addition: Failed to refresh a vibration chamber", t);
         return false;
      }
   }

   private static void writeDoubleField(Object unsafe, Object target, Class<?> type, String name, double value)
      throws Exception {
      java.lang.reflect.Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      long offset = (long) unsafeOffsetOf().invoke(unsafe, field);
      unsafePutDouble().invoke(unsafe, target, offset, value);
   }

   private static volatile Object unsafeInstance;
   private static volatile boolean unsafeTried;
   private static volatile java.lang.reflect.Method cachedOffsetOf;
   private static volatile java.lang.reflect.Method cachedPutDouble;

   private static Object unsafe() {
      if (!unsafeTried) {
         synchronized (Ae2GeneratorControl.class) {
            if (!unsafeTried) {
               try {
                  Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                  java.lang.reflect.Field field = unsafeClass.getDeclaredField("theUnsafe");
                  field.setAccessible(true);
                  unsafeInstance = field.get(null);
                  cachedOffsetOf = unsafeClass.getMethod("objectFieldOffset", java.lang.reflect.Field.class);
                  cachedPutDouble = unsafeClass.getMethod("putDouble", Object.class, long.class, double.class);
               } catch (Throwable t) {
                  CreateCIRAddition.LOGGER.warn(
                     "Create CIR Addition: sun.misc.Unsafe unavailable, existing machines will keep their cached rates", t
                  );
               } finally {
                  unsafeTried = true;
               }
            }
         }
      }
      return unsafeInstance;
   }

   private static java.lang.reflect.Method unsafeOffsetOf() {
      unsafe();
      return cachedOffsetOf;
   }

   private static java.lang.reflect.Method unsafePutDouble() {
      unsafe();
      return cachedPutDouble;
   }

   // ================= 反射 =================

   /** 拿到 {@code AEConfig$CommonConfig} 实例；AE2 未装或尚未初始化时返回 null */
   private static Object readCommonConfig() {
      try {
         Class<?> configClass = Class.forName(AE_CONFIG_CLASS);
         Object instance = configClass.getMethod("instance").invoke(null);
         if (instance == null) {
            return null;
         }
         java.lang.reflect.Field field = configClass.getDeclaredField(FIELD_COMMON);
         field.setAccessible(true);
         return field.get(instance);
      } catch (Throwable t) {
         return null;
      }
   }

   private static ModConfigSpec.DoubleValue crystalRate() {
      return doubleField(FIELD_CRYSTAL_RATE);
   }

   private static ModConfigSpec.DoubleValue doubleField(String name) {
      Object value = fieldValue(name);
      return value instanceof ModConfigSpec.DoubleValue d ? d : null;
   }

   private static ModConfigSpec.IntValue intField(String name) {
      Object value = fieldValue(name);
      return value instanceof ModConfigSpec.IntValue i ? i : null;
   }

   private static Object fieldValue(String name) {
      Object common = readCommonConfig();
      if (common == null) {
         return null;
      }
      try {
         java.lang.reflect.Field field = common.getClass().getField(name);
         // CommonConfig 本身是包私有类，public 字段也得先放开访问权
         field.setAccessible(true);
         return field.get(common);
      } catch (Throwable t) {
         return null;
      }
   }

   // ================= 工具 =================

   private static double clampDouble(double value, double lower, double upper) {
      if (Double.isNaN(value)) {
         return lower;
      }
      return Math.max(lower, Math.min(upper, value));
   }

   private static int clampInt(long value, int lower, int upper) {
      return (int) Math.max((long) lower, Math.min((long) upper, value));
   }
}
