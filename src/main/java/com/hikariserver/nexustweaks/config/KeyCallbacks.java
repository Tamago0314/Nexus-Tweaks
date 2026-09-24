package com.hikariserver.nexustweaks.config;

import com.hikariserver.nexustweaks.gui.GuiConfigs;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeyCallbackToggleBooleanConfigWithMessage;
import net.minecraft.client.Minecraft;

/**
 * 各ホットキーが押されたときに何をするかを割り当てる。
 *
 * malilib のキーバインドは「キーの組み合わせ」しか持っていないので、
 * 実際の動作はここで setCallback して結び付ける。
 */
public final class KeyCallbacks {

    /** ユーティリティクラスなのでインスタンス化を禁止する。 */
    private KeyCallbacks() {
    }

    /**
     * すべてのホットキーへコールバックを割り当てる。
     *
     * MOD の初期化時に 1 度だけ呼ぶこと。
     */
    public static void init() {
        // ── 設定画面を開くホットキー ────────────────────────────────
        Configs.General.OPEN_CONFIG.getKeybind().setCallback(new OpenConfigCallback());

        // ── Auto Repair の ON/OFF トグル ────────────────────────────
        // malilib に「真偽値を反転してチャットへ結果を出す」既製のコールバックがあるので、
        // わざわざ自前で書かずにそれを使う。
        Configs.Tweaks.AUTO_REPAIR.getKeybind().setCallback(
                new KeyCallbackToggleBooleanConfigWithMessage(Configs.Tweaks.AUTO_REPAIR));
    }

    /**
     * 設定画面を開くコールバック。
     */
    private static class OpenConfigCallback implements IHotkeyCallback {

        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            Minecraft mc = Minecraft.getInstance();

            // 起動直後など、まだクライアントが用意できていないケースを弾く。
            if (mc == null) {
                return false;
            }

            // 画面の切り替えは必ずメインスレッドで行う必要がある。
            // malilib の入力処理はメインスレッドから来るが、
            // execute() で 1 tick ずらしておくと入力処理の途中で画面が入れ替わる事故を防げる。
            mc.execute(() -> GuiBase.openGui(new GuiConfigs()));

            // true を返すと「このキー入力は処理済み」として malilib が伝播を止める。
            return true;
        }
    }
}
