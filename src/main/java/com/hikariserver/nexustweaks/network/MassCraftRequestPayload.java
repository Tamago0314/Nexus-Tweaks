package com.hikariserver.nexustweaks.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * クライアント → サーバーの「このレシピでまとめてクラフトしてください」要求（massCraft の代行）。
 *
 * サーバー側 MOD（Nexus-Sync）の同名クラスと
 * チャンネル ID・フィールドの順番を必ず一致させること。
 *
 * 送るのは「何を作りたいか」だけ。
 * 素材が足りるか・何回作るか・実際に作るかは、すべてサーバーが自分の持っている情報で判断する。
 *
 * @param containerId    今開いている画面の ID（サーバーが「同じ画面か」を確かめるのに使う）
 * @param gridWidth      クラフトグリッドの幅（作業台なら 3、インベントリなら 2）
 * @param gridHeight     クラフトグリッドの高さ
 * @param pattern        グリッドの並び（左上から右へ、行ごと）。空きマスは ItemStack.EMPTY
 * @param expectedResult 期待している成果物（サーバーは照合にだけ使う）
 * @param maxCrafts      1 回の要求で回したいクラフト回数（サーバー設定の上限で切り詰められる）
 */
public record MassCraftRequestPayload(
        int containerId,
        int gridWidth,
        int gridHeight,
        List<ItemStack> pattern,
        ItemStack expectedResult,
        int maxCrafts) implements CustomPacketPayload {

    /** グリッドの一辺の上限。サーバーはこれより大きい要求を受け付けない。 */
    public static final int MAX_GRID_SIDE = 3;

    /** グリッドのマス数の上限。 */
    public static final int MAX_GRID_SLOTS = MAX_GRID_SIDE * MAX_GRID_SIDE;

    /** このパケットのチャンネル ID。 */
    public static final CustomPacketPayload.Type<MassCraftRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    Identifier.fromNamespaceAndPath(NexusNetworking.CHANNEL_NAMESPACE, "mass_craft_request"));

    /**
     * アイテム 1 個分の読み書き。
     *
     * サーバー側は検証付きのコーデックで読むので、こちらも同じものを使って形をそろえておく
     * （検証は読み込み時にしか働かず、書き込む内容は変わらない）。
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> ITEM_CODEC =
            ItemStack.validatedStreamCodec(ItemStack.OPTIONAL_STREAM_CODEC);

    /**
     * バイト列との相互変換。
     *
     * 宣言した順番どおりに読み書きする。フィールドを増やすときは末尾に足すこと。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, MassCraftRequestPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MassCraftRequestPayload::containerId,
                    ByteBufCodecs.VAR_INT, MassCraftRequestPayload::gridWidth,
                    ByteBufCodecs.VAR_INT, MassCraftRequestPayload::gridHeight,
                    ITEM_CODEC.apply(ByteBufCodecs.list(MAX_GRID_SLOTS)), MassCraftRequestPayload::pattern,
                    ITEM_CODEC, MassCraftRequestPayload::expectedResult,
                    ByteBufCodecs.VAR_INT, MassCraftRequestPayload::maxCrafts,
                    MassCraftRequestPayload::new);

    /** Minecraft 側がチャンネルを判別するために使う。 */
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
