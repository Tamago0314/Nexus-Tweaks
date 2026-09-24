# 検証環境と手順

Nexus-Tweaks（クライアント）と Nexus-Sync（サーバー）を手元で検証するための環境と手順。

## 環境の全体像

| 役割 | 場所 | 接続先 / ポート | 起動コマンド |
| --- | --- | --- | --- |
| Nexus-Sync 入りサーバー | `Nexus-Sync/run/` | `localhost:25565`（RCON 25575） | `Nexus-Sync> ./gradlew runServer` |
| 純バニラサーバー（Nexus-Sync 無し） | `Nexus-Sync/run-vanilla/` | `localhost:25566`（RCON 25576） | `Nexus-Sync> ./gradlew runVanillaServer` |
| クライアント（通常） | `Nexus-Tweaks/run/` | ― | `Nexus-Tweaks> ./gradlew runClient` |
| クライアント → Nexus-Sync サーバー | 同上 | 起動と同時に :25565 へ接続 | `Nexus-Tweaks> ./gradlew runClientSync` |
| クライアント → バニラサーバー | 同上 | 起動と同時に :25566 へ接続 | `Nexus-Tweaks> ./gradlew runClientVanilla` |
| クライアント + Nexus-Sync 同居（シングル） | 同上 | ― | `Nexus-Tweaks> ./gradlew runClientBoth` |

- クライアントのユーザー名は常に **`NexusTester`**。両サーバーの `ops.json` に最初から OP として登録してある。
- サーバーは `server-ip=127.0.0.1` なので自分の PC からしか接続できない（ファイアウォールの許可も不要）。
- どちらもオフラインモード・サバイバル・ピースフル・フラットワールド。
- サーバーの起動タスクは、足りないファイル（`eula.txt` / `server.properties` / `ops.json`）と
  テストキットを自動で用意してから起動する（`./gradlew setupTestServers` 単体でも実行できる）。
  既にある `server.properties` などは上書きしないので、手で変えた設定は残る。
- `runClientBoth` は隣の `Nexus-Sync/build/libs/` の jar を使うので、先に Nexus-Sync を `build` しておくこと。
- クライアントの起動タスクはどれも **Item Scroller 0.32.2** を一緒に読み込む（massCraft の代行の検証用）。
  `-PnoItemScroller` を付けると外して起動できる（例: `./gradlew runClientSync -PnoItemScroller`）。

## テストキット

`/function nexus_test:help` で一覧が出る。どのサーバーでも、シングルプレイ（チート有効）でも使える。

| コマンド | 内容 |
| --- | --- |
| `/function nexus_test:kit` | 道具一式を配り、経験値を 30 レベルにする |
| `/function nexus_test:status` | 手持ちの消耗度（damage）と経験値を表示 |
| `/function nexus_test:wear` | 手持ちの耐久を残り 10% にする |
| `/function nexus_test:xp30` | 経験値を 30 レベルにする |
| `/function nexus_test:xp5pt` | 経験値を 5 ポイントにする（部分回復の確認） |
| `/function nexus_test:xp0` | 経験値を 0 にする |
| `/function nexus_test:masscraft` | massCraft 用の素材を配る（下の表） |
| `/function nexus_test:quartz` | クオーツをシュルカー 4 箱分（6912 個）配る（大量クラフトの負荷テスト用） |
| `/function nexus_test:drops` | 周囲 16 ブロックに落ちているアイテムの合計個数を表示 |
| `/function nexus_test:cleardrops` | 周囲 16 ブロックに落ちているアイテムを消す |

`kit` で配られるもの:

| スロット | アイテム | 期待する動作（スニーク＋空に向けて右クリック） |
| --- | --- | --- |
| 1 | ダイヤのツルハシ（消耗 1400） | **修理される** |
| 2 | ネザライトの剣（消耗 1800） | **修理される** |
| 3 | ハサミ（消耗 200） | **修理される** |
| 4 | 火打石と打ち金（消耗 50） | **修理される** |
| 5 | ダイヤのツルハシ（満タン） | 何も起きない |
| 6 | 弓（消耗 300） | 修理されず、弓を引く |
| 7 | 盾（消耗 300） | 修理されず、構える |
| 8 | 釣り竿（消耗 50） | 修理されず、竿を投げる（除外リスト） |
| 9 | ダイヤのチェストプレート（消耗 400） | 修理されず、装備される |
| インベントリ | エリトラ / トライデント / 耐久無限のクワ / 鉄の斧 / 丸石 | エリトラ・トライデントは修理されない。クワは何も起きない。斧は修理される。丸石はブロックに向けて普通に置ける |

