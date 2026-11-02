package com.cirserver.createciraddition.quantum;

import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.config.CirConfig;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.fml.ModList;

/**
 * ME 量子桥数量限制（按已加载区块扫描）。
 *
 * <p><b>行为：</b>量子网络桥刚成型时，扫描半径内已加载区块里其他已存在的量子环。
 * 若数量已达上限，把这座新桥整座拆掉、在原位置召唤闪电，并给附近玩家发提示。
 *
 * <p><b>为什么扫已加载区块而不是维护登记表：</b>量子桥会强制加载自己所在区块，
 * 所以"已加载区块"天然覆盖了服务器上所有活跃的桥。这样老存档不需要迁移、
 * 也不会有"登记表过期导致配额泄漏"的问题。扫描只在成型瞬间发生一次，
 * 半径 512 格也就 64×64 个 chunk 的循环，开销可以接受。
 *
 * <p><b>为什么全用反射：</b>Create CIR Addition 不能对 AE2 产生编译期依赖，
 * 没装 AE2 的整合包必须能正常加载。任何反射失败都只记日志，不影响游戏。
 */
public final class QuantumGuard {

    /** AE2 的量子桥方块实体类名，用来识别"这是不是一座桥" */
    private static final String QNB_BE_CLASS = "appeng.blockentity.qnb.QuantumBridgeBlockEntity";

    /** AE2 是否加载，只查一次 */
    private static Boolean ae2Present = null;

    private QuantumGuard() {
    }

    /** AE2 装了没有。没有的话整个功能静默跳过。 */
    public static boolean isAe2Present() {
        if (ae2Present == null) {
            try {
                ae2Present = ModList.get().isLoaded("ae2");
            } catch (Throwable t) {
                ae2Present = Boolean.FALSE;
            }
        }
        return ae2Present;
    }

    /**
     * 由 mixin 在 {@code updateStatus} 尾部调用。
     *
     * @param be        QuantumBridgeBlockEntity（Object，避免编译期引用 AE2）
     * @param wasFormed 上一次已知的成型状态
     * @return 本次之后的成型状态，回写给 mixin 的 @Unique 字段
     */
    public static boolean onStatusUpdate(Object be, boolean wasFormed) {
        if (!isAe2Present()) return wasFormed;

        // 总开关：只在服务器启动时读取一次（见 QuantumLimiterSwitch 的说明），
        // 关掉时直接返回，连反射都不做
        if (!QuantumLimiterSwitch.enabled()) return wasFormed;

        boolean formed;
        try {
            Method m = be.getClass().getMethod("isFormed");
            formed = (Boolean) m.invoke(be);
        } catch (Throwable t) {
            return wasFormed;
        }

        // 只在「刚刚成型」这一刻判定
        if (!formed || wasFormed) return formed;

        try {
            handleFormed(be);
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.warn("Create CIR Addition: Quantum ring check failed (ignored)", t);
        }
        return formed;
    }

    // ------------------------------------------------------------------
    // 判定
    // ------------------------------------------------------------------

    private static void handleFormed(Object be) throws Exception {
        BlockEntity self = (BlockEntity) be;
        var log = CreateCIRAddition.LOGGER;

        // 只在中心（quantum_link）判定，避免 9 个方块各判一次
        if (!isCenter(be)) return;

        if (!(self.getLevel() instanceof ServerLevel level)) return;

        int limitNear = CirConfig.QUANTUM_LIMIT_NEAR.get();
        int radiusNear = CirConfig.QUANTUM_RADIUS_NEAR.get();
        int limitFar = CirConfig.QUANTUM_LIMIT_FAR.get();
        int radiusFar = CirConfig.QUANTUM_RADIUS_FAR.get();
        int otherLimit = CirConfig.OTHER_DIMENSION_LIMIT.get();

        BlockPos center = self.getBlockPos().immutable();

        // ---- 基地豁免：基地半径内不限制量子环数量 ----
        // 注意：这里也要登记，否则这座桥没进表，下次区块加载还会再走一遍，
        // 虽然不判定但仍然会重复计算。登记之后由 countOutsideHome 把它排除在配额之外。
        if (isInHomeZone(level, center)) {
            log.info("Create CIR Addition: {} is inside the base exemption zone, allowing", center);
            QuantumBridgeData.get(level).add(center);
            return;
        }

        QuantumBridgeData data = QuantumBridgeData.get(level);

        // 顺手清掉已经拆掉的桥，避免"幽灵桥"一直占配额
        int purged = data.purgeStale(level);
        if (purged > 0) {
            log.info("Create CIR Addition: purged {} stale ring record(s)", purged);
        }

        // ============================================================
        // 只靠登记表确认，不再追踪「玩家放置」事件
        // ------------------------------------------------------------
        // 曾经加过 BlockEvent.EntityPlaceEvent 的追踪，但那条路能被绕开：
        // 活塞推方块、/setblock、结构方块、粘性活塞拉回等等都不会触发
        // EntityPlaceEvent，玩家可以把桥「搬」成型而躲过判定。
        // 所以现在只看一件事：这个坐标登记过没有。
        //   已登记 → 老桥，区块加载重算，跳过
        //   未登记 → 一律走完整判定（不管它是怎么被放出来的）
        // ============================================================
        if (data.contains(center)) {
            log.debug("Create CIR Addition: {} already registered, skipping (likely chunk reload)", center);
            return;
        }

        // 未登记 = 第一次见到这座桥，走完整判定
        log.info("Create CIR Addition: {} is not registered yet, running the limit check", center);

        boolean overworld = level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD);

