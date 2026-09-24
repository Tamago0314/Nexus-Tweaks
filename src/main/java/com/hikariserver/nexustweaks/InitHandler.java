package com.hikariserver.nexustweaks;

import com.hikariserver.nexustweaks.config.Configs;
import com.hikariserver.nexustweaks.config.KeyCallbacks;
import com.hikariserver.nexustweaks.event.InputHandler;
import com.hikariserver.nexustweaks.gui.GuiConfigs;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import fi.dy.masa.malilib.registry.Registry;
import fi.dy.masa.malilib.util.data.ModInfo;

/**
 * この MOD の登録処理をすべてまとめた場所。
 *
 * malilib の InitializationHandler から呼ばれるので、
 * 「malilib 本体の初期化が終わっている」ことが保証された状態で実行される。
 * ModInitializer の中で直接 malilib を触ると読み込み順によっては落ちるため、
 * malilib に関わる登録は必ずここへ書くこと。
 */
public class InitHandler implements IInitializationHandler {

    @Override
    public void registerModHandlers() {
        // ── 設定 ────────────────────────────────────────────────────
        // malilib へ設定ハンドラを登録する。
        // 以後、malilib が適切なタイミングで load() / save() を呼んでくれる。
        ConfigManager.getInstance().registerConfigHandler(Reference.MOD_ID, new Configs());

        // 起動直後に 1 度読み込んでおく。
        // これをしないと、最初にワールドへ入るまで設定ファイルの内容が反映されない。
        Configs.loadFromFile();

        // 読み込んだ内容をそのまま書き戻す。
        // 初回起動時に既定値のファイルが生成されるので、
        // ユーザーが「設定ファイルはどこ？」と探さずに済む。
        Configs.saveToFile();

        // ── 設定画面 ────────────────────────────────────────────────
        // malilib 共通の設定画面（複数 MOD を切り替えられるドロップダウン）へ
        // この MOD の画面を登録する。
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(
                new ModInfo(Reference.MOD_ID, Reference.MOD_NAME, GuiConfigs::new));

        // ── ホットキー ──────────────────────────────────────────────
        // 先にコールバック（押されたときの動作）を割り当ててから、
        // キーバインドプロバイダを登録する。
        KeyCallbacks.init();

        InputEventHandler.getKeybindManager().registerKeybindProvider(InputHandler.getInstance());

        // 登録したキーバインドを malilib の監視対象へ反映させる。
        InputEventHandler.getKeybindManager().updateUsedKeys();

        // 注意: バニラのキー割り当て登録とパケット型の登録はここではやらない。
        // このハンドラは Minecraft のコンストラクタが終わる直前に呼ばれるので、
        // どちらもタイミングとして遅すぎる（詳細は NexusTweaksClient を参照）。

        NexusTweaks.LOGGER.info("{} の初期化が完了しました。", Reference.MOD_NAME);
    }
}
