package com.hikariserver.nexustweaks.network;

import com.hikariserver.nexustweaks.feature.RepairStatus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * サーバー → クライアントの「修理要求の処理結果」通知。
 *
 * クライアントはこれを受け取って、チャットやアクションバーへ結果を表示する。
 *
 * @param statusId 結果の種類（RepairStatus の整数 ID）
 * @param repaired 実際に回復した耐久値
 * @param xpSpent  実際に消費した経験値ポイント数
 */
public record RepairResultPayload(int statusId, int repaired, int xpSpent) implements CustomPacketPayload {

    /** このパケットのチャンネル ID。 */
    public static final CustomPacketPayload.Type<RepairResultPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(NexusNetworking.CHANNEL_NAMESPACE, "repair_result"));

    /**
     * バイト列との相互変換。
     *
     * VAR_INT を 3 個、宣言した順番どおりに読み書きする。
     * 送信側と受信側で順番が食い違うと壊れるので、フィールドを増やすときは末尾に足すこと。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairResultPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RepairResultPayload::statusId,
                    ByteBufCodecs.VAR_INT, RepairResultPayload::repaired,
                    ByteBufCodecs.VAR_INT, RepairResultPayload::xpSpent,
                    RepairResultPayload::new);

    /** 整数 ID を enum へ戻して返す。知らない ID なら UNKNOWN になる。 */
    public RepairStatus getStatus() {
        return RepairStatus.fromId(this.statusId);
    }

    /** Minecraft 側がチャンネルを判別するために使う。 */
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
