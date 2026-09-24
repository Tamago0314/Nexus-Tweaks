package com.hikariserver.nexustweaks;

import net.fabricmc.loader.api.FabricLoader;

/**
 * MOD 全体で使う定数置き場。
 *
 * <p>ここを 1 か所にまとめておくと、mod id を使う箇所（設定ファイル名・翻訳キー・
 * ネットワークチャンネル ID・ログ名など）が散らばらずに済む。</p>
 */
public final class Reference {

    /** fabric.mod.json の "id" と必ず一致させること。設定ファイル名や翻訳キーの接頭辞にも使う。 */
    public static final String MOD_ID = "nexus-tweaks";

    /** 画面タイトルや malilib のホットキーカテゴリ名に使う、人間向けの表示名。 */
    public static final String MOD_NAME = "Nexus-Tweaks";

    /**
     * MOD のバージョン。
     *
     * <p>ハードコードせず fabric.mod.json（＝gradle.properties の mod_version）から読む。
     * こうしておけばバージョンを上げたときにここを直し忘れることがない。</p>
     */
    public static final String MOD_VERSION = FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            // メタデータが取れた場合はそのバージョン文字列を使う
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            // 取れないケース（テスト実行など）でも落ちないようにフォールバックを用意する
            .orElse("unknown");

    /** 定数だけのクラスなのでインスタンス化を禁止する。 */
    private Reference() {
    }
}