`masscraft` で配られるもの（手持ちは全部消えてから配られる）:

| 素材 | 作るもの | 期待する結果 |
| --- | --- | --- |
| 作業台 ×1 | ― | 置いて使う |
| 樫の板材 ×128 | 棒 | 棒が **256** 本、板材 0 |
| 丸石 ×512 | かまど | かまどが **64** 個、丸石 0 |
| ミルク入りバケツ ×9・砂糖 ×6・卵 ×3・小麦 ×9 | ケーキ | ケーキが **3** 個、空のバケツが **ちょうど 9** 個インベントリに戻る |
| ハチミツ入り瓶 ×16 | 砂糖 | 砂糖が **48** 個、ガラス瓶が **ちょうど 16** 個インベントリに戻る |

テストキットの中身は `Nexus-Sync/testkit/` にある。編集したら `Nexus-Sync> ./gradlew installTestKit` で各所へ配り直し、
サーバーでは `/reload` する。

## RCON（ゲームに入らずにサーバーへコマンドを送る）

```
cd Nexus-Sync
python scripts/rcon.py sync    "list"
python scripts/rcon.py sync    "data get entity NexusTester SelectedItem"
python scripts/rcon.py sync    "xp query NexusTester points"
python scripts/rcon.py vanilla "stop"
```

パスワードは各サーバーの `server.properties` から自動で読む。

## 検証手順

### A. Config 画面・キー設定
1. `N` を押しながら `T` → Config 画面が開き、**General / Tweaks** の 2 タブがある
2. General に「Open Nexus-Tweaks Config」、Tweaks に「Auto Repair」（ON/OFF とホットキーが 1 行）と除外リストがある
3. ESC → オプション → 操作設定 → キー割り当て → 「Nexus-Tweaks」カテゴリに
   「Open Nexus-Tweaks Config」が **未割り当て** で並んでいる。任意のキーを割り当てると画面が開く

### B. Nexus-Sync 入りサーバー（`runClientSync`）
1. `/function nexus_test:kit`
2. スロット 1（消耗 1400 のツルハシ）を持って、**スニークしながら空**に向けて右クリック
   → 「耐久を 1400 回復しました（経験値 700 消費）」と出る
   → `/function nexus_test:status` で damage=0、経験値は **Lv22 + 16pt**（30 レベル = 1395pt から 700pt 引いた値）
3. サーバー側のコンソールにも「NexusTester の道具を修理しました (耐久 +1400, 経験値 -700)」が出る
4. 一度切断して入り直しても、耐久と経験値が **元に戻っていない**（＝サーバーに保存されている）
5. スロット 6〜9 とエリトラ・トライデントで、それぞれ上の表どおりバニラの動作になる
6. ブロック・動物・額縁などに向けてスニーク＋右クリック → 修理されず、バニラの動作（設置など）になる
6b. **スニークせずに**空へ右クリック → 何も起きない（スニークが条件）
7. `/function nexus_test:xp5pt` → `/function nexus_test:wear` → スニーク＋右クリック
   → 耐久が **10 だけ** 回復し、経験値が 0 になる
8. `/function nexus_test:xp0` → スニーク＋右クリック → 「経験値が足りません」、何も消費されない
9. Config で Auto Repair を OFF → スニーク＋右クリックしても何も起きない。
   Auto Repair にホットキーを割り当てれば、押すたびに ON/OFF が切り替わってメッセージが出る
10. スニークしたまま右クリック押しっぱなし → 連打にならず、4 tick ごとの実行にとどまる
11. `Nexus-Sync/run/config/nexus-sync.json` の `enabled` を `false` にしてサーバー再起動
    → スニーク＋右クリックで「このサーバーでは Auto Repair が無効にされています」

### C. 純バニラサーバー（`runClientVanilla`）
1. `/function nexus_test:kit`
2. スロット 1 でスニークしながら空に向けて右クリック
   → 「このサーバーには Nexus-Sync が入っていないため、Auto Repair は使えません」と出て、
     耐久も経験値も変わらない（`/function nexus_test:status` で確認）

