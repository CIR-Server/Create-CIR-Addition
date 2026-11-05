package com.cirserver.createciraddition.gift;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 已兑换记录：码 → 领过的玩家 UUID 集合。整服一份，不分维度。
 */
public class GiftCodeStore extends SavedData {

    private static final String DATA_NAME = "create_cir_addition_gift_codes";

    /** 码 → 已兑换的玩家 */
    private final Map<String, Set<UUID>> redeemed = new HashMap<>();

    public static GiftCodeStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(GiftCodeStore::new, GiftCodeStore::load),
            DATA_NAME
        );
    }

    public boolean hasRedeemed(String code, UUID player) {
        Set<UUID> set = this.redeemed.get(code);
        return set != null && set.contains(player);
    }

    /** 清掉某个码的全部兑换记录（删除码时用） */
    public void clearCode(String code) {
        if (this.redeemed.remove(code) != null) {
            this.setDirty();
        }
    }

    /** 记一笔，返回 false 表示之前已经领过 */
    public boolean markRedeemed(String code, UUID player) {
        Set<UUID> set = this.redeemed.computeIfAbsent(code, k -> new HashSet<>());
        if (!set.add(player)) {
            return false;
        }
        this.setDirty();
        return true;
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag codes = new ListTag();
        for (Map.Entry<String, Set<UUID>> e : this.redeemed.entrySet()) {
            CompoundTag one = new CompoundTag();
            one.putString("Code", e.getKey());
            ListTag uuids = new ListTag();
            for (UUID id : e.getValue()) {
                CompoundTag u = new CompoundTag();
                u.putUUID("Id", id);
                uuids.add(u);
            }
            one.put("Players", uuids);
            codes.add(one);
        }
        tag.put("Codes", codes);
        return tag;
    }

    static GiftCodeStore load(CompoundTag tag, HolderLookup.Provider registries) {
        GiftCodeStore data = new GiftCodeStore();
        ListTag codes = tag.getList("Codes", Tag.TAG_COMPOUND);
        for (int i = 0; i < codes.size(); i++) {
            CompoundTag one = codes.getCompound(i);
            String code = one.getString("Code");
            if (code.isEmpty()) {
                continue;
            }
            Set<UUID> set = new HashSet<>();
            ListTag uuids = one.getList("Players", Tag.TAG_COMPOUND);
            for (int j = 0; j < uuids.size(); j++) {
                CompoundTag u = uuids.getCompound(j);
                if (u.hasUUID("Id")) {
                    set.add(u.getUUID("Id"));
                }
            }
            if (!set.isEmpty()) {
                data.redeemed.put(code, set);
            }
        }
        return data;
    }
}
