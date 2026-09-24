package com.hikariserver.nexustweaks.keybind;

import com.hikariserver.nexustweaks.Reference;
import com.hikariserver.nexustweaks.gui.GuiConfigs;

import com.mojang.blaze3d.platform.InputConstants;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.util.GuiUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/**
 * バニラの「操作設定 → キー割り当て」画面にこの MOD の項目を出すための登録処理。
 *
 * malilib のホットキー（N + T）とは完全に別系統の仕組みで、
 * 「malilib を知らないユーザーでもバニラの設定画面から探せる」ようにするのが目的。
 *
 * バニラのキー割り当ては N+T のような同時押しに対応していないため、
 * 既定値は「未割り当て」にしてある。こうしておけば malilib 側の N,T と
 * 二重に発火することもなく、欲しい人だけが好きな 1 キーを割り当てられる。
 */
public final class VanillaKeyMappings {

    /**
     * 操作設定画面でのカテゴリ見出し。
     *
     * 26.x でキーのカテゴリが単なる文字列から KeyMapping.Category という型になり、
     * Identifier で登録する方式へ変わった。
     * 見出しの翻訳キーは Identifier から自動生成され、
     * "nexus-tweaks:main" なら "key.category.nexus-tweaks.main" になる。
     */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Reference.MOD_ID, "main"));

    /**
     * 「Open Nexus-Tweaks Config」のキー割り当て。
     *
     * 初期化順の都合で register() の中から代入するため、final にはしていない。
     */
    private static KeyMapping openConfigKey;

    /** ユーティリティクラスなのでインスタンス化を禁止する。 */
    private VanillaKeyMappings() {
    }

    /**
     * キー割り当てを登録し、毎 tick その入力を拾う処理を仕掛ける。
     *
     * MOD の初期化時に 1 度だけ呼ぶこと。
     */
    public static void register() {
        // ── キー割り当て本体の登録 ──────────────────────────────────
        // 第 1 引数: 操作設定画面に出る項目名の翻訳キー
        // 第 2 引数: 入力の種類（KEYSYM = 通常のキーボードキー）
        // 第 3 引数: 既定のキーコード。InputConstants.UNKNOWN (-1) は「未割り当て」
        // 第 4 引数: 上で登録したカテゴリ
        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key." + Reference.MOD_ID + ".open_config",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),
                CATEGORY));

        // ── 押されたかどうかを毎 tick 確認する ──────────────────────
        // バニラのキー割り当ては「押された回数」を内部に溜め込む方式なので、
        // consumeClick() が false を返すまで回して溜まった分を消化する。
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                // 何らかの画面が開いている間の入力は、その画面への入力として扱うべきなので拾わない。
                // ただし consumeClick() 自体は呼び切って、溜まった押下を捨てておく。
                if (GuiUtils.getCurrentScreen() != null) {
                    continue;
                }

                // ここは既にクライアントのメインスレッドなので、そのまま画面を開いてよい。
                GuiBase.openGui(new GuiConfigs());
            }
        });
    }
}
