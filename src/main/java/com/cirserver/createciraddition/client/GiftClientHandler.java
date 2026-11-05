package com.cirserver.createciraddition.client;

import com.cirserver.createciraddition.network.GiftCreateResultPayload;
import com.cirserver.createciraddition.network.GiftManageDataPayload;
import com.cirserver.createciraddition.network.GiftResultPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 兑换码的客户端处理。只在客户端加载。 */
public final class GiftClientHandler {

    private GiftClientHandler() {
    }

    /** 兑换结果 */
    public static void handleResult(GiftResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof GiftCodeScreen screen) {
                screen.onResult(payload.result());
            }
        });
    }

    /** 管理界面的列表数据 */
    public static void handleManageData(GiftManageDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof GiftManageScreen screen) {
                screen.onData(payload);
            }
        });
    }

    /** 新建兑换码的校验结果 */
    public static void handleCreateResult(GiftCreateResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof GiftManageScreen screen) {
                screen.onCreateResult(payload.ok(), payload.badIds());
            }
        });
    }
}