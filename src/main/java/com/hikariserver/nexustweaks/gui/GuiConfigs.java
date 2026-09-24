package com.hikariserver.nexustweaks.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.hikariserver.nexustweaks.Reference;
import com.hikariserver.nexustweaks.config.Configs;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.config.options.BooleanHotkeyGuiWrapper;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * Nexus-Tweaks 専用の設定画面。
 *
 * malilib の GuiConfigsBase を継承すると、設定リストの描画・スクロール・
 * キーバインド入力・検索ボックスなどをすべて malilib 側が面倒を見てくれる。
 * こちらがやることは「タブのボタンを並べる」ことと
 * 「今のタブで表示すべき設定のリストを返す」ことだけ。
 */
public class GuiConfigs extends GuiConfigsBase {

    /**
     * 現在選択中のタブ。
     *
     * static にしているのは、画面を閉じて開き直したときに
     * 直前に見ていたタブへ戻したいため（malilib 本家の GUI も同じ挙動）。
     */
    private static ConfigGuiTab currentTab = ConfigGuiTab.GENERAL;

    /**
     * 引数なしのコンストラクタ。
     *
     * malilib の設定切り替えメニュー（Registry.CONFIG_SCREEN）は
     * Supplier<GuiBase> を要求するので、こちらが必要になる。
     */
    public GuiConfigs() {
        // 第 1・2 引数は設定リストの描画開始位置（x, y）。
        // 第 3 引数の modId は malilib が「どの MOD の画面か」を判別するのに使う。
        // 第 4 引数は戻り先の画面。null なら閉じたときにゲームへ戻る。
        // 第 5 引数以降は画面タイトルの翻訳キーとその引数。
        super(10, 50, Reference.MOD_ID, null,
                Reference.MOD_ID + ".gui.title.configs", Reference.MOD_VERSION);
    }

    /**
     * 画面を組み立てる。
     *
     * 画面サイズが変わったときやタブを切り替えたときにも呼び直される。
     */
    @Override
    public void initGui() {
        // まず malilib 側の初期化（設定リストウィジェットの生成など）を行う。
        super.initGui();

        // 前回 initGui したときのボタンが残らないよう、設定リストを一度空にする。
        this.clearOptions();

        // タブボタンを横一列に並べる。x を戻り値の分だけ進めていくのが malilib の定石。
        int x = 10;
        int y = 26;

        for (ConfigGuiTab tab : ConfigGuiTab.values()) {
            x += this.createTabButton(x, y, tab);
        }
    }

    /**
     * タブ 1 つ分のボタンを作って登録し、次のボタンを置くための「幅 + 余白」を返す。
     */
    private int createTabButton(int x, int y, ConfigGuiTab tab) {
        // 幅に -1 を渡すと malilib が文字幅から自動でボタン幅を決めてくれる。
        ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, tab.getDisplayName());

        // 今表示しているタブのボタンは押せないようにして、選択状態が分かるようにする。
        button.setEnabled(currentTab != tab);

        this.addButton(button, new TabButtonListener(tab, this));

