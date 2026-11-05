package com.cirserver.createciraddition.gift;

import com.cirserver.createciraddition.CreateCIRAddition;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 兑换码的服务端逻辑：校验、发奖、记账。 */
public final class GiftCodeService {

    private GiftCodeService() {
    }

    /** 兑换结果 */
    public enum Result {
        OK,
        INVALID,
        /** 码存在但已被作废 */
        DISABLED,
        ALREADY,
        BROKEN
    }

    public static Result redeem(ServerPlayer player, String rawCode) {
        if (rawCode == null) {
            return Result.INVALID;
        }
        String code = rawCode.trim().toLowerCase();
        if (code.isEmpty()) {
            return Result.INVALID;
        }

        Map<String, GiftCodeConfig.Entry> table = GiftCodeConfig.load();
        GiftCodeConfig.Entry entry = table.get(code);
        if (entry == null) {
            return Result.INVALID;
        }
        if (entry.disabled()) {
            return Result.DISABLED;
        }

        GiftCodeStore store = GiftCodeStore.get(player.server);
        if (store.hasRedeemed(code, player.getUUID())) {
            return Result.ALREADY;
        }

        if (!grant(player, entry)) {
            return Result.BROKEN;
        }

        store.markRedeemed(code, player.getUUID());
        CreateCIRAddition.LOGGER.info(
            "Create CIR Addition: {} redeemed gift code '{}'", player.getName().getString(), code
        );
        return Result.OK;
    }

    private static boolean grant(ServerPlayer player, GiftCodeConfig.Entry entry) {
        boolean any = false;

        for (GiftCodeConfig.ItemReward reward : entry.items()) {
            Item item = resolveItem(reward.itemId());
            if (item == null) {
                CreateCIRAddition.LOGGER.warn(
                    "Create CIR Addition: gift code '{}' references unknown item '{}'", entry.code(), reward.itemId()
                );
                continue;
            }
            ItemStack stack = new ItemStack(item, Math.max(1, reward.count()));
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
            any = true;
        }

        if (entry.xp() > 0) {
            player.giveExperiencePoints(entry.xp());
            any = true;
        }

        for (GiftCodeConfig.EffectReward reward : entry.effects()) {
            Holder<MobEffect> effect = resolveEffect(reward.effectId());
            if (effect == null) {
                CreateCIRAddition.LOGGER.warn(
                    "Create CIR Addition: gift code '{}' references unknown effect '{}'",
                    entry.code(), reward.effectId()
                );
                continue;
            }
            player.addEffect(new MobEffectInstance(
                effect, Math.max(1, reward.durationTicks()), Math.max(0, reward.amplifier())
            ));
            any = true;
        }

        return any;
    }

    // ------------------------------------------------------------------
    // 供管理界面校验用
    // ------------------------------------------------------------------

    public static Item resolveItem(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return null;
        }
        return item;
    }

    public static Holder<MobEffect> resolveEffect(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return null;
        }
        return BuiltInRegistries.MOB_EFFECT.getHolder(id).orElse(null);
    }

    /** 解析「id,数量;id,数量」这种输入，只保留合法项。 */
    public static ParseResult parseItems(String raw) {
        List<GiftCodeConfig.ItemReward> ok = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return new ParseResult(ok, bad);
        }
        for (String seg : raw.split(";")) {
            String s = seg.trim();
            if (s.isEmpty()) {
                continue;
            }
            String[] parts = s.split(",");
            String id = parts[0].trim();
            Item item = resolveItem(id);
            if (item == null) {
                bad.add(id);
                continue;
            }
            int count = 1;
            if (parts.length > 1) {
                try {
                    count = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    count = 1;
                }
            }
            ok.add(new GiftCodeConfig.ItemReward(id, Math.max(1, count)));
        }
        return new ParseResult(ok, bad);
    }

    /** 解析「id,秒,等级;...」，只保留合法项。 */
    public static EffectParseResult parseEffects(String raw) {
        List<GiftCodeConfig.EffectReward> ok = new ArrayList<>();
        List<String> bad = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return new EffectParseResult(ok, bad);
        }
        for (String seg : raw.split(";")) {
            String s = seg.trim();
            if (s.isEmpty()) {
                continue;
            }
            String[] parts = s.split(",");
            String id = parts[0].trim();
            if (resolveEffect(id) == null) {
                bad.add(id);
                continue;
            }
            int seconds = 10;
            int amp = 0;
            if (parts.length > 1) {
                try {
                    seconds = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    seconds = 10;
                }
            }
            if (parts.length > 2) {
                try {
                    amp = Integer.parseInt(parts[2].trim());
                } catch (NumberFormatException e) {
                    amp = 0;
                }
            }
            ok.add(new GiftCodeConfig.EffectReward(id, Math.max(1, seconds) * 20, Math.max(0, amp)));
        }
        return new EffectParseResult(ok, bad);
    }

    public record ParseResult(List<GiftCodeConfig.ItemReward> items, List<String> badIds) {
    }

    public record EffectParseResult(List<GiftCodeConfig.EffectReward> effects, List<String> badIds) {
    }
}
