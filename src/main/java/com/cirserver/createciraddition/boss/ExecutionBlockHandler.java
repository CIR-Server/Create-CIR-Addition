package com.cirserver.createciraddition.boss;

import dev.shadowsoffire.apotheosis.Apoth;
import dev.shadowsoffire.apotheosis.affix.ItemAffixes;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Referenced only from the optional Apotheosis mixin. */
public final class ExecutionBlockHandler {
    private static final ResourceLocation EXECUTING = ResourceLocation.parse("apotheosis:melee/executing");
    private static final String COUNTERS = BossCombatHandler.EXECUTION_COUNTERS;
    private ExecutionBlockHandler() {}

    public static void blocked(LivingEntity attacker, LivingEntity boss) {
        if (!BossCombatHandler.isProtected(boss) || !(attacker instanceof ServerPlayer player)) return;
        player.displayClientMessage(Component.translatable("event.cir.execution_blocked").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
        String key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).toString();
        CompoundTag counters = player.getPersistentData().getCompound(COUNTERS);
        int threshold = BossGuardConfig.current().global().executionBlockStripCount;
        int count = BossCombatRules.nextBlockedCount(counters.getInt(key), threshold);
        counters.putInt(key, count);
        player.getPersistentData().put(COUNTERS, counters);
        if (count < threshold) return;
        ItemStack stack = player.getMainHandItem();
        ItemAffixes affixes = stack.get(Apoth.Components.AFFIXES);
        if (affixes == null || affixes.isEmpty() || affixes.keySet().stream().noneMatch(holder -> EXECUTING.equals(holder.getId()))) return;
        ItemAffixes.Builder builder = affixes.toBuilder();
        builder.removeIf(holder -> EXECUTING.equals(holder.getId()));
        stack.set(Apoth.Components.AFFIXES, builder.build());
        counters.remove(key);
        player.getPersistentData().put(COUNTERS, counters);
        player.server.getPlayerList().broadcastSystemMessage(Component.translatable("event.cir.execution_affix_stripped",
            player.getGameProfile().getName(), boss.getType().getDescription()).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), false);
    }
}
