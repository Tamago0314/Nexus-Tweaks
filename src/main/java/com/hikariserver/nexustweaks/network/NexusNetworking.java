package com.hikariserver.nexustweaks.network;

import com.hikariserver.nexustweaks.NexusTweaks;
import com.hikariserver.nexustweaks.feature.MassCraftClient;
import com.hikariserver.nexustweaks.feature.RepairStatus;

import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * サーバー側 MOD（Nexus-Sync）との通信を担当するクライアント側の窓口。
 *
 * 通信の考え方は「サーバー権限」。
 * クライアントは「修理して」「このレシピでまとめてクラフトして」と要求するだけで、
 * できるかどうかの確認・計算・実際の適用はすべてサーバーが行う。
 */
public final class NexusNetworking {

    /**
     * パケットのチャンネル名前空間。
     *
     * サーバー側 MOD の名前（nexus-sync）に合わせてある。
     * クライアントとサーバーでここが食い違うと通信が成立しないので、
     * 片方を変えたら必ずもう片方も変えること。
     */
    public static final String CHANNEL_NAMESPACE = "nexus-sync";

    /**
     * プロトコルのバージョン。
     *
     * 既にあるパケットの中身を変えたらここを上げる。
     * サーバーから来たバージョンがこれと違う場合は、通信せず「非対応」として扱う。
     *
     * 機能を足すときは上げずに新しいチャンネルを足し、canSend でサーバー側にそのチャンネルがあるかを見る
     * （massCraft の代行がこの方式）。こうしておけば、massCraft を知らない古い Nexus-Sync でも
     * Auto Repair はそのまま使える。
     */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * クライアント → サーバーのカスタムパケットの中身の上限（バイト）。
     *
     * バニラの上限は 32767 バイトで、超えるとサーバー側の読み込みで失敗して切断される。
     * チャンネル名などの分の余白を取って少し小さくしてある。
     */
    private static final int MAX_SERVERBOUND_PAYLOAD_BYTES = 32000;

    /**
     * 接続中のサーバーが Nexus-Sync に対応しているか。
     *
     * ハンドシェイクを受け取ったら true になり、切断時に false へ戻る。
     * ネットワークスレッドから書かれてクライアントスレッドから読まれるため volatile にする。
     */
    private static volatile boolean serverSupported = false;

    /** ユーティリティクラスなのでインスタンス化を禁止する。 */
    private NexusNetworking() {
    }

    /**
     * パケット型の登録と受信ハンドラの設置。
     *
     * MOD の初期化時に 1 度だけ呼ぶこと。
     */
    public static void register() {
        // ── Nexus-Sync と同居している場合 ────────────────────────────
        // 同じ環境に Nexus-Sync も入っている場合（シングルプレイでの動作確認など）は、通信を一切使わない。
        //
        // Nexus-Sync は ModInitializer の中で、同じチャンネル ID に自分のクラスでパケット型を登録する
        // （main エントリポイントなので必ずこちらより先に走る）。
        // Fabric はチャンネル ID ごとに型を 1 つしか登録できず、受信ハンドラもクラスを確かめずに
        // ID だけで呼び出すため、そのまま通信すると次の食い違いが起きる。
        //   - 受信: 向こうのクラスのパケットがこちらのハンドラへ届き、ClassCastException になる
        //   - 送信: こちらの修理要求を向こうのコーデックが受け付けず、IllegalStateException になる
        // 公開 API では登録済みのコーデックを取り出せないので、両者のクラスを変換することもできない。
        //
        // 受信ハンドラを登録しなければ、Nexus-Sync はハンドシェイクを送ってこない（canSend で確認している）。
        // serverSupported は false のままになり、シングルプレイでは AutoRepair が
        // 統合サーバーへの直接適用に回るので、修理の結果は変わらない。
        // 代わりに、両方入りのクライアントでマルチプレイの Nexus-Sync サーバーへ入ると「非対応」扱いになる。
        // massCraft の代行も使えなくなり、Item Scroller の通常の処理で動く。
        if (FabricLoader.getInstance().isModLoaded("nexus-sync")) {
            NexusTweaks.LOGGER.info("Nexus-Sync が同じ環境に存在するため、サーバーとの通信は使いません。"
                    + "シングルプレイでは統合サーバーへ直接適用します。");
            return;
        }

        // ── パケット型の登録 ────────────────────────────────────────
        // 送受信するチャンネルは、方向ごとに型を登録しておく必要がある。

        // 受け取る側（サーバー → クライアント）。
        PayloadTypeRegistry.clientboundPlay().register(HandshakePayload.TYPE, HandshakePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RepairResultPayload.TYPE, RepairResultPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MassCraftResultPayload.TYPE, MassCraftResultPayload.CODEC);

        // 送る側（クライアント → サーバー）。
        // 送信するだけでも型の登録は必要。
        PayloadTypeRegistry.serverboundPlay().register(RepairRequestPayload.TYPE, RepairRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(MassCraftRequestPayload.TYPE, MassCraftRequestPayload.CODEC);

        // ── ハンドシェイクの受信 ────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(HandshakePayload.TYPE, (payload, context) -> {
            // プロトコルが食い違う場合は、無理に通信せず非対応扱いにする。
            if (payload.protocolVersion() != PROTOCOL_VERSION) {
                NexusTweaks.LOGGER.warn(
                        "Nexus-Sync のプロトコルバージョンが一致しません (server={}, client={})。連携を無効にします。",
                        payload.protocolVersion(), PROTOCOL_VERSION);
                serverSupported = false;
                return;
            }

            serverSupported = true;
            NexusTweaks.LOGGER.info("Nexus-Sync 対応サーバーを検出しました。サーバー経由で修理を行います。");
        });

