package com.hikariserver.nexustweaks.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.hikariserver.nexustweaks.NexusTweaks;
import com.hikariserver.nexustweaks.Reference;

import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import net.fabricmc.loader.api.FabricLoader;

/**
 * この MOD の設定定義と、その JSON への読み書き。
 *
 * malilib の IConfigHandler として登録することで、
 * 「設定画面を閉じたとき」「ワールドへ入ったとき」など malilib が適切なタイミングで
 * load() / save() を呼んでくれる。
 *
 * 注意: malilib の WorldLoadHandler はワールド／サーバーへ参加するたびに
 * loadAllConfigs() を呼ぶ。つまり load() は何度も走る。
 * ここでは設定値を静的な Config オブジェクトへ書き戻すだけなので問題ないが、
 * 「設定オブジェクトそのものを差し替える」実装にしてはいけない。
 */
public class Configs implements IConfigHandler {

    /** 設定ファイル名。.minecraft/config/nexus-tweaks.json に置かれる。 */
    private static final String CONFIG_FILE_NAME = Reference.MOD_ID + ".json";

    /** 人が読める形で書き出したいので pretty printing を有効にした Gson を使い回す。 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 「General」カテゴリ。MOD 全体に関わる操作をまとめる。
     */
    public static class General {

        /*
         * 翻訳キーの接頭辞。
         *
         * malilib の apply(prefix) を呼ぶと、その設定の
         * 表示名 / 説明文 / prettyName の翻訳キーが自動で下記のように決まる。
         *   表示名 : <prefix>.name.<設定名>
         *   説明文 : <prefix>.comment.<設定名>
         *   整形名 : <prefix>.prettyName.<設定名>
         * lang ファイル側はこの規則に合わせて書くこと。
         */
        private static final String PREFIX = Reference.MOD_ID + ".config.general";

        /*
         * 設定画面を開くホットキー。
         *
         * malilib のキー表現はカンマ区切りで「同時押し」を表す。
         * つまり "N,T" は「N を押しながら T」で、画面上は "N + T" と表示される。
         */
        public static final ConfigHotkey OPEN_CONFIG =
                new ConfigHotkey("openConfig", "N,T").apply(PREFIX);

        /** General タブに並べる設定の一覧（この順序がそのまま画面に出る）。 */
        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                OPEN_CONFIG
        );

