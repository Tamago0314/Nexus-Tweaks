# Nexus-Tweaks

Minecraft **26.2** / Fabric Loader **0.19.5** 向けのクライアントサイド Tweaks MOD。

## 機能

### Auto Repair
**スニークしながら**、何にも視点を合わせていない状態（クロスヘアが空を向いている状態）で
**アイテム使用ボタン（既定は右クリック）**を押すと、所持している経験値を消費して
選択中のホットバースロットにある道具を修繕します。

- スニークを条件にしているのは、空に向けた右クリックで意図せず発動しないようにするためです
- 比率はバニラの修繕（Mending）と同じ **経験値 1 ポイント = 耐久 2 回復**
- 経験値が足りない場合は、持っているぶんだけ部分的に回復します
- ブロックやエンティティに視点を合わせているときは発動せず、バニラどおりに動作します
- 弓・盾・食料など「使用アニメーションを持つ」アイテムは自動的に除外されます
- 防具・エリトラなど「右クリックで装備できる」アイテムも自動的に除外されます
  （空中右クリックの装備動作を横取りしないため）
- 釣り竿のように「アニメーション無しで空撃ちが成立する」アイテムは
  設定の除外リスト（既定で `minecraft:fishing_rod` が入っています）で指定します

### massCraft の代行（Item Scroller 連携・既定は OFF）
[Item Scroller](https://modrinth.com/mod/item-scroller) の **massCraft**（選択中のレシピで
まとめてクラフトし、成果物を投げ捨てる機能）を、クリックの連打ではなく**サーバー（Nexus-Sync）に代行**させます。

**既定は OFF です。**設定画面（`N` + `T`）の Tweaks タブから ON にしてください。
OFF のままでも Item Scroller 自身がレシピ本を使う方式で動くので、普通に使うぶんには困りません。
他の MOD の処理へ割り込む以上どうしても影響範囲が広いので、
「回線が悪い」「レシピ本に載らないレシピを大量に作る」といった必要がある人だけが使う形にしてあります。

- クライアントは「このレシピで N 回作って」という要求を **1 通**送るだけで、
  グリッドへの充填・クラフト・成果物の投げ捨てはサーバーがまとめて行います
- 回線が遅くても、グリッドが埋まらない・カーソルにアイテムが残る・途中で止まる、といった
  クリック連打特有の失敗が起きません。サーバーの負荷と通信量も連打より小さくなります
- 操作は Item Scroller のまま変わりません（レシピの記憶・`massCraft` キー・`massCraftHold` /
  `massCraftToggle`・`massCraftIterations` がそのまま使えます）
- 作れる量に上限はありません。サーバーは 1 回の要求に使う時間（既定 5 ミリ秒）で区切り、
  作りきれなければ「続きがある」と返します。クライアントはそのまま続きを頼むので、
  サーバーの 1 tick を長く占有せずに、いくらでも作り続けられます
- 使えないとき（Item Scroller が無い・サーバーに Nexus-Sync が無い／代行が無効・
  3x3 で記憶したレシピを 2x2 で使うなど形が合わない）は、**Item Scroller の通常の処理**に戻ります
- 対応する Item Scroller は、起動時に**割り込み先のメソッドが実在するか**を調べて判断します。
  バージョン番号で縛っていないので、中身が変わっていない新しい版でもそのまま動きます
  （確認済みは 0.32.0〜0.32.2。割り込み先が見当たらない版では代行を無効にします）

素材の消費・容器などの残り物（空のバケツ・ガラス瓶）・統計・レシピの解禁は、
出力スロットで Ctrl+Q を押したときと同じバニラの処理を通るので、手でクラフトしたときと同じ結果になります。

## 設定画面

`N` + `T`（N を押しながら T）で開きます。

| タブ | 内容 |
| --- | --- |
| General | Open Nexus-Tweaks Config のホットキー設定 |
| Tweaks  | Auto Repair の ON/OFF・トグル用ホットキー・除外リスト、massCraft の代行の ON/OFF（既定 OFF） |

バニラの「操作設定 → キー割り当て」にも **Nexus-Tweaks** カテゴリで
`Open Nexus-Tweaks Config` が並びます。バニラのキー割り当ては同時押しに対応していないため、
こちらの既定値は **未割り当て** です。好きな 1 キーを割り当てて使ってください。

設定ファイルは `.minecraft/config/nexus-tweaks.json` です。

## サーバー側 MOD について

耐久値・経験値・インベントリは**サーバー権限**のデータなので、クライアント単体では
マルチプレイのバニラサーバーで本当の修理や代行はできません。動作は接続先によって変わります。

| 接続先 | Auto Repair | massCraft |
| --- | --- | --- |
| Nexus-Sync 導入済みサーバー | サーバーへ要求を送り、サーバー側で計算・適用します | サーバーが代行します |
| シングルプレイ | 統合サーバーのプレイヤーへ直接適用します（Nexus-Sync 不要） | Item Scroller の通常の処理 |
| Nexus-Sync 未導入のマルチサーバー | 何もせず「非対応です」とメッセージを出します | Item Scroller の通常の処理 |

シングルプレイの massCraft は遅延が無いので、Item Scroller の通常の処理でも問題なく動きます。

クライアントに Nexus-Sync も一緒に入れている場合は、サーバーとの通信を使いません
（両者のパケット型が衝突するため）。シングルプレイでは統合サーバーへ直接適用するので普通に動きますが、
マルチプレイでは Nexus-Sync 導入済みサーバーでも Auto Repair は「非対応です」、
massCraft は Item Scroller の通常の処理になります。

サーバー側 MOD は同リポジトリ群の **Nexus-Sync** です。
通信プロトコル（チャンネル ID・`PROTOCOL_VERSION`・パケットのフィールド順）は
両者で必ず一致させてください。片方だけ変えると連携が黙って無効になります。

- チャンネル名前空間: `nexus-sync`
- `nexus-sync:handshake`（S2C） / `nexus-sync:repair_request`（C2S） / `nexus-sync:repair_result`（S2C）
- `nexus-sync:mass_craft_request`（C2S） / `nexus-sync:mass_craft_result`（S2C）

`PROTOCOL_VERSION` を上げるのは既存パケットの中身を変えたときだけです。
massCraft のように機能を足すときは新しいチャンネルを足し、クライアントは `canSend` で
サーバー側にそのチャンネルがあるかを見て使うかを決めます。
こうしておけば、massCraft を知らない古い Nexus-Sync でも Auto Repair はそのまま使えます。

## 依存

- Fabric Loader `>=0.19.5`
- Fabric API（`fabric-api`）
- [MaLiLib](https://modrinth.com/mod/malilib) `>=0.29.6`
- Java 25
- （任意）[Item Scroller](https://modrinth.com/mod/item-scroller)（確認済み `0.32.0`〜`0.32.2`） — massCraft の代行を使う場合のみ

### Item Scroller との連携の作り
Item Scroller は任意の依存なので、無い環境でもクラスの読み込みで落ちないように作ってあります。

- Item Scroller のクラスを参照してよいのは `compat.itemscroller` と `mixin.itemscroller` の中だけ
  （`feature.MassCraftClient` や `network` からは参照しない）
- `mixin.itemscroller` は `ItemScrollerMixinPlugin` が適用の可否を判断する。
  Item Scroller のクラスを読んで、割り込み先の
  `KeybindCallbacks.onClientTickMassCraftImpl(Minecraft)` が実在するときだけ適用する
  （クラスを読めなかったときだけ、確認済みのバージョン範囲で判断する）。
  起動ログの `massCraft の代行（Item Scroller 連携）: 有効 / 無効` で判定結果が分かる
- メソッドの有無しか見ていないので、**中身の作りが変わった場合は検出できない**。
  Item Scroller を大きく上げたときは、massCraft の判定と処理がまだそのメソッドの中にあることを
  `javap -p -c` で確かめること

## ビルド

```
./gradlew build      # 成果物: build/libs/nexus-tweaks-<version>+26.2.jar
./gradlew runClient  # 開発用クライアントの起動（Item Scroller も一緒に読み込む）
./gradlew runClient -PnoItemScroller  # Item Scroller 抜きで起動できるかの確認
```

検証用サーバーへ自動接続する起動タスク（`runClientSync` / `runClientVanilla` / `runClientBoth`）と
検証手順は [TESTING.md](TESTING.md) を参照。

## バージョンを上げてリリースする

変更を入れたら、その都度バージョンを上げる。

1. `gradle.properties` の `mod_version` を上げる
2. 変更とまとめてコミットし、`main` へ push する（CI がビルドを確認する）
3. タグを付けて push する

```
git tag v1.0.1
git push origin v1.0.1
```

タグを push すると GitHub Actions が jar 付きの Release を作る。
タグと `mod_version` がずれているとワークフローが止まるので、1 の作業を忘れても事故にはならない。

番号の付け方:

| 上げる桁 | 対象 |
| --- | --- |
| パッチ（1.0.**x**） | **基本はこれ**。不具合の修正、文言の修正、既存機能の調整、小さな設定項目の追加 |
| マイナー（1.**x**.0） | Auto Repair や massCraft の代行のような、**新しい機能そのもの**を足したとき |
| メジャー（**x**.0.0） | 通信プロトコルの互換が切れる変更、設定ファイルの作り直し |

変更は細かく刻んで出す。1 つ直したらパッチを 1 つ上げる、という運用にする。

通信に関わる変更（チャンネルの追加・パケットの形の変更）をしたときは、
サーバー側の [Nexus-Sync](https://github.com/Tamago0314/Nexus-Sync) も同じ番号へ上げて、両方を同時に配ること。

26.x は Mojang が難読化マップを公開しておらず yarn も存在しないため、
`fabric.loom.disableObfuscation=true` を指定して **マッピング無し**でビルドします。
そのため以下の点が通常の Fabric MOD と異なります。

- `mappings` を宣言しない（Loom がコンフィギュレーション自体を作らない）
- 依存は `modImplementation` ではなく素の `implementation` を使う
- `remapJar` は登録されない。配布物は `jar` タスクの出力そのもの
- `loom.mixin.useLegacyMixinAp` を有効にするとビルドが落ちる（既定の static 方式のままにすること）
- `fabric.mod.json` の `depends` は `"fabric"` ではなく `"fabric-api"` を指定する
  （26.1 で fabric-api の `provides` から `fabric` が消えたため）
