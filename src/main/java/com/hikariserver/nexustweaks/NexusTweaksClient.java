package com.hikariserver.nexustweaks;

import com.hikariserver.nexustweaks.feature.MassCraftClient;
import com.hikariserver.nexustweaks.keybind.VanillaKeyMappings;
import com.hikariserver.nexustweaks.network.NexusNetworking;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * クライアント用のエントリポイント。
 *
 * malilib に依存しない登録処理のうち、
 * 「ゲームの初期化より前に済ませておかないといけないもの」をここで行う。
 *
 * malilib の InitializationHandler（InitHandler）は
 * Minecraft のコンストラクタが終わる直前に呼ばれるため、下記のような
 * 「もっと早い段階でしか受け付けてくれない登録」には間に合わない。
 *   - バニラのキー割り当て: GameOptions が作られる前でないと
 *     「GameOptions has already been initialised」で落ちる
 *   - パケット型の登録    : サーバーへ接続する前に済ませておく必要がある
 */
public class NexusTweaksClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // ── バニラの操作設定への登録 ────────────────────────────────
        // 「操作設定 → キー割り当て」の一覧に項目を出す。既定は未割り当て。
        VanillaKeyMappings.register();

        // ── 通信 ────────────────────────────────────────────────────
        // サーバー側 MOD（Nexus-Sync）とやり取りするパケット型と受信ハンドラを用意する。
        NexusNetworking.register();

        // ── massCraft の代行 ────────────────────────────────────────
        // 応答待ちのタイムアウトなどを数えるため、毎 tick 知らせる。
        ClientTickEvents.END_CLIENT_TICK.register(client -> MassCraftClient.onClientTick());
    }
}
