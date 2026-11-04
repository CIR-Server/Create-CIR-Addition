package com.cirserver.createciraddition.quantum;

import com.cirserver.createciraddition.CreateCIRAddition;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 每个维度一份的「已登记量子桥中心坐标」表。
 *
 * 用登记表而不是实时扫描区块，原因有两个：
 */
public class QuantumBridgeData extends SavedData {

    private static final String DATA_NAME = "create_cir_addition_quantum_bridges";

    /** 已登记的桥中心坐标 */
    private final List<BlockPos> bridges = new ArrayList<>();

    public static QuantumBridgeData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(QuantumBridgeData::new, QuantumBridgeData::load),
            DATA_NAME
        );
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 统计某点在水平半径内的已登记桥数量 */
    public int countWithin(BlockPos center, int radius) {
        long r2 = (long) radius * radius;
        int n = 0;
        for (BlockPos p : bridges) {
            long dx = (long) p.getX() - center.getX();
            long dz = (long) p.getZ() - center.getZ();
            if (dx * dx + dz * dz <= r2) {
                n++;
            }
        }
        return n;
    }

    public int size() {
        return bridges.size();
    }

    /**
 * 这个坐标是否已经登记过。
 *
 * 用途：区块加载时 AE2 会重算集群、再次调用 {@code updateStatus}，
 */
    public boolean contains(BlockPos center) {
        return bridges.contains(center.immutable());
    }

    // ------------------------------------------------------------------
    // 修改
    // ------------------------------------------------------------------

    /** 登记一座桥（去重） */
    public void add(BlockPos center) {
        BlockPos imm = center.immutable();
        if (!bridges.contains(imm)) {
            bridges.add(imm);
            setDirty();
        }
    }

    /** 注销一座桥 */
    public void remove(BlockPos center) {
        if (bridges.remove(center.immutable())) {
            setDirty();
        }
    }

    /** 清理已经不存在了的记录。 */
    public int purgeStale(ServerLevel level) {
        int removed = 0;
        Iterator<BlockPos> it = bridges.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (!level.isLoaded(p)) {
                continue;   // 未加载，不下结论
            }
            String blockId;
            try {
                blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(level.getBlockState(p).getBlock())
                    .toString();
            } catch (Throwable t) {
                continue;
            }
            if (!"ae2:quantum_link".equals(blockId)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }

    /** 只读副本，调试用 */
    public List<BlockPos> snapshot() {
        return new ArrayList<>(bridges);
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BlockPos p : bridges) {
            CompoundTag one = new CompoundTag();
            one.putInt("x", p.getX());
            one.putInt("y", p.getY());
            one.putInt("z", p.getZ());
            list.add(one);
        }
        tag.put("Bridges", list);
        return tag;
    }

    public static QuantumBridgeData load(CompoundTag tag, HolderLookup.Provider registries) {
        QuantumBridgeData data = new QuantumBridgeData();
        try {
            ListTag list = tag.getList("Bridges", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag one = list.getCompound(i);
                data.bridges.add(new BlockPos(one.getInt("x"), one.getInt("y"), one.getInt("z")));
            }
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.warn(
                "Create CIR Addition: Failed to read the quantum ring registry, starting empty", t
            );
        }
        return data;
    }
}
