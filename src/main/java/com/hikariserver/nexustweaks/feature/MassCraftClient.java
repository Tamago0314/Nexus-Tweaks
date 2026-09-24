package com.hikariserver.nexustweaks.feature;

import com.hikariserver.nexustweaks.NexusTweaks;
import com.hikariserver.nexustweaks.config.Configs;
import com.hikariserver.nexustweaks.network.MassCraftRequestPayload;
import com.hikariserver.nexustweaks.network.NexusNetworking;

import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;

/**
 * massCraft の代行のクライアント側の状態と、送信の可否の判断。
 *
 * 実際の作業はすべてサーバー（Nexus-Sync）が行うので、ここが持つのは
 * 「今送ってよいか」を決めるための状態だけ。
 *
 * 送信は「要求 1 通につき応答 1 通」を守る。応答が来るまで次を送らないので、
 * 回線が遅くても要求が溜まってサーバーに押し寄せることが無い。
 *
 * サーバーは 1 回の要求に使ってよい時間が決まっていて、作りきれない量を頼まれると
 * 途中まで作って PARTIAL を返す。その場合は待たずに次の要求を出し、続きを作らせる。
 *
 * このクラスは Item Scroller を参照しない。
 * Item Scroller に触れるのは compat.itemscroller と mixin.itemscroller の中だけにしておかないと、
 * Item Scroller が入っていない環境でクラスの読み込みに失敗する。
 *
 * すべてクライアントスレッドから呼ぶこと。
 */
public final class MassCraftClient {

    /**
     * 応答を待つ上限（tick）。
     *
     * 応答は必ず返ってくる作りだが、届かなかったときに二度と送れなくならないための保険。
     * 5 秒。短すぎると重いサーバーで空振りし、長すぎると止まったときの復帰が遅れる。
     */
    private static final int RESPONSE_TIMEOUT_TICKS = 100;

    /**
     * 1 回もクラフトできなかったとき（素材切れなど）に、次の要求まで空ける間隔（tick）。
     *
     * 押しっぱなし／massCraftHold のまま素材が尽きても、空振りの要求を毎 tick 送り続けないため。
     * 放置クラフトで素材が補充されたときに再開できるよう、止めてしまわずに間隔を空けるだけにする。
     */
    private static final int IDLE_RETRY_TICKS = 10;

    /** このクラスが数えている tick。間隔の計算にだけ使う。 */
    private static int clientTick;

    /** 要求を送って、まだ応答が来ていないか。 */
    private static boolean awaitingResponse;

    /** 最後に要求を送った tick。 */
    private static int awaitingSinceTick;

    /** 次の要求を送ってよくなる tick。 */
    private static int retryAfterTick;

    /**
     * 接続中のサーバーに「代行は無効」と言われたか。
     *
     * true の間は代行を使わず、Item Scroller の通常の処理に任せる。切断するまで続く。
     */
    private static boolean disabledByServer;

    /** ユーティリティクラスなのでインスタンス化を禁止する。 */
    private MassCraftClient() {
    }

    /**
     * 今の接続で代行を使えるかを返す。
     *
     * false のときは Item Scroller の通常の処理に任せる。
     */
    public static boolean isAvailable() {
        return Configs.Tweaks.MASS_CRAFT_ON_SERVER.getBooleanValue()
                && !disabledByServer
                && NexusNetworking.canSendMassCraft();
    }

    /** 今この tick に要求を送ってよいかを返す（応答待ちや、空振り後の待ち時間でないか）。 */
    public static boolean canSendNow() {
        return !awaitingResponse && clientTick - retryAfterTick >= 0;
    }

    /**
     * 要求を送る。
     *
     * @return 送れたら true。false なら代行できない要求なので、Item Scroller の通常の処理に任せること
     */
    public static boolean send(MassCraftRequestPayload request) {
        if (!NexusNetworking.sendMassCraftRequest(request)) {
            return false;
        }

        awaitingResponse = true;
        awaitingSinceTick = clientTick;
        return true;
    }

    /** サーバーから結果が返ってきたときに呼ばれる。 */
    public static void onResult(MassCraftStatus status, int crafted) {
        awaitingResponse = false;

        switch (status) {
            // サーバー側で無効にされている（または権限が無い）。
            // 送り続けても無駄なので、切断するまで Item Scroller の通常の処理に戻す。
            case DISABLED -> {
                if (!disabledByServer) {
                    disabledByServer = true;
                    NexusTweaks.LOGGER.info("サーバーで massCraft の代行が無効にされているため、Item Scroller の通常の処理に戻します。");
                    InfoUtils.showGuiOrInGameMessage(Message.MessageType.WARNING,
                            "nexus-tweaks.message.mass_craft_disabled");
                }
            }

            // 早すぎただけなので、次の tick に送り直してよい。
            // PARTIAL は「サーバーが時間の都合で途中まで作った」なので、続きをそのまま頼む。
            // どちらも待たずに次を送ってよい（送るのは tick ごとに 1 通まで）。
            case RATE_LIMITED, PARTIAL -> {
            }

            // こちらの組み立てた要求がおかしい、または知らない結果。バグの可能性が高いので記録を残す。
            case INVALID_REQUEST, UNKNOWN -> {
                NexusTweaks.LOGGER.warn("massCraft の代行要求が受け付けられませんでした (結果: {})", status);
                retryAfterTick = clientTick + IDLE_RETRY_TICKS;
            }

            // それ以外は、1 回も作れなかったときだけ少し間を空ける。
            default -> {
                if (crafted <= 0) {
                    retryAfterTick = clientTick + IDLE_RETRY_TICKS;
                }
            }
        }
    }

    /**
     * massCraft のキーを離した（massCraftHold も OFF）ときや、対象の画面を離れたときに呼ばれる。
     *
     * 空振り後の待ち時間を解除して、次に押したときはすぐ送れるようにする。
     */
    public static void onInactive() {
        retryAfterTick = clientTick;
    }

    /** 毎 tick の終わりに呼ばれる。応答待ちのタイムアウトを見る。 */
    public static void onClientTick() {
        clientTick++;

        if (awaitingResponse && clientTick - awaitingSinceTick > RESPONSE_TIMEOUT_TICKS) {
            awaitingResponse = false;
        }
    }

    /**
     * 切断したときに呼ばれる。
     *
     * 別のサーバーへ入り直したときに、前のサーバーの状態を持ち越さないため。
     */
    public static void reset() {
        awaitingResponse = false;
        retryAfterTick = clientTick;
        disabledByServer = false;
    }
}
