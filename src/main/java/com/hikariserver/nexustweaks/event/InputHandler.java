package com.hikariserver.nexustweaks.event;

import com.hikariserver.nexustweaks.Reference;
import com.hikariserver.nexustweaks.config.Configs;

import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

/**
 * malilib へ「この MOD が持っているホットキー」を伝える窓口。
 *
 * malilib は自前でキー入力を監視していて、押されたキーの組み合わせに一致する
 * キーバインドを探してコールバックを呼ぶ。そのためには
 *   1. どのキーを監視すべきか（addKeysToMap）
 *   2. 設定画面のホットキー一覧にどう並べるか（addHotkeys）
 * の 2 つを教えてやる必要がある。
 */
public final class InputHandler implements IKeybindProvider {

    /** malilib へ登録するのは 1 個で十分なのでシングルトンにする。 */
    private static final InputHandler INSTANCE = new InputHandler();

    /** 外から new させない。 */
    private InputHandler() {
    }

    /** シングルトンを取得する。 */
    public static InputHandler getInstance() {
        return INSTANCE;
    }

    /**
     * 監視対象のキーバインドを malilib のマップへ登録する。
     *
     * ここへ入れ忘れたキーバインドは、設定画面で割り当てられても反応しない。
     */
    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : Configs.allHotkeys()) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    /**
     * malilib 共通のホットキー一覧画面に載せるカテゴリを登録する。
     *
     * 第 1 引数は MOD 名、第 2 引数はカテゴリ見出しの翻訳キー。
     */
    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(
                Reference.MOD_NAME,
                Reference.MOD_ID + ".hotkeys.category",
                Configs.allHotkeys());
    }
}