        // ── 修理結果の受信 ──────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(RepairResultPayload.TYPE, (payload, context) -> {
            // 受信ハンドラはネットワークスレッドで呼ばれることがあるので、
            // 画面へ触る処理はクライアントスレッドへ渡す。
            context.client().execute(() -> showResultMessage(
                    payload.getStatus(), payload.repaired(), payload.xpSpent()));
        });

        // ── massCraft の代行結果の受信 ──────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(MassCraftResultPayload.TYPE, (payload, context) -> {
            // 状態はクライアントスレッドでだけ触るので、そちらへ渡す。
            context.client().execute(() -> MassCraftClient.onResult(payload.getStatus(), payload.crafted()));
        });

        // ── 切断時のリセット ────────────────────────────────────────
        // 対応サーバーから未対応サーバーへ繋ぎ直したときに
        // 「対応している」と誤認したままにならないよう、必ず落とす。
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            serverSupported = false;
            client.execute(MassCraftClient::reset);
        });
    }

    /**
     * 接続中のサーバーが Nexus-Sync に対応しているかを返す。
     */
    public static boolean isServerSupported() {
        return serverSupported;
    }

    /**
     * サーバーへ「メインハンドの道具を修理して」と要求する。
     */
    public static void requestRepair() {
        // 相手がこのチャンネルを受け取れることを確認してから送る。
        // 未対応サーバーへ未知のチャンネルを送ると切断されることがある。
        if (!ClientPlayNetworking.canSend(RepairRequestPayload.TYPE)) {
            NexusTweaks.LOGGER.warn("サーバーが修理要求チャンネルを受け付けないため、送信を中止しました。");
            return;
        }

        ClientPlayNetworking.send(RepairRequestPayload.INSTANCE);
    }

    /**
     * 接続中のサーバーに massCraft の代行を頼めるかを返す。
     *
     * ハンドシェイク済み（＝Nexus-Sync でプロトコルが一致）で、かつサーバーが代行のチャンネルを
     * 受け付けている必要がある。massCraft を知らない古い Nexus-Sync では後者が false になる。
     */
    public static boolean canSendMassCraft() {
        return serverSupported && ClientPlayNetworking.canSend(MassCraftRequestPayload.TYPE);
    }

    /**
     * サーバーへ massCraft の代行を要求する。
     *
     * @return 送れたら true。送れなかったら false（呼び出し側は Item Scroller の通常の処理に任せる）
     */
    public static boolean sendMassCraftRequest(MassCraftRequestPayload request) {
        if (!canSendMassCraft()) {
            return false;
        }

        // 大きすぎるパケットは、サーバー側で読めずにこちらが切断される。
        // 中身の詰まったシュルカーボックスを素材にするレシピなど、アイテムのデータが重いときの保険。
        int size = measureSize(request);

        if (size < 0 || size > MAX_SERVERBOUND_PAYLOAD_BYTES) {
            NexusTweaks.LOGGER.warn("massCraft の代行要求が大きすぎるため ({} バイト)、Item Scroller の通常の処理に任せます。", size);
            return false;
        }

        ClientPlayNetworking.send(request);
        return true;
    }

    /**
     * 要求を実際にバイト列へ変換して、大きさを測る。
     *
     * @return バイト数。測れなかったときは -1
     */
    private static int measureSize(MassCraftRequestPayload request) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();

        if (connection == null) {
            return -1;
        }

        // アイテムの読み書きにはレジストリの情報が要るので、接続中のものを渡す。
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), connection.registryAccess());

        try {
            MassCraftRequestPayload.CODEC.encode(buf, request);
            return buf.readableBytes();
        } catch (Exception e) {
            NexusTweaks.LOGGER.warn("massCraft の代行要求を組み立てられませんでした", e);
            return -1;
        } finally {
            buf.release();
        }
    }

    /**
     * 修理結果をユーザーへ表示する。
     *
     * サーバー経由の場合もシングルプレイ直接実行の場合も、最終的にここを通す。
     * 呼び出しはクライアントスレッドから行うこと。
     */
    public static void showResultMessage(RepairStatus status, int repaired, int xpSpent) {
        if (status == RepairStatus.OK) {
            // 成功したときは何がどれだけ起きたかを具体的に出す。
            InfoUtils.showInGameMessage(Message.MessageType.SUCCESS,
                    status.getMessageKey(), repaired, xpSpent);
        } else {
            // 失敗理由は警告として出す。数値は入らないので引数は渡さない。
            InfoUtils.showInGameMessage(Message.MessageType.WARNING, status.getMessageKey());
        }
    }
}
