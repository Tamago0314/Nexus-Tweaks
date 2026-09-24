package com.hikariserver.nexustweaks.network;

import com.hikariserver.nexustweaks.feature.MassCraftStatus;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * サーバー → クライアントの「massCraft 要求の処理結果」通知。
 *
 * 要求 1 通につき必ず 1 通返ってくるので、クライアントはこれを
 * 「次の要求を送ってよい」という合図としても使う。
 *
 * @param statusId 結果の種類（MassCraftStatus の整数 ID）
 * @param crafted  実際にクラフトできた回数
 */
public record MassCraftResultPayload(int statusId, int crafted) implements CustomPacketPayload {

    /** このパケットのチャンネル ID。 */
    public static final CustomPacketPayload.Type<MassCraftResultPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(NexusNetworking.CHANNEL_NAMESPACE, "mass_craft_result"));

    /**
     * バイト列との相互変換。
     *
     * VAR_INT を 2 個、宣言した順番どおりに読み書きする。
     * 送信側と受信側で順番が食い違うと壊れるので、フィールドを増やすときは末尾に足すこと。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, MassCraftResultPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MassCraftResultPayload::statusId,
                    ByteBufCodecs.VAR_INT, MassCraftResultPayload::crafted,
                    MassCraftResultPayload::new);

    /** 整数 ID を enum へ戻して返す。知らない ID なら UNKNOWN になる。 */
    public MassCraftStatus getStatus() {
        return MassCraftStatus.fromId(this.statusId);
    }

    /** Minecraft 側がチャンネルを判別するために使う。 */
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