        /** malilib のキーバインドマネージャへ登録するホットキーの一覧。 */
        public static final ImmutableList<IHotkey> HOTKEYS = ImmutableList.of(
                OPEN_CONFIG
        );
    }

    /**
     * 「Tweaks」カテゴリ。個々の便利機能の ON/OFF をまとめる。
     */
    public static class Tweaks {

        /** 翻訳キーの接頭辞（General.PREFIX と同じ規則）。 */
        private static final String PREFIX = Reference.MOD_ID + ".config.tweaks";

        /*
         * Auto Repair 機能の ON/OFF。
         *
         * ConfigBooleanHotkeyed は「真偽値」と「その値をトグルするホットキー」を
         * セットで持つ設定。第 3 引数がホットキーの既定値で、"" は未割り当てを意味する。
         * 設定画面では ON/OFF ボタンとキー設定ボタンが 1 行にまとまって表示される。
         */
        public static final ConfigBooleanHotkeyed AUTO_REPAIR =
                new ConfigBooleanHotkeyed("autoRepair", true, "").apply(PREFIX);

        /*
         * Auto Repair の対象から外すアイテムの ID 一覧。
         *
         * 「耐久値があり、かつ空中を右クリックすると実際に何かが起きる」アイテムは
         * 修理に化けてしまうと困るので除外する。使用アニメーション（弓・盾・食料など）は
         * コード側で自動的に弾けるが、釣り竿はアニメーションが NONE のまま竿を投げるため
         * コードでは判別できない。そのため既定値として入れてある。
         */
        public static final ConfigStringList AUTO_REPAIR_EXCLUSIONS =
                new ConfigStringList("autoRepairExclusions", ImmutableList.of(
                        "minecraft:fishing_rod"
                )).apply(PREFIX);

        /*
         * Item Scroller の massCraft をサーバー（Nexus-Sync）に代行させるかどうか。
         *
         * 効くのは、クライアントに Item Scroller、サーバーに Nexus-Sync が入っているときだけ。
         * どちらかが無ければ、この設定に関係なく Item Scroller の通常の処理になる。
         * 代行の具合が悪いときに、すぐ元の動作へ戻せるよう切り替えを残しておく。
         */
        public static final ConfigBoolean MASS_CRAFT_ON_SERVER =
                new ConfigBoolean("massCraftOnServer", true).apply(PREFIX);

        /** Tweaks タブに並べる設定の一覧。 */
        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                AUTO_REPAIR,
                AUTO_REPAIR_EXCLUSIONS,
                MASS_CRAFT_ON_SERVER
        );

        /** malilib のキーバインドマネージャへ登録するホットキーの一覧。 */
        public static final ImmutableList<IHotkey> HOTKEYS = ImmutableList.of(
                AUTO_REPAIR
        );
    }

    /**
     * この MOD が持つ全ホットキーをまとめて返す。
     *
     * キーバインドの登録（IKeybindProvider）とコールバックの割り当ての両方から使う。
     */
    public static List<IHotkey> allHotkeys() {
        return ImmutableList.<IHotkey>builder()
                .addAll(General.HOTKEYS)
                .addAll(Tweaks.HOTKEYS)
                .build();
    }

    /** 設定ファイルの絶対パスを返す。 */
    private static Path getConfigFilePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
    }

    /**
     * 設定ファイルを読み込んで、各 Config オブジェクトへ値を流し込む。
     *
     * ファイルが無い／壊れている場合は既定値のまま続行する。ここで例外を投げると
     * MOD の初期化ごと失敗してゲームが起動しなくなるため、必ず握りつぶす。
     */
    public static void loadFromFile() {
        Path configFile = getConfigFilePath();

        // 初回起動時はファイルが無い。既定値のままで良いので何もしない。
        if (!Files.isReadable(configFile)) {
            return;
        }

        JsonObject root;

        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);

            // 中身が JSON オブジェクトでなければ読み進めても意味が無い。
            if (element == null || !element.isJsonObject()) {
                NexusTweaks.LOGGER.warn("設定ファイルの形式が不正です: {}", configFile);
                return;
            }

            root = element.getAsJsonObject();
        } catch (Exception e) {
            // JsonSyntaxException は非検査例外なので IOException だけを捕まえるのでは足りない。
            NexusTweaks.LOGGER.error("設定ファイルの読み込みに失敗しました: {}", configFile, e);
            return;
        }

        // カテゴリ名（＝JSON のキー）ごとに、対応する設定リストへ値を流し込む。
        ConfigUtils.readConfigBase(root, "General", General.OPTIONS);
        ConfigUtils.readConfigBase(root, "Tweaks", Tweaks.OPTIONS);
    }

    /**
     * 現在の設定値を JSON へ書き出す。
     *
     * 書き込み中にゲームが落ちても設定ファイルが壊れないよう、
     * 一時ファイルへ書いてから原子的に置き換える。
     */
    public static void saveToFile() {
        Path configFile = getConfigFilePath();

        // config ディレクトリがまだ無いことがある（初回起動など）ので先に作る。
        try {
            Files.createDirectories(configFile.getParent());
        } catch (IOException e) {
            NexusTweaks.LOGGER.error("設定ディレクトリの作成に失敗しました: {}", configFile.getParent(), e);
            return;
        }

        // カテゴリごとに JSON オブジェクトへ書き出す。読み込み側と同じキー名を使うこと。
        JsonObject root = new JsonObject();
        ConfigUtils.writeConfigBase(root, "General", General.OPTIONS);
        ConfigUtils.writeConfigBase(root, "Tweaks", Tweaks.OPTIONS);

        // まず一時ファイルへ書き出す。
        Path tempFile = configFile.resolveSibling(CONFIG_FILE_NAME + ".tmp");

        try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        } catch (Exception e) {
            NexusTweaks.LOGGER.error("設定ファイルの書き出しに失敗しました: {}", tempFile, e);
            return;
        }

        // 書き終わってから本命のファイルへ差し替える。
        try {
            Files.move(tempFile, configFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // 一部のファイルシステムは原子的な move に対応していないので、その場合は通常の move で妥協する。
            try {
                Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e2) {
                NexusTweaks.LOGGER.error("設定ファイルの置き換えに失敗しました: {}", configFile, e2);
            }
        } catch (IOException e) {
            NexusTweaks.LOGGER.error("設定ファイルの置き換えに失敗しました: {}", configFile, e);
        }
    }

    /** malilib から呼ばれる読み込み口。 */
    @Override
    public void load() {
        loadFromFile();
    }

    /** malilib から呼ばれる保存口。 */
    @Override
    public void save() {
        saveToFile();
    }
}
