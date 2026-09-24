package com.hikariserver.nexustweaks.feature;

/**
 * 修理を試みた結果の種類。
 *
 * サーバー側 MOD（Nexus-Sync）との通信でも使うため、
 * 各値に「通信で流す整数 ID」を持たせてある。
 * enum の並び順（ordinal）ではなく明示的な ID を使うのは、
 * 将来値を挿入しても既存クライアントとの互換が壊れないようにするため。
 */
public enum RepairStatus {

    /** 修理に成功した。 */
    OK(0),

    /** メインハンドが空だった。 */
    EMPTY_HAND(1),

    /** そのアイテムは耐久値を持たない（＝修理できない）。 */
    NOT_DAMAGEABLE(2),

    /** 耐久値は満タンなので直すところが無い。 */
    NOT_DAMAGED(3),

    /** 経験値が 1 ポイントも無い。 */
    NO_XP(4),

    /** サーバー側で機能が無効にされている。 */
    DISABLED(5),

    /** 未知の値（新しいサーバーから知らない ID が来たときのフォールバック）。 */
    UNKNOWN(-1);

    /** 通信で流す整数 ID。 */
    private final int id;

    RepairStatus(int id) {
        this.id = id;
    }

    /** 通信で流す整数 ID を返す。 */
    public int getId() {
        return this.id;
    }

    /**
     * 整数 ID から enum へ戻す。
     *
     * 知らない ID が来た場合は UNKNOWN を返し、例外は投げない。
     * 相手が新しいバージョンでも落ちないようにするための保険。
     */
    public static RepairStatus fromId(int id) {
        for (RepairStatus status : values()) {
            if (status.id == id) {
                return status;
            }
        }

        return UNKNOWN;
    }

    /**
     * この結果をユーザーへ伝えるためのメッセージ翻訳キーを返す。
     *
     * lang ファイル側にこのキーを用意しておくこと。
     */
    public String getMessageKey() {
        return switch (this) {
            case OK -> "nexus-tweaks.message.repaired";
            case EMPTY_HAND -> "nexus-tweaks.message.empty_hand";
            case NOT_DAMAGEABLE -> "nexus-tweaks.message.not_damageable";
            case NOT_DAMAGED -> "nexus-tweaks.message.not_damaged";
            case NO_XP -> "nexus-tweaks.message.no_xp";
            case DISABLED -> "nexus-tweaks.message.disabled";
            case UNKNOWN -> "nexus-tweaks.message.unknown";
        };
    }
}