        // ---- 非主世界：只按本维度总数限制，不看距离 ----
        if (!overworld) {
            int countBefore = data.size();
            if (otherLimit > 0 && countBefore >= otherLimit) {
                log.info("Create CIR Addition: dimension {} already has {} bridges (limit {}); dismantled",
                    level.dimension().location(), countBefore, otherLimit);
                reject(be, level, center);
                return;
            }
            data.add(center);
            notifyBuildAllowed(level, center, overworld,
                radiusNear, limitNear, 0,
                radiusFar, limitFar, 0,
                otherLimit, countBefore + 1);   // +1：把刚建成的这座算进去
            return;
        }

        // ---- 主世界：两级半径 ----
        if (limitNear <= 0 && limitFar <= 0) return;   // 都关掉 = 不限制

        int near = limitNear > 0 ? countOutsideHome(level, data, center, radiusNear) : 0;
        int far = limitFar > 0 ? countOutsideHome(level, data, center, radiusFar) : 0;
        log.info("Create CIR Addition: near {}/{} (r={}), far {}/{} (r={})",
            near, limitNear, radiusNear, far, limitFar, radiusFar);

        boolean nearOk = limitNear <= 0 || near < limitNear;
        boolean farOk = limitFar <= 0 || far < limitFar;

        if (nearOk && farOk) {
            data.add(center);
            // +1：把刚建成的这座算进去。玩家看到提示时桥已经成型了，
            // 显示「此前的数量」会少算一座，容易以为还有空位。
            notifyBuildAllowed(level, center, overworld,
                radiusNear, limitNear, near + 1,
                radiusFar, limitFar, far + 1,
                otherLimit, 0);
            return;
        }

        log.info(
            "Create CIR Addition: quantum ring at {} {} exceeds the limit; dismantled",
            level.dimension().location(), center
        );

