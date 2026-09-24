package com.hikariserver.nexustweaks.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * サーバー → クライアントの「このサーバーは Nexus-Sync に対応しています」通知。
 *
 * クライアントはこれを受け取るまで、修理要求をサーバーへ送らない。
 * 送っても処理されないうえ、未対応サーバーによっては
 * 未知のチャンネルを受け取った時点で切断されることがあるため。
 *
 * @param protocolVersion サーバー側が話すプロトコルのバージョン
 */
public record HandshakePayload(int protocolVersion) implements CustomPacketPayload {

    /**
     * このパケットのチャンネル ID。
     *
     * CustomPacketPayload.createType(String) は名前空間を強制的に "minecraft" にしてしまうので、
     * 自前の名前空間を使いたい場合は Type を直接 new すること。
     */
    public static final CustomPacketPayload.Type<HandshakePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(NexusNetworking.CHANNEL_NAMESPACE, "handshake"));

    /**
     * バイト列との相互変換。
     *
     * VAR_INT を 1 個だけ読み書きする単純な構成。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, HandshakePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, HandshakePayload::protocolVersion,
                    HandshakePayload::new);

    /** Minecraft 側がチャンネルを判別するために使う。 */
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
