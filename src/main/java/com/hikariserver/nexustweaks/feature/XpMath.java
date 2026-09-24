package com.hikariserver.nexustweaks.feature;

import net.minecraft.world.entity.player.Player;

/**
 * 経験値レベルと経験値ポイントの相互変換。
 *
 * Minecraft のプレイヤーは「レベル」と「次のレベルまでの進捗（0.0〜1.0）」しか
 * 素直に持っていないため、「今このプレイヤーは合計何ポイント持っているのか」を
 * 知りたいときは自分で積算する必要がある。
 *
 * Player.totalExperience というフィールドもあるが、コマンドやプラグインで
 * レベルを直接いじられると実際の値とずれることがあるので、こちらは使わない。
 */
public final class XpMath {

    /** ユーティリティクラスなのでインスタンス化を禁止する。 */
    private XpMath() {
    }

    /**
     * 「レベル 0 からレベル level に到達するまでに必要な経験値ポイントの合計」を返す。
     *
     * バニラの経験値カーブは 3 区間の二次式でできている（Minecraft Wiki の式と同じ）。
     * 区間の境目（16 と 31）ではどちらの式でも同じ値になるので、連続している。
     */
    public static int getTotalXpForLevel(int level) {
        // 負のレベルは存在しないので 0 として扱う。
        if (level <= 0) {
            return 0;
        }

        // レベル 0〜16 の区間: L^2 + 6L
        if (level <= 16) {
            return level * level + 6 * level;
        }

        // レベル 17〜31 の区間: 2.5L^2 - 40.5L + 360
        if (level <= 31) {
            return (int) (2.5D * level * level - 40.5D * level + 360.0D);
        }

        // レベル 32 以上の区間: 4.5L^2 - 162.5L + 2220
        return (int) (4.5D * level * level - 162.5D * level + 2220.0D);
    }

    /**
     * プレイヤーが今持っている経験値ポイントの合計を返す。
     *
     * 「現在レベルまでの累積」＋「次のレベルまでの進捗ぶん」で計算する。
     */
    public static int getPlayerTotalXp(Player player) {
        // 現在のレベルに到達するまでに費やしたポイント。
        int base = getTotalXpForLevel(player.experienceLevel);

        // 現在のレベルから次のレベルまでに必要なポイント数。
        int needed = player.getXpNeededForNextLevel();

        // 進捗バーが示している端数ぶん。四捨五入しておく。
        int progress = Math.round(player.experienceProgress * needed);

        // 浮動小数の誤差で needed を超えることがあるので念のため丸めておく。
        progress = Math.max(0, Math.min(progress, needed));

        return base + progress;
    }

    /**
     * 「耐久を damage ぶん回復するのに必要な経験値ポイント数」を返す。
     *
     * バニラの修繕（Mending）は経験値 1 ポイントで耐久 2 回復なので、
     * 必要なポイントは耐久値の半分。ただし端数を切り捨てると
     * 「1 ポイントで 2 回復」の枠に収まらない 1 耐久ぶんが直せなくなるので、切り上げる。
     */
    public static int getXpCostForRepair(int damage) {
        if (damage <= 0) {
            return 0;
        }

        // 切り上げ除算。damage が奇数のときに +1 される。
        return (damage + XP_TO_DURABILITY_RATIO - 1) / XP_TO_DURABILITY_RATIO;
    }

    /**
     * 経験値 1 ポイントあたりに回復する耐久値。
     *
     * バニラの修繕エンチャントのデータ（data/minecraft/enchantment/mending.json の
     * minecraft:repair_with_xp -> multiply factor 2.0）と同じ比率にしてある。
     * この値は enchantment のデータ駆動なのでサーバー側から読むこともできるが、
     * 修繕が付いていない道具も直せる必要があるため、ここでは定数として持つ。
     */
    public static final int XP_TO_DURABILITY_RATIO = 2;
}