        // 2px の余白を足して返す。
        return button.getWidth() + 2;
    }

    /**
     * 設定リストの横幅。
     *
     * タブによって値の編集 UI の広さが違うので、見やすい幅を個別に指定する。
     */
    @Override
    protected int getConfigWidth() {
        return switch (currentTab) {
            // General はホットキーだけなので、キー表示に十分な幅を確保する。
            case GENERAL -> 240;
            // Tweaks は ON/OFF + ホットキー + 文字列リストが並ぶので少し広めにする。
            case TWEAKS -> 260;
        };
    }

    /**
     * 検索ボックスを「キーバインド検索」モードにするかどうか。
     *
     * どちらのタブにもホットキーが含まれるので、常に true にしておく。
     * これで「このキーに何が割り当たっているか」を検索できるようになる。
     */
    @Override
    protected boolean useKeybindSearch() {
        return true;
    }

    /**
     * 現在のタブで表示する設定の一覧を返す。
     *
     * malilib はここで返した ConfigOptionWrapper のリストをそのまま行として描画する。
     */
    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return switch (currentTab) {
            // General タブ: 定義した設定をそのまま出す。
            case GENERAL -> ConfigOptionWrapper.createFor(Configs.General.OPTIONS);

            // Tweaks タブ: ON/OFF とホットキーを 1 行にまとめたいものだけラップして出す。
            case TWEAKS -> {
                List<fi.dy.masa.malilib.config.IConfigBase> configs = new ArrayList<>();

                // ConfigBooleanHotkeyed をそのまま渡すと ON/OFF 行とキー行が別々になる。
                // BooleanHotkeyGuiWrapper で包むと 1 行にまとまって見やすい。
                configs.add(new BooleanHotkeyGuiWrapper(
                        Configs.Tweaks.AUTO_REPAIR.getPrettyName(),
                        Configs.Tweaks.AUTO_REPAIR,
                        Configs.Tweaks.AUTO_REPAIR.getKeybind()));

                // 除外リストは単独の設定としてそのまま並べる。
                configs.add(Configs.Tweaks.AUTO_REPAIR_EXCLUSIONS);

                // massCraft の代行はホットキーを持たないので、そのまま並べる。
                configs.add(Configs.Tweaks.MASS_CRAFT_ON_SERVER);

                yield ConfigOptionWrapper.createFor(configs);
            }
        };
    }

    /**
     * 設定値が変更されたときに呼ばれる。
     *
     * 変更のたびにファイルへ保存しておくと、ゲームがクラッシュしても設定が飛ばない。
     */
    @Override
    protected void onSettingsChanged() {
        super.onSettingsChanged();
        Configs.saveToFile();
    }

    /**
     * 画面が閉じられたときに呼ばれる。
     *
     * onSettingsChanged が呼ばれない種類の変更（文字列リストの編集など）に備えて、
     * 閉じるタイミングでもう一度保存しておく。
     */
    @Override
    public void removed() {
        super.removed();
        Configs.saveToFile();

        // malilib へ「設定が変わったかもしれない」と伝える。
        // 他 MOD の連携処理や malilib 側のキャッシュ更新がここで走る。
        ConfigManager.getInstance().onConfigsChanged(Reference.MOD_ID);
    }

    /**
     * タブボタンが押されたときの処理。
     */
    private record TabButtonListener(ConfigGuiTab tab, GuiConfigs parent) implements IButtonActionListener {

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            // 表示するタブを切り替える。
            currentTab = this.tab;

            // getConfigWidth() の戻り値がタブごとに違うので、
            // リストウィジェットを作り直さないと幅が反映されない。
            this.parent.reCreateListWidget();

            // 前のタブのスクロール位置が残っていると違和感があるので先頭へ戻す。
            Objects.requireNonNull(this.parent.getListWidget()).resetScrollbarPosition();

            // ボタンの有効／無効を作り直すために画面を組み立て直す。
            this.parent.initGui();
        }
    }

    /**
     * 設定画面のタブ定義。
     *
     * ここへ値を足すだけで新しいタブが増える（getConfigs / getConfigWidth の switch も要更新）。
     */
    public enum ConfigGuiTab {

        /** MOD 全体に関わる操作をまとめたタブ。 */
        GENERAL(Reference.MOD_ID + ".gui.button.config.general"),

        /** 個々の便利機能をまとめたタブ。 */
        TWEAKS(Reference.MOD_ID + ".gui.button.config.tweaks");

        /** ボタンに表示する文字列の翻訳キー。 */
        private final String translationKey;

        ConfigGuiTab(String translationKey) {
            this.translationKey = translationKey;
        }

        /** 現在の言語設定に応じたボタン表示名を返す。 */
        public String getDisplayName() {
            return StringUtils.translate(this.translationKey);
        }
    }
}
