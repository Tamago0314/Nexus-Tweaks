package com.hikariserver.nexustweaks.feature;

/**
 * massCraft の代行を試みた結果の種類。
 *
 * サーバー側 MOD（Nexus-Sync）の同名 enum と ID を必ず一致させること。
 * enum の並び順（ordinal）ではなく明示的な ID を使うのは、
 * 将来値を挿入しても既存クライアントとの互換が壊れないようにするため。
 */
public enum MassCraftStatus {

    /** 1 回以上クラフトできた。 */
    OK(0),

    /** 対象の画面が開いていない／画面が入れ替わった／作業台から離れた。 */
    NO_SCREEN(1),

    /** レシピが見つからない、または要求した成果物と一致しない。 */
    NO_RECIPE(2),

    /** 素材が足りず、1 回もクラフトできなかった。 */
    NO_MATERIAL(3),

    /** 要求の間隔が短すぎる（次の tick に送り直せば通る）。 */
    RATE_LIMITED(4),

    /** サーバー設定で無効にされている、または権限が足りない。 */
    DISABLED(5),

    /** doLimitedCrafting が有効で、そのレシピをまだ解禁していない。 */
    LIMITED_CRAFTING(6),

    /** 不変条件が壊れたのでサーバーが中断した（素材が減らないレシピなど）。 */
    ABORTED(7),

    /** 要求の中身がおかしいとサーバーに判断された（こちらのバグの可能性が高い）。 */
    INVALID_REQUEST(8),

    /**
     * 途中まで作ったが、サーバーが 1 回の要求に使ってよい時間を使い切ったので中断した。
     *
     * 続きがあるということなので、間を空けずにそのまま次を要求してよい。
     */
    PARTIAL(9),

    /** 未知の値（新しいサーバーから知らない ID が来たときのフォールバック）。 */
    UNKNOWN(-1);

    /** 通信で流す整数 ID。 */
    private final int id;

    MassCraftStatus(int id) {
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
    public static MassCraftStatus fromId(int id) {
        for (MassCraftStatus status : values()) {
            if (status.id == id) {
                return status;
            }
        }

        return UNKNOWN;
    }
}
