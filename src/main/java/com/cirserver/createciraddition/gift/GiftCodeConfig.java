package com.cirserver.createciraddition.gift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.cirserver.createciraddition.CreateCIRAddition;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 兑换码配置。文件在 {@code config/create_cir_addition/gift_codes.json}。
 *
 * <p>格式：
 * <pre>{@code
 * {
 *   "码": {
 *     "items":    [["物品ID", 数量], ...],
 *     "xp":       经验值,
 *     "effects":  [["效果ID", 持续tick, 等级], ...],
 *     "disabled": true
 *   }
 * }
 * }</pre>
 *
 * <p>键名以下划线开头的条目会被忽略，方便写注释。
 */
public final class GiftCodeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_CONTENT = """
        {
          "_comment": "兑换码配置。键名以下划线开头的条目会被忽略。disabled=true 表示已作废。",
          "cirnewkjs30": {
            "items": [
              ["kubejs:setlla_quantum_gem", 1],
              ["kubejs:random_blcok", 16],
              ["minecraft:golden_apple", 10]
            ],
            "xp": 500,
            "effects": [
              ["minecraft:speed", 36000, 1]
            ]
          }
        }
        """;

    private GiftCodeConfig() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("create_cir_addition").resolve("gift_codes.json");
    }

    /** 读配置。文件不存在时先写一份默认的，再读。读失败返回空表。 */
    public static Map<String, Entry> load() {
        Path file = path();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, DEFAULT_CONTENT, StandardCharsets.UTF_8);
                CreateCIRAddition.LOGGER.info("Create CIR Addition: wrote default gift code config to {}", file);
            }
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.warn("Create CIR Addition: failed to read gift code config", t);
            return Map.of();
        }
    }

    /** 把整张表写回磁盘。以 {@code _} 开头的注释条目会保留。 */
    public static boolean save(Map<String, Entry> table) {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());

            // 先读回原文件里的注释条目
            JsonObject root = new JsonObject();
            if (Files.exists(file)) {
                try {
                    JsonObject old = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), JsonObject.class);
                    if (old != null) {
                        for (Map.Entry<String, JsonElement> e : old.entrySet()) {
                            if (e.getKey().startsWith("_")) {
                                root.add(e.getKey(), e.getValue());
                            }
                        }
                    }
                } catch (Throwable ignored) {
                    // 旧文件坏了就只写新内容
                }
            }

            for (Entry entry : table.values()) {
                root.add(entry.code(), entry.toJson());
            }

            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
            return true;
        } catch (Throwable t) {
            CreateCIRAddition.LOGGER.warn("Create CIR Addition: failed to write gift code config", t);
            return false;
        }
    }

    private static Map<String, Entry> parse(String raw) {
        JsonObject root = GSON.fromJson(raw, JsonObject.class);
        if (root == null) {
            return Map.of();
        }
        Map<String, Entry> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            String code = e.getKey();
            if (code.startsWith("_") || !e.getValue().isJsonObject()) {
                continue;
            }
            Entry entry = Entry.from(code, e.getValue().getAsJsonObject());
            if (entry != null) {
                result.put(code, entry);
            }
        }
        return result;
    }

    /** 一条兑换码。 */
    public record Entry(
        String code,
        List<ItemReward> items,
        int xp,
        List<EffectReward> effects,
        boolean disabled
    ) {
        static Entry from(String code, JsonObject obj) {
            List<ItemReward> items = new ArrayList<>();
            JsonArray arr = obj.has("items") && obj.get("items").isJsonArray() ? obj.getAsJsonArray("items") : null;
            if (arr != null) {
                for (JsonElement el : arr) {
                    if (!el.isJsonArray()) {
                        continue;
                    }
                    JsonArray pair = el.getAsJsonArray();
                    if (pair.size() < 1) {
                        continue;
                    }
                    items.add(new ItemReward(
                        pair.get(0).getAsString(),
                        pair.size() > 1 ? pair.get(1).getAsInt() : 1
                    ));
                }
            }

            int xp = obj.has("xp") ? obj.get("xp").getAsInt() : 0;

            List<EffectReward> effects = new ArrayList<>();
            JsonArray effArr = obj.has("effects") && obj.get("effects").isJsonArray()
                ? obj.getAsJsonArray("effects") : null;
            if (effArr != null) {
                for (JsonElement el : effArr) {
                    if (!el.isJsonArray()) {
                        continue;
                    }
                    JsonArray triple = el.getAsJsonArray();
                    if (triple.size() < 1) {
                        continue;
                    }
                    effects.add(new EffectReward(
                        triple.get(0).getAsString(),
                        triple.size() > 1 ? triple.get(1).getAsInt() : 200,
                        triple.size() > 2 ? triple.get(2).getAsInt() : 0
                    ));
                }
            }

            boolean disabled = obj.has("disabled") && obj.get("disabled").getAsBoolean();

            if (items.isEmpty() && xp <= 0 && effects.isEmpty()) {
                return null;
            }
            return new Entry(code, List.copyOf(items), xp, List.copyOf(effects), disabled);
        }

        JsonObject toJson() {
            JsonObject obj = new JsonObject();

            if (!this.items.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (ItemReward r : this.items) {
                    JsonArray pair = new JsonArray();
                    pair.add(r.itemId());
                    pair.add(r.count());
                    arr.add(pair);
                }
                obj.add("items", arr);
            }

            if (this.xp > 0) {
                obj.addProperty("xp", this.xp);
            }

            if (!this.effects.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (EffectReward r : this.effects) {
                    JsonArray triple = new JsonArray();
                    triple.add(r.effectId());
                    triple.add(r.durationTicks());
                    triple.add(r.amplifier());
                    arr.add(triple);
                }
                obj.add("effects", arr);
            }

            if (this.disabled) {
                obj.addProperty("disabled", true);
            }

            return obj;
        }
    }

    public record ItemReward(String itemId, int count) {
    }

    public record EffectReward(String effectId, int durationTicks, int amplifier) {
    }
}
