package com.hikariserver.nexustweaks.feature;

import java.util.List;
import java.util.UUID;

import com.hikariserver.nexustweaks.config.Configs;
import com.hikariserver.nexustweaks.network.NexusNetworking;

import fi.dy.masa.malilib.gui.Message;
import fi.dy.masa.malilib.util.InfoUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.phys.HitResult;

/**
 * Auto Repair 機能のクライアント側の入口。
 *
 * 「スニークしながら、何にも視点を合わせていない状態で使用ボタン（既定は右クリック）を押したら
 * 経験値でメインハンドの道具を修理する」という判定と、実行経路の振り分けを行う。
 *
 * 実際の修理計算そのものは RepairLogic が持っていて、こちらはやらない。
 */
public final class AutoRepair {

    /**
     * 連続実行を防ぐためのクールダウン（tick 数）。
     *
     * バニラは使用ボタンを押しっぱなしにすると 4 tick ごとにアイテム使用処理を呼ぶ。
     * こちらはその処理をキャンセルして横取りするため、バニラのクールダウン変数が
     * セットされない。そのため自前で同じ間隔を持たせて連射を防ぐ。
     */
    private static final int COOLDOWN_TICKS = 4;

    /**
     * 最後に修理を実行したときのプレイヤーの tick カウンタ。
     *
     * 初回は必ず通したいので、tick 0 の時点でクールダウンが明けている値で初期化しておく。
     * Integer.MIN_VALUE にすると now - lastRepairTick が int の範囲を超えて負になり、
     * 判定が常に「クールダウン中」になってしまうので使わないこと。
     */
    private static int lastRepairTick = -COOLDOWN_TICKS;

    /** ユーティリティクラスなのでインスタンス化を禁止する。 */
    private AutoRepair() {
    }

    /**
     * 使用ボタンが押されたときに呼ばれる。
     *
     * @param mc クライアント本体
     * @return true を返した場合、呼び出し元（mixin）はバニラのアイテム使用処理をキャンセルする
     */
    public static boolean onStartUseItem(Minecraft mc) {
        // ── 1. 機能が有効か ──────────────────────────────────────────
        if (!Configs.Tweaks.AUTO_REPAIR.getBooleanValue()) {
            return false;
        }

        // ── 2. ワールドに入っているか ────────────────────────────────
        if (mc.player == null || mc.level == null) {
            return false;
        }

        // ── 3. スニークしているか ────────────────────────────────────
        // 使用ボタンだけでは、空に向けて右クリックしたときに毎回発動してしまう。
        // スニークを足すことで、修理したいときにだけ意図して出せるようにする。
        if (!mc.player.isShiftKeyDown()) {
            return false;
        }

        // ── 4. クールダウン中でないか ────────────────────────────────
        // tickCount はワールドを跨ぐとリセットされるので、
        // 「進んでいない」だけでなく「巻き戻った」場合も通すようにする。
        int now = mc.player.tickCount;

        if (now >= lastRepairTick && now - lastRepairTick < COOLDOWN_TICKS) {
            return false;
        }

        // ── 5. 何にも視点を合わせていないか ──────────────────────────
        // hitResult が MISS のときだけ発動する。
        // ブロックやエンティティを見ているときは、バニラの動作を邪魔しない。
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
            return false;
        }

        // ── 6. 対象アイテムが修理できるものか ────────────────────────
        ItemStack stack = mc.player.getInventory().getSelectedItem();

        if (stack.isEmpty()) {
            return false;
        }

        // 耐久値を持たない、または既に満タンなら何もしない。
        // ここで弾いておけば、素手や建材ブロックを持っているときに誤爆しない。
        if (!stack.isDamageableItem() || !stack.isDamaged()) {
            return false;
        }

        // ── 7. 右クリックで「使える」アイテムを除外する ──────────────
        // 弓・盾・トライデント・食べ物などは空中を右クリックすると実際に動作する。
        // これらは使用アニメーションが NONE 以外なので、それで判別して除外する。
        if (stack.getUseAnimation() != ItemUseAnimation.NONE) {
            return false;
        }