### D. シングルプレイ（`runClient`）
1. ワールド作成時に「チートの許可: オン」にする
2. `/function nexus_test:kit` → スロット 1 でスニークしながら空に向けて右クリック → B-2 と同じ結果
   （Nexus-Sync 無しでも、統合サーバーへ直接適用される経路）
3. ワールドを出て入り直しても直ったまま

### E. シングルプレイ + Nexus-Sync 同居（`runClientBoth`）
同居時、Nexus-Tweaks はサーバーとの通信を一切使わない（同じチャンネル ID に両者が別のクラスで型を登録するため、
通信すると `ClassCastException` / `IllegalStateException` になる。詳細は `NexusNetworking.register()`）。
1. 起動時に落ちない。
   ログに「Nexus-Sync が同じ環境に存在するため、サーバーとの通信は使いません。」が出る
2. ワールドに入っても「Nexus-Sync 対応サーバーを検出しました」は **出ない**
3. D-2 と同じ結果になる（Nexus-Sync を経由せず、統合サーバーへ直接適用する経路）
4. ログに `ClassCastException` / `IllegalStateException` が出ていない
5. massCraft は代行されず、Item Scroller の通常の処理で動く（F の手順で確認）

### F. massCraft の代行（`runServer` + `runClientSync`）

Item Scroller の操作（既定のキー）:
- **レシピの記憶**: 作業台のグリッドに手でレシピを並べ、`A`（recipeView）を押しながら出力スロットを**ホイールクリック**
- **massCraft**: `Ctrl + Alt + C` を押している間、記憶したレシピでクラフトして成果物を投げ捨てる
- **massCraftHold**: Item Scroller の設定（`I` + `C`）→ Generic、またはホットキー `massCraftToggle` を割り当てて切り替え

数え間違いは「増殖」か「消失」を意味するので、毎回 `masscraft` 関数から始めて正確に数えること。
インベントリ内の個数は `/clear @s <アイテム> 0`（消さずに数だけ表示）で数えられる。

1. 起動ログを確認する
   - クライアント: 「massCraft の代行（Item Scroller 連携）: 有効」
   - サーバー: 「Nexus-Sync の初期化が完了しました。(Auto Repair: 有効 / massCraft の代行: 有効)」
2. `/function nexus_test:masscraft` → 作業台を置いて開く
3. **基本（棒）**: 板材 2 枚を縦に並べて記憶 → グリッドを空にしてから `Ctrl+Alt+C` を長押し
   → `/function nexus_test:drops` で **256**、`/clear @s minecraft:oak_planks 0` で **0**、グリッドは空
   → `/function nexus_test:cleardrops`
4. **同じ素材を複数マス（かまど）**: 丸石 8 個で記憶 → 長押し → かまど **64**、丸石 **0**
5. **★ 残り物・スタック不可（ケーキ）**: ミルク入りバケツ 3・砂糖 2・卵 1・小麦 3 で記憶 → 長押し
   → ケーキ **3**、`/clear @s minecraft:bucket 0` で **ちょうど 9**（増殖の最重要テスト）。
   サーバーのログに「massCraft を中断しました」が出ていないこと
6. **★ 残り物・スタック可（ハチミツ → 砂糖）**: ハチミツ入り瓶 1 つで記憶 → 長押し
   → 砂糖 **48**、`/clear @s minecraft:glass_bottle 0` で **ちょうど 16**
7. **2x2**: 作業台を閉じ、サバイバルのインベントリ画面で板材 2 枚の棒を記憶し直す → 長押し → 3 と同じ結果
8. **応答待ちの間に Item Scroller が動かないこと**: 長押し中、グリッドがちらつかない
   （サーバーの代行と Item Scroller のクリックが同時に走ると、グリッドに素材が見え隠れする）
9. **中断**: 長押ししたまま画面を閉じる／作業台を壊す → きれいに止まり、グリッドにもカーソルにもアイテムが残っていない
10. **massCraftHold**: ON にする → キーを押さなくても続く。素材が尽きても固まらず、
    `/function nexus_test:masscraft` で補充すると再開する。OFF にすると止まる
