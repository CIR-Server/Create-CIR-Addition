package com.cirserver.createciraddition.home;

import com.cirserver.createciraddition.CreateCIRAddition;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 全局唯一的「基地」坐标。
 *
 * 整个存档只有一处基地，不区分维度——基地记录里带着自己的维度，
 */
public class HomeData extends SavedData {

    private static final String DATA_NAME = "create_cir_addition_home";

    /** 基地坐标，null 表示还没初始化 */
    @Nullable
    private BlockPos home;

    /** 基地所在维度，null 表示还没初始化 */
    @Nullable
    private ResourceLocation dimension;

    /** 是否已经做过首次初始化（防止每次启动都覆盖玩家手动设置的位置） */
    private boolean initialized;

    public static HomeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(HomeData::new, HomeData::load),
            DATA_NAME
        );
    }

    // ------------------------------------------------------------------
    // 读写
    // ------------------------------------------------------------------

    public boolean isInitialized() {
        return initialized && home != null && dimension != null;
    }

    @Nullable
    public BlockPos getHome() {
        return home;
    }

    @Nullable
    public ResourceLocation getDimension() {
        return dimension;
    }

    /** 首次初始化：用出生点作为基地 */
    public void initFromSpawn(ServerLevel overworld) {
        this.home = overworld.getSharedSpawnPos().immutable();
        this.dimension = overworld.dimension().location();
        this.initialized = true;
        setDirty();
        CreateCIRAddition.LOGGER.info(
            "Create CIR Addition: Base initialized from world spawn {} {}",
            this.dimension, this.home
        );
    }

    /** 手动设置基地 */
    public void setHome(BlockPos pos, ResourceKey<Level> dim) {
        this.home = pos.immutable();
        this.dimension = dim.location();
        this.initialized = true;
        setDirty();
    }

    /** 某坐标是否落在基地豁免范围内。 */
    public boolean isInHomeZone(ServerLevel level, BlockPos pos, int radius) {
        if (!isInitialized()) return false;
        if (!level.dimension().location().equals(this.dimension)) return false;
        long dx = (long) pos.getX() - this.home.getX();
        long dz = (long) pos.getZ() - this.home.getZ();
        return dx * dx + dz * dz <= (long) radius * radius;
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("Initialized", initialized);
        if (home != null) {
            CompoundTag pos = new CompoundTag();
            pos.putInt("x", home.getX());
            pos.putInt("y", home.getY());
            pos.putInt("z", home.getZ());
            tag.put("Home", pos);
        }
        if (dimension != null) {
            tag.putString("Dimension", dimension.toString());
        }
        return tag;
    }

    public static HomeData load(CompoundTag tag, HolderLookup.Provider registries) {
        HomeData data = new HomeData();
        try {
            data.initialized = tag.getBoolean("Initialized");
            if (tag.contains("Home")) {
                CompoundTag pos = tag.getCompound("Home");
                data.home = new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
            }
            if (tag.contains("Dimension")) {
                data.dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            }
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.warn("Create CIR Addition: Failed to read base data, treating as uninitialized", t);
            data.initialized = false;
            data.home = null;
            data.dimension = null;
        }
        return data;
    }
}