        // 防具・エリトラ・カボチャなどは使用アニメーションが NONE だが、
        // 空中を右クリックすると「その場で装備（手持ちと装備欄の入れ替え）」が発動する。
        // この入れ替えができるものは Equippable コンポーネントの swappable が true なので、それで除外する。
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);

        if (equippable != null && equippable.swappable()) {
            return false;
        }

        // 使用アニメーションが NONE のまま空撃ちで動くアイテム（釣り竿など）は
        // コードから判別できないので、設定の除外リストで指定してもらう。
        if (isExcluded(stack)) {
            return false;
        }

        // ── 8. ここまで来たら実行する ────────────────────────────────
        lastRepairTick = now;
        dispatchRepair(mc);

        // バニラのアイテム使用処理は行わせない。
        return true;
    }

    /**
     * 対象アイテムが除外リストに入っているかを判定する。
     */
    private static boolean isExcluded(ItemStack stack) {
        List<String> exclusions = Configs.Tweaks.AUTO_REPAIR_EXCLUSIONS.getStrings();

        // 除外リストが空なら照合するまでもない。
        if (exclusions.isEmpty()) {
            return false;
        }

        // レジストリからアイテムの ID（例: minecraft:fishing_rod）を引く。
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());

        // ID が引けない自作アイテムなどは除外対象にしようが無いので通す。
        if (itemId == null) {
            return false;
        }

        return exclusions.contains(itemId.toString());
    }

    /**
     * 実行経路を振り分ける。
     *
     * 1. サーバーが Nexus-Sync 対応 → サーバーへ要求を投げる（結果は後からパケットで返る）
     * 2. シングルプレイ            → 統合サーバーのプレイヤーへ直接適用する
     * 3. それ以外（バニラサーバー） → 何もせず「非対応」と伝える
     */
    private static void dispatchRepair(Minecraft mc) {
        // 経路 1: サーバー側 MOD がいるならすべて任せる。
        // クライアントに Nexus-Sync も入っている場合は通信を使わないので、ここへは来ない
        // （シングルプレイなら経路 2 になる。理由は NexusNetworking.register() を参照）。
        if (NexusNetworking.isServerSupported()) {
            NexusNetworking.requestRepair();
            return;
        }

        // 経路 2: Nexus-Sync が無くても、シングルプレイなら統合サーバーが同じ JVM で動いている。
        // そちらのプレイヤーを直接書き換えれば、ちゃんと保存される本物の修理になる。
        if (mc.hasSingleplayerServer()) {
            repairOnIntegratedServer(mc);
            return;
        }

        // 経路 3: マルチプレイで相手が非対応。
        // クライアントだけ書き換えても次の同期で戻るうえ、直ったと誤解させるので何もしない。
        InfoUtils.showInGameMessage(Message.MessageType.WARNING,
                "nexus-tweaks.message.server_unsupported");
    }

    /**
     * シングルプレイ（統合サーバー）でのみ使う直接実行の経路。
     */
    private static void repairOnIntegratedServer(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();

        if (server == null || mc.player == null) {
            return;
        }

        // クライアント側のプレイヤーとサーバー側のプレイヤーを結び付ける鍵。
        UUID uuid = mc.player.getUUID();

        // 耐久値と経験値はサーバー権限のデータなので、必ずサーバースレッドで触る。
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(uuid);

            // ワールドを抜けた直後などは見つからないことがある。
            if (serverPlayer == null) {
                return;
            }

            RepairResult result = RepairLogic.repairMainHand(serverPlayer);

            // 表示はクライアントスレッドの仕事なので、結果を持って戻す。
            mc.execute(() -> NexusNetworking.showResultMessage(
                    result.status(), result.repaired(), result.xpSpent()));
        });
    }
}