11. **統計**: 統計画面の「棒 → クラフトされた数」が、3 の 1 回でちょうど **256** 増える
12. **大量のクラフト（続きの要求）**: `/function nexus_test:quartz` でクオーツをシュルカー 4 箱分（6912 個）受け取る。
    クオーツ 4 個 → クオーツブロック 1 個なので、全部使うとちょうどシュルカー 1 箱分（1728 個）になる。
    - シュルカーを 1 箱ずつ開けて、クオーツをインベントリへ移す（1 箱 = 1728 個 = 27 スタック）
    - 2x2（インベントリ画面）か作業台でレシピを記憶し、長押しする
    - 1 箱ぶんで **432 個**のクオーツブロックが落ちる。拾って空のシュルカーへしまい、次の箱へ進む
    - 4 箱で合計 **1728 個**ちょうど。`/clear @s minecraft:quartz_block 0` と `/function nexus_test:drops` で数える
    - 途中で止まらず続けて作られること、サーバーの tick（F3 の MSPT 表示やサーバーのラグ）に大きな山ができないことを見る
    - `massCraft.maxMillisPerRequest` を大きくすると速くなり、小さくすると遅くなる
13. **doLimitedCrafting**: `/recipe take @s minecraft:stick` → `/gamerule limited_crafting true` → 棒で長押し
    → 1 本も作られず、板材は減らない。終わったら `/gamerule limited_crafting false` と `/recipe give @s *` で戻す
14. **Nexus-Tweaks 側で OFF**: Nexus-Tweaks の設定（`N` + `T`）→ Tweaks →「massCraft をサーバーで実行」を OFF
    → 長押しすると Item Scroller の通常の処理（クリックの連打）になる。結果の個数は同じ
15. **サーバー側で無効**: `Nexus-Sync/run/config/nexus-sync.json` の `massCraft.enabled` を `false` にしてサーバー再起動
    → 長押しすると「このサーバーでは massCraft の代行が無効です。Item Scroller の通常の処理で行います」が **1 回だけ**出て、
    以後は Item Scroller の通常の処理になる。終わったら `true` に戻す
16. **権限**: `massCraft.requiredPermissionLevel` を `4` にしてサーバー再起動 → `/deop NexusTester`
    → 15 と同じ動作。終わったら `0` に戻して `/op NexusTester`

パケット数が本当に減ったかは、`ClientPacketListener.send` にでも一時的なカウンタを入れて、
massCraft を 1 秒押した分を数えると分かる（代行中は `ServerboundContainerClickPacket` が **0**、
`nexus-sync:mass_craft_request` が応答 1 通につき 1 通）。
この機能は回線が遅い人のためのものなので、`clumsy` などで 300〜500ms の遅延をかけて、
13（Item Scroller の通常の処理）ではグリッドがちらついたり途中で止まったりするのに対し、
代行では止まらないことを見るのが本当の合格条件。

### G. 代行できない環境
1. **純バニラサーバー**（`runClientVanilla`）: `masscraft` 関数で棒を長押し → Item Scroller の通常の処理で作られる。
   ログに警告やエラーが出ない
2. **シングルプレイ**（`runClient`）: 同上（シングルプレイでは代行しない）
3. **Item Scroller 無し**（`./gradlew runClientSync -PnoItemScroller`）
   - 起動ログに「massCraft の代行（Item Scroller 連携）: 無効」が出る
   - `ClassNotFoundException` / `NoClassDefFoundError` が出ない
   - Auto Repair（B の手順）は普通に動く
4. **取りこぼしたインベントリ同期の解放**（v1.0.2 の回帰確認）
   1. 作業台で **素材が 5 個以上のレシピ**（かまど＝丸石 8 個など）を記憶する
   2. 作業台を閉じ、**インベントリ画面（2x2）**を開いて massCraft キーを押す
      （Item Scroller 側に「覚えたレシピがグリッドに入りきらないとき、インベントリ同期を
      溜める旗を立てたまま抜ける」経路があり、ここを通る）
   3. そのままアイテムを拾う・シュルカーを開く・インベントリを操作する
      → **どれも普通にできること**（v1.0.1 以前は、ここで同期が止まって操作できなくなった）
   4. ログに「Item Scroller がインベントリ同期のパケットを溜めたままにしていたので……」が
      1 回だけ出ていてよい（直した記録）
5. **Item Scroller を上げたとき**: `gradle.properties` の `itemscroller_version` を新しい版に変えて `runClientSync`
   - 起動ログに「Item Scroller <版> に割り込み先を確認しました。」が出れば、そのまま代行が働く
   - 「割り込み先が見当たらない」と出た場合は代行が無効になる（Item Scroller の通常の処理に戻るだけで、壊れはしない）
   - メソッドの有無しか見ていないので、大きな版上げのときは massCraft の挙動も F の手順で確かめること