        reject(be, level, center);
    }

    /** 超限处置：拆桥 + 闪电 + 提示 */
    private static void reject(Object be, ServerLevel level, BlockPos center) {
        dismantle(be, level, center);
        strikeLightning(level, center);
        notifyNearbyPlayers(level, center);
    }

    /**
     * 统计半径内「不在基地豁免区」的已登记环数量。
     *
     * <p>基地内的环也会进登记表（否则每次区块加载都会重复触发判定），
     * 但它们不该占用配额，所以统计时排除掉。
     */
    private static int countOutsideHome(ServerLevel level, QuantumBridgeData data, BlockPos center, int radius) {
        long r2 = (long) radius * radius;
        int n = 0;
        for (BlockPos p : data.snapshot()) {
            if (isInHomeZone(level, p)) continue;
            long dx = (long) p.getX() - center.getX();
            long dz = (long) p.getZ() - center.getZ();
            if (dx * dx + dz * dz <= r2) {
                n++;
            }
        }
        return n;
    }

    /** 桥被拆掉时注销登记，供 mixin 调用 */
    public static void onUnformed(Object be) {
        if (!isAe2Present()) return;
        try {
            BlockEntity self = (BlockEntity) be;
            if (!isCenter(be)) return;
            if (!(self.getLevel() instanceof ServerLevel level)) return;
            QuantumBridgeData data = QuantumBridgeData.get(level);
            data.remove(self.getBlockPos());
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.debug("Create CIR Addition: failed to unregister ring", t);
        }
    }

    /** 判断是不是桥中心（量子链接仓） */
    private static boolean isCenter(Object be) {
        try {
            Method m = be.getClass().getDeclaredMethod("isCenter");
            m.setAccessible(true);
            return (Boolean) m.invoke(be);
        } catch (Throwable t) {
            try {
                return ((BlockEntity) be).getBlockState().getBlock().toString().contains("quantum_link");
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    /**
     * 判断该坐标是否在基地豁免区内（基地半径内不限制量子环）。
     *
     * <p>基地是全局唯一的，记录里带着自己的维度，只有同维度才豁免。
     */
    private static boolean isInHomeZone(ServerLevel level, BlockPos pos) {
        try {
            int homeRadius = CirConfig.HOME_RADIUS.get();
            if (homeRadius <= 0) return false;

            net.minecraft.server.MinecraftServer server = level.getServer();
            if (server == null) return false;

            return com.cirserver.createciraddition.home.HomeData
                .get(server)
                .isInHomeZone(level, pos, homeRadius);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 处置
    // ------------------------------------------------------------------

    /**
     * 拆掉整座桥：先取部件坐标逐个替换成空气，然后在中心位置留下黑曜石。
     *
     * <p>中心方块（量子链接仓）<b>不掉落</b>——直接消失，不给玩家退还物品，
     * 原位置换成一块黑曜石作为"此处曾违规建造"的痕迹。
     */
    private static void dismantle(Object be, ServerLevel level, BlockPos center) {
        List<BlockPos> parts = collectParts(be);
        for (BlockPos p : parts) {
            try {
                level.removeBlock(p, false);
            } catch (Throwable t) {
                CreateCIRAddition.LOGGER.debug("Create CIR Addition: Failed to remove quantum ring block {}", p, t);
            }
        }

        // 中心位置留下黑曜石（中心方块本身不掉落）
        try {
            level.setBlockAndUpdate(center, net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState());
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.debug("Create CIR Addition: Failed to place obsidian", t);
        }
    }

    /** 从集群里取出所有组成方块的坐标 */
    @SuppressWarnings("unchecked")
    private static List<BlockPos> collectParts(Object be) {
        List<BlockPos> out = new ArrayList<>();
        try {
            Method getCluster = be.getClass().getMethod("getCluster");
            Object cluster = getCluster.invoke(be);
            if (cluster != null) {
                Method getBlockEntities = cluster.getClass().getMethod("getBlockEntities");
                Iterator<Object> it = (Iterator<Object>) getBlockEntities.invoke(cluster);
                while (it != null && it.hasNext()) {
                    Object part = it.next();
                    if (part instanceof BlockEntity partBe) {
                        out.add(partBe.getBlockPos().immutable());
                    }
                }
            }
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.debug("Create CIR Addition: Failed to collect ring parts, falling back to center block only", t);
        }
        if (out.isEmpty() && be instanceof BlockEntity blockEntity) {
            out.add(blockEntity.getBlockPos().immutable());
        }
        return out;
    }

    /** 在原位置召唤一道闪电（纯视觉效果，不点燃方块、不伤害实体） */
    private static void strikeLightning(ServerLevel level, BlockPos center) {
        try {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.debug("Create CIR Addition: Failed to summon lightning", t);
        }
    }

    /** 给附近玩家发提示（超限：黄色加粗） */
    private static void notifyNearbyPlayers(ServerLevel level, BlockPos center) {
        Component msg = Component.translatable(
            "cir.quantum.too_many", center.getX(), center.getY(), center.getZ()
        ).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        try {
            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5) <= 64.0 * 64.0) {
                    p.sendSystemMessage(msg);
                }
            }
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.debug("Create CIR Addition: Failed to send warning message", t);
        }
    }

    /**
     * 桥成功建立后给玩家的说明。
     *
     * <p>主世界只讲两级半径规则；其他维度只讲本维度总数上限——玩家在哪个维度，
     * 就只看得到跟那个维度有关的规则。
     */
    private static void notifyBuildAllowed(ServerLevel level, BlockPos center, boolean overworld,
                                           int radiusNear, int limitNear, int countNear,
                                           int radiusFar, int limitFar, int countFar,
                                           int otherLimit, int countOther) {
        try {
            net.minecraft.server.MinecraftServer server = level.getServer();
            if (server == null) return;

            var homeData = com.cirserver.createciraddition.home.HomeData.get(server);
            int homeRadius = CirConfig.HOME_RADIUS.get();
            BlockPos home = homeData.getHome();
            boolean hasHome = homeData.isInitialized() && home != null;

            // 标题（青色加粗）
            MutableComponent msg = Component.translatable("cir.quantum.online.title")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);

            // 基地信息（白色，不加粗）
            if (hasHome) {
                msg.append("\n").append(Component.translatable(
                    "cir.quantum.online.base",
                    home.getX(), home.getY(), home.getZ(), homeRadius
                ).withStyle(ChatFormatting.WHITE));
            }

            // 规则（浅绿，不加粗；括号里的当前数量是黄色）
            // 某个半径的限制关掉（limit <= 0）时不显示那个括号，否则会出现「最多 0 座（当前已有 1 座）」
            MutableComponent rule;
            if (overworld) {
                rule = Component.translatable(
                    "cir.quantum.online.rule.overworld",
                    radiusNear, limitNear, limitNear > 0 ? currentTag(countNear) : Component.empty(),
                    radiusFar, limitFar, limitFar > 0 ? currentTag(countFar) : Component.empty()
                );
            } else {
                rule = Component.translatable(
                    "cir.quantum.online.rule.other",
                    otherLimit, otherLimit > 0 ? currentTag(countOther) : Component.empty()
                );
            }
            msg.append("\n").append(Component.translatable("cir.quantum.online.rulehead")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD))
               .append(Component.literal(" ").withStyle(ChatFormatting.GREEN))
               .append(rule.withStyle(ChatFormatting.GREEN));

            for (ServerPlayer p : level.players()) {
                if (p.distanceToSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5) <= 64.0 * 64.0) {
                    p.sendSystemMessage(msg);
                }
            }
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.debug("Create CIR Addition: Failed to send placement notice", t);
        }
    }

    /** 黄色的「（当前已有 N 座）」片段 */
    private static Component currentTag(int count) {
        return Component.translatable("cir.quantum.online.current", count)
            .withStyle(ChatFormatting.YELLOW);
    }

    /**
     * 扫描当前维度已加载区域里所有量子桥中心，批量登记进表。
     * 供 {@code /cir quantum scan} 使用。
     *
     * @return 新登记的条数
     */
    public static int scanLoadedBridges(ServerLevel level) {
        int added = 0;
        try {
            QuantumBridgeData data = QuantumBridgeData.get(level);
            int before = data.size();

            Object chunkMap = level.getChunkSource().chunkMap;
            Method getChunks = net.minecraft.server.level.ChunkMap.class.getDeclaredMethod("getChunks");
            getChunks.setAccessible(true);
            Iterable<?> holders = (Iterable<?>) getChunks.invoke(chunkMap);
            if (holders == null) return 0;

            for (Object holder : holders) {
                Object chunk = null;
                try {
                    Method getChunk = holder.getClass().getMethod("getTickingChunk");
                    chunk = getChunk.invoke(holder);
                } catch (Throwable ignored) {
                    continue;
                }
                if (!(chunk instanceof LevelChunk lc)) continue;

                for (BlockEntity be : lc.getBlockEntities().values()) {
                    if (!QNB_BE_CLASS.equals(be.getClass().getName())) continue;
                    if (!isCenter(be)) continue;
                    data.add(be.getBlockPos());
                }
            }

            added = data.size() - before;
            CreateCIRAddition.LOGGER.info(
                "Create CIR Addition: scan on {} registered {} ring(s), {} total",
                level.dimension().location(), added, data.size()
            );
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.warn("Create CIR Addition: ring scan failed", t);
        }
        return added;
    }

    /**
     * 生成全局统计：主世界多少座、其中基地内/基地外各多少、其他维度各多少。
     *
     * @param scannedLevel 刚刚被扫描的维度（已登记完）
     */
    public static Component buildScanReport(net.minecraft.server.MinecraftServer server, ServerLevel scannedLevel) {
        try {
            ServerLevel overworld = server.overworld();
            QuantumBridgeData owData = QuantumBridgeData.get(overworld);
            var homeData = com.cirserver.createciraddition.home.HomeData.get(server);
            int homeRadius = CirConfig.HOME_RADIUS.get();
            BlockPos home = homeData.getHome();

            int owTotal = owData.size();
            int inBase = 0;
            if (homeData.isInitialized() && home != null) {
                for (BlockPos p : owData.snapshot()) {
                    long dx = (long) p.getX() - home.getX();
                    long dz = (long) p.getZ() - home.getZ();
                    if (dx * dx + dz * dz <= (long) homeRadius * homeRadius) {
                        inBase++;
                    }
                }
            }
            int outside = owTotal - inBase;

            // 其他维度
            int otherTotal = 0;
            StringBuilder detail = new StringBuilder();
            for (ServerLevel lv : server.getAllLevels()) {
                if (lv.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) continue;
                int n = QuantumBridgeData.get(lv).size();
                if (n <= 0) continue;
                otherTotal += n;
                if (detail.length() > 0) detail.append(", ");
                detail.append(lv.dimension().location().getPath()).append(" x").append(n);
            }

            MutableComponent msg = Component.translatable(
                "cir.quantum.scan.main", owTotal, inBase, outside
            );
            if (otherTotal > 0) {
                msg.append(" ").append(Component.translatable(
                    "cir.quantum.scan.others", otherTotal, detail.toString()
                ));
            }
            return msg;
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.warn("Create CIR Addition: failed to build ring scan report", t);
            return Component.translatable("cir.quantum.scan.failed");
        }
    }
}
