package com.cirserver.createciraddition.boss;

import com.cirserver.createciraddition.CreateCIRAddition;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/** Atomic, explicitly reloadable JSON configuration. Never links optional mod classes. */
public final class BossGuardConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Snapshot current = new Snapshot(new Global(), Map.of());
    private BossGuardConfig() {}

    public static final class Group {
        public String mod;
        public List<String> bossList = new ArrayList<>();
        public double lowHealthDamageRatio = 0.10;
        public double lowHealthMinDamage = 15.0;
        public double lowHealthSkipThreshold = 15.0;
        public Group() {}
        private Group(String mod, String... bosses) {
            this.mod = mod;
            this.bossList = new ArrayList<>(List.of(bosses));
        }
    }

    public static final class Global {
        public double singleHitCapRatio = 0.50;
        public double lowHealthThresholdRatio = 0.50;
        public double burstTriggerDamage = 100.0;
        public long berserkDurationMs = 10000;
        public long berserkCooldownMs = 20000;
        public double berserkAttackBonus = 10.0;
        public double halfHealthHealSmall = 40.0;
        public double halfHealthHealMedium = 60.0;
        public double halfHealthHealLarge = 80.0;
        public List<Double> halfHealthHealThresholds = List.of(400.0, 700.0);
        public double particleRadius = 2.5;
        public int executionBlockStripCount = 5;
    }

    public static final class Document {
        public List<Group> bossGroups = defaults();
        public Global global = new Global();
    }

    public record Snapshot(Global global, Map<ResourceLocation, Group> bosses) {}

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("create_cir_addition-boss-guard.json");
    }

    public static Snapshot current() { return current; }

    public static boolean enabled() {
        ModList list = ModList.get();
        return list != null && list.isLoaded("apotheosis");
    }

    public static boolean reload() {
        if (!enabled()) return false;
        try {
            Path file = path();
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, GSON.toJson(new Document()) + "\n", StandardCharsets.UTF_8);
            }
            Document doc = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Document.class);
            validate(doc);
            Map<ResourceLocation, Group> bosses = new LinkedHashMap<>();
            for (Group group : doc.bossGroups) {
                if (!ModList.get().isLoaded(group.mod)) continue;
                for (String name : group.bossList) {
                    ResourceLocation id = name == null ? null : ResourceLocation.tryParse(name);
                    if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                        CreateCIRAddition.LOGGER.warn("Boss Guard: unknown entity id ignored: {}", name);
                        continue;
                    }
                    bosses.putIfAbsent(id, group);
                }
            }
            current = new Snapshot(doc.global, Map.copyOf(bosses));
            CreateCIRAddition.LOGGER.info("Boss Guard: {} protected entity types loaded", bosses.size());
            return true;
        } catch (IOException | RuntimeException error) {
            CreateCIRAddition.LOGGER.error("Boss Guard: invalid config; keeping the last valid snapshot", error);
            return false;
        }
    }

    public static void validate(Document doc) {
        if (doc == null || doc.global == null || doc.bossGroups == null) throw new IllegalArgumentException("Missing global/bossGroups");
        Global g = doc.global;
        ratio(g.singleHitCapRatio, "singleHitCapRatio");
        ratio(g.lowHealthThresholdRatio, "lowHealthThresholdRatio");
        positive(g.burstTriggerDamage, "burstTriggerDamage");
        if (g.berserkDurationMs < 1 || g.berserkDurationMs > 86400000 || g.berserkCooldownMs < 0 || g.berserkCooldownMs > 86400000)
            throw new IllegalArgumentException("Berserk timings must be within one day");
        nonnegative(g.berserkAttackBonus, "berserkAttackBonus");
        nonnegative(g.halfHealthHealSmall, "halfHealthHealSmall");
        nonnegative(g.halfHealthHealMedium, "halfHealthHealMedium");
        nonnegative(g.halfHealthHealLarge, "halfHealthHealLarge");
        if (g.halfHealthHealThresholds == null || g.halfHealthHealThresholds.size() != 2)
            throw new IllegalArgumentException("Exactly two heal thresholds required");
        positive(g.halfHealthHealThresholds.get(0), "first heal threshold");
        positive(g.halfHealthHealThresholds.get(1), "second heal threshold");
        if (g.halfHealthHealThresholds.get(0) > g.halfHealthHealThresholds.get(1)) throw new IllegalArgumentException("Heal thresholds out of order");
        nonnegative(g.particleRadius, "particleRadius");
        if (g.particleRadius > 128 || g.executionBlockStripCount < 1) throw new IllegalArgumentException("Invalid particle radius/strip count");
        for (Group group : doc.bossGroups) {
            if (group == null || group.mod == null || group.mod.isBlank() || group.bossList == null)
                throw new IllegalArgumentException("Invalid boss group");
            ratio(group.lowHealthDamageRatio, "lowHealthDamageRatio");
            positive(group.lowHealthMinDamage, "lowHealthMinDamage");
            nonnegative(group.lowHealthSkipThreshold, "lowHealthSkipThreshold");
        }
    }

    private static void ratio(double value, String name) {
        positive(value, name);
        if (value > 1) throw new IllegalArgumentException(name + " must be <= 1");
    }
    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException("Invalid " + name);
    }
    private static void nonnegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid " + name);
    }

    private static List<Group> defaults() {
        Group vanilla = new Group("minecraft", "minecraft:wither", "minecraft:ender_dragon");
        vanilla.lowHealthMinDamage = 20.0;
        return new ArrayList<>(List.of(
            new Group("cataclysm", "cataclysm:ignis", "cataclysm:netherite_monstrosity", "cataclysm:ender_guardian",
                "cataclysm:the_harbinger", "cataclysm:the_leviathan", "cataclysm:ancient_remnant", "cataclysm:maledictus", "cataclysm:scylla"),
            new Group("twilightforest", "twilightforest:naga", "twilightforest:lich", "twilightforest:minoshroom", "twilightforest:hydra",
                "twilightforest:knight_phantom", "twilightforest:ur_ghast", "twilightforest:alpha_yeti", "twilightforest:snow_queen", "twilightforest:plateau_boss"),
            vanilla));
    }
}
