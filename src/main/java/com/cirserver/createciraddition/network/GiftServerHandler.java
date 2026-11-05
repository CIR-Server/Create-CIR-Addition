package com.cirserver.createciraddition.network;

import com.cirserver.createciraddition.CreateCIRAddition;
import com.cirserver.createciraddition.gift.GiftCodeConfig;
import com.cirserver.createciraddition.gift.GiftCodeService;
import com.cirserver.createciraddition.gift.GiftCodeStore;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 兑换码的服务端处理。 */
public final class GiftServerHandler {

    private GiftServerHandler() {
    }

    // ------------------------------------------------------------------
    // 玩家兑换
    // ------------------------------------------------------------------

    public static void handleSubmit(GiftSubmitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            GiftCodeService.Result result = GiftCodeService.redeem(player, payload.code());
            PacketDistributor.sendToPlayer(player, new GiftResultPayload(toWire(result)));
        });
    }

    private static GiftResultPayload.Result toWire(GiftCodeService.Result result) {
        return switch (result) {
            case OK -> GiftResultPayload.Result.OK;
            case DISABLED -> GiftResultPayload.Result.ALREADY;
            case ALREADY -> GiftResultPayload.Result.ALREADY;
            case BROKEN -> GiftResultPayload.Result.BROKEN;
            case INVALID -> GiftResultPayload.Result.INVALID;
        };
    }

    // ------------------------------------------------------------------
    // 管理界面
    // ------------------------------------------------------------------

    /** 打开管理界面时把列表推下去 */
    public static void sendManageData(ServerPlayer player) {
        Map<String, GiftCodeConfig.Entry> table = GiftCodeConfig.load();
        List<GiftManageDataPayload.Row> rows = new ArrayList<>(table.size());
        for (GiftCodeConfig.Entry entry : table.values()) {
            rows.add(new GiftManageDataPayload.Row(entry.code(), entry.disabled()));
        }
        PacketDistributor.sendToPlayer(player, new GiftManageDataPayload(rows));
    }

    /** 玩家看完免责声明，打开目标界面 */
    public static void handleDisclaimerAck(GiftDisclaimerAckPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            if (payload.manage()) {
                com.cirserver.createciraddition.gift.GiftManageMenu.open(player);
            } else {
                com.cirserver.createciraddition.gift.GiftCodeMenu.open(player);
            }
        });
    }

    public static void handleManageAction(GiftManageActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            String code = payload.code() == null ? "" : payload.code();
            Map<String, GiftCodeConfig.Entry> table = new LinkedHashMap<>(GiftCodeConfig.load());

            switch (payload.action()) {
                case DISABLE -> {
                    GiftCodeConfig.Entry old = table.get(code);
                    if (old != null) {
                        table.put(code, new GiftCodeConfig.Entry(
                            old.code(), old.items(), old.xp(), old.effects(), true
                        ));
                        GiftCodeConfig.save(table);
                    }
                }
                case DELETE -> {
                    if (table.remove(code) != null) {
                        GiftCodeConfig.save(table);
                    }
                    GiftCodeStore.get(player.server).clearCode(code);
                }
                case REFRESH -> {
                    // 什么都不改，重新读一次就行
                }
            }

            sendManageData(player);
        });
    }

    public static void handleCreate(GiftCreatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            String code = payload.code() == null ? "" : payload.code().trim().toLowerCase();
            if (code.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new GiftCreateResultPayload(false, List.of()));
                return;
            }

            GiftCodeService.ParseResult itemResult = GiftCodeService.parseItems(payload.items());
            GiftCodeService.EffectParseResult effectResult = GiftCodeService.parseEffects(payload.effects());

            List<String> bad = new ArrayList<>();
            bad.addAll(itemResult.badIds());
            bad.addAll(effectResult.badIds());

            if (!bad.isEmpty()) {
                PacketDistributor.sendToPlayer(player, new GiftCreateResultPayload(false, bad));
                return;
            }

            int xp = 0;
            try {
                String raw = payload.xp() == null ? "" : payload.xp().trim();
                if (!raw.isEmpty()) {
                    xp = Math.max(0, Integer.parseInt(raw));
                }
            } catch (NumberFormatException ignored) {
                xp = 0;
            }

            if (itemResult.items().isEmpty() && effectResult.effects().isEmpty() && xp <= 0) {
                PacketDistributor.sendToPlayer(player, new GiftCreateResultPayload(false, List.of()));
                return;
            }

            Map<String, GiftCodeConfig.Entry> table = new LinkedHashMap<>(GiftCodeConfig.load());
            table.put(code, new GiftCodeConfig.Entry(
                code, List.copyOf(itemResult.items()), xp, List.copyOf(effectResult.effects()), false
            ));

            if (!GiftCodeConfig.save(table)) {
                CreateCIRAddition.LOGGER.warn("Create CIR Addition: failed to save gift code '{}'", code);
                PacketDistributor.sendToPlayer(player, new GiftCreateResultPayload(false, List.of()));
                return;
            }

            PacketDistributor.sendToPlayer(player, new GiftCreateResultPayload(true, List.of()));
            sendManageData(player);
        });
    }
}
