package com.hikariserver.nexustweaks.feature;

/**
 * 修理処理の結果をひとまとめにした戻り値。
 *
 * @param status    結果の種類
 * @param repaired  実際に回復した耐久値
 * @param xpSpent   実際に消費した経験値ポイント数
 */
public record RepairResult(RepairStatus status, int repaired, int xpSpent) {

    /**
     * 失敗（何も消費していない）を表す結果を作る。
     *
     * 「経験値が足りない」「直すところが無い」などの分岐でいちいち 0 を書かなくて済むようにする。
     */
    public static RepairResult failure(RepairStatus status) {
        return new RepairResult(status, 0, 0);
    }
}
