package com.hikariserver.nexustweaks.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * クライアント → サーバーの「メインハンドの道具を修理してください」要求。
 *
 * 中身は空。
 * 「経験値を何ポイント使って耐久を何回復させる」といった数値をクライアントが決めて
 * 送ってしまうと、改造クライアントから好きな値を送られてしまう。
 * どのアイテムを対象にするか（＝選択中のホットバースロット）も
 * サーバーが自分の持っている情報から判断できるので、送る必要が無い。
 */
public record RepairRequestPayload() implements CustomPacketPayload {

    /** 中身が無いので、毎回 new せず使い回せるインスタンスを 1 個だけ持っておく。 */
    public static final RepairRequestPayload INSTANCE = new RepairRequestPayload();

    /** このパケットのチャンネル ID。 */
    public static final CustomPacketPayload.Type<RepairRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(NexusNetworking.CHANNEL_NAMESPACE, "repair_request"));

    /**
     * バイト列との相互変換。
     *
     * 送る中身が無いので、書き込みは何もせず、読み込みは常に同じインスタンスを返す
     * StreamCodec.unit を使う。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, RepairRequestPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    /** Minecraft 側がチャンネルを判別するために使う。 */
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
