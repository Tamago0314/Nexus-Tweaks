package com.hikariserver.nexustweaks.feature;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * 「経験値を消費してメインハンドの道具を修理する」処理そのもの。
 *
 * 耐久値も経験値もサーバー権限のデータなので、この処理は必ず
 * サーバー側のプレイヤー（ServerPlayer）に対して、サーバースレッド上で実行すること。
 *
 * シングルプレイでも内部的にはサーバーが動いているので、
 * クライアントから統合サーバーの ServerPlayer を取り出してここへ渡せば
 * ちゃんと保存される本物の修理になる。
 */
public final class RepairLogic {

    /** ユーティリティクラスなのでインスタンス化を禁止する。 */
    private RepairLogic() {
    }

    /**
     * メインハンドの道具を、持っている経験値の範囲で可能な限り修理する。
     *
     * 呼び出し側の前提: サーバースレッド上で呼ぶこと。
     *
     * @param player 対象のサーバー側プレイヤー
     * @return 何がどれだけ起きたかを表す結果
     */
    public static RepairResult repairMainHand(ServerPlayer player) {
        // ── 1. 対象アイテムを取り出す ────────────────────────────────
        // 「現在選択中のホットバースロット」にあるアイテムを対象にする。
        ItemStack stack = player.getInventory().getSelectedItem();

        if (stack.isEmpty()) {
            return RepairResult.failure(RepairStatus.EMPTY_HAND);
        }

        // ── 2. そもそも修理できるアイテムかを確認する ────────────────
        // 耐久値を持たない（＝壊れない）アイテムは対象外。
        if (!stack.isDamageableItem()) {
            return RepairResult.failure(RepairStatus.NOT_DAMAGEABLE);
        }

        // 既に満タンなら直すところが無い。
        if (!stack.isDamaged()) {
            return RepairResult.failure(RepairStatus.NOT_DAMAGED);
        }

        // ── 3. 使える経験値と、必要な経験値を突き合わせる ────────────
        // 現在の傷み具合（数値が大きいほどボロボロ）。
        int damage = stack.getDamageValue();

        // プレイヤーが今持っている経験値ポイントの合計。
        int availableXp = XpMath.getPlayerTotalXp(player);

        if (availableXp <= 0) {
            return RepairResult.failure(RepairStatus.NO_XP);
        }

        // 完全に直すのに必要なポイント数。
        int neededXp = XpMath.getXpCostForRepair(damage);

        // 実際に払えるのは「必要ぶん」と「持っているぶん」の小さい方。
        int xpToSpend = Math.min(neededXp, availableXp);

        // 払ったポイントで回復できる耐久値。damage を超えて回復させない。
        int repaired = Math.min(damage, xpToSpend * XpMath.XP_TO_DURABILITY_RATIO);

        // 端数の都合で 1 も回復できないなら、経験値を無駄にしないよう何もしない。
        if (repaired <= 0) {
            return RepairResult.failure(RepairStatus.NO_XP);
        }

        // ── 4. 実際に適用する ────────────────────────────────────────
        // 耐久値を回復させる（damage を減らす方向が「直る」方向）。
        stack.setDamageValue(damage - repaired);

        // 経験値を減らす。giveExperiencePoints は負の値でそのまま減算できる。
        player.giveExperiencePoints(-xpToSpend);

        // ── 5. クライアントへ変更を伝える ────────────────────────────
        // インベントリの中身は「開いている画面（containerMenu）」経由で同期されるので、
        // 変更を明示的にブロードキャストしないとクライアント側の表示が古いままになる。
        player.containerMenu.broadcastChanges();

        // 経験値バーは ServerPlayer が毎 tick 差分を見て自動で同期するため、
        // ここで明示的に送る必要は無い。

        return new RepairResult(RepairStatus.OK, repaired, xpToSpend);
    }
}
