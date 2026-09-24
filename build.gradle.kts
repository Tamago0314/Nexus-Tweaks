// ── プラグイン ────────────────────────────────────────────────────────
// fabric-loom 1.17.20 を使う。
// 26.x は Mojang が難読化マップを公開しておらず（intermediary も 0.0.0）、
// Loom 側に「難読化されていない Minecraft」を扱うモード（fabric.loom.disableObfuscation）が要る。
// この分岐が入ったのが Loom 1.17 系なので、それより古い Loom では 26.x をビルドできない。
plugins {
    id("fabric-loom") version "1.17.20"
}

// ── gradle.properties の読み出し ──────────────────────────────────────
// 型安全アクセサ（project.minecraft_version など）は使わず、明示的に property() で取る。
val minecraftVersion  = property("minecraft_version")  as String
val minecraftRange    = property("minecraft_range")    as String
val loaderVersion     = property("loader_version")     as String
val fabricApiVersion  = property("fabric_api_version") as String
val fabricApiModId    = property("fabric_api_mod_id")  as String
val malilibVersion    = property("malilib_version")    as String
val malilibMin        = property("malilib_min")        as String
val itemScrollerVersion = property("itemscroller_version") as String
val itemScrollerMin   = property("itemscroller_min")   as String
val javaVersionNumber = (property("java_version") as String).toInt()
val modId             = property("mod_id")             as String
val modName           = property("mod_name")           as String
val modVersion        = property("mod_version")        as String
val mavenGroup        = property("maven_group")        as String
val archivesBaseName  = property("archives_base_name") as String

// 検証用クライアントのユーザー名。
// Loom の loom { runs { ... } } より前で宣言しておかないと、スクリプトの実行順の都合で
// まだ初期化されていない（null の）値を参照してしまう。
// テスト用サーバー（Nexus-Sync/run, Nexus-Sync/run-vanilla）の ops.json と一致させること。
val TEST_USERNAME = "NexusTester"

// Mixin の compatibilityLevel。Java 25 なら "JAVA_25"。
// mixins.json 側は ${java} プレースホルダにしてあり、processResources で差し替える。
val mixinCompatLevel = "JAVA_$javaVersionNumber"

// jar のファイル名は「<mod version>+<mc version>」にして、どの MC 向けか一目で分かるようにする。
version = "$modVersion+$minecraftVersion"
group = mavenGroup

base { archivesName.set(archivesBaseName) }

// ── リポジトリ ────────────────────────────────────────────────────────
repositories {
    mavenCentral()

    // Minecraft / fabric-loader / fabric-api の取得元。
    maven { name = "Fabric"; url = uri("https://maven.fabricmc.net/") }

    // malilib は Modrinth の maven から取る（masa 本家の maven は 26.x を配信していない）。
    // exclusiveContent で「maven.modrinth グループはここからしか探さない／
    // ここでは他のグループを探さない」と宣言し、無駄な問い合わせと誤解決を防ぐ。
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

// ── 依存 ──────────────────────────────────────────────────────────────
dependencies {
    // Minecraft 本体。add() の文字列指定を使うのは、後続の mappings と書き方を揃えるため。
    add("minecraft", "com.mojang:minecraft:$minecraftVersion")

    // **mappings は宣言しない。**
    // fabric.loom.disableObfuscation=true のとき Loom は mappings コンフィギュレーション自体を
    // 作らないため、mappings(...) と書くと「Unresolved reference」でビルドスクリプトが壊れる。

    // 難読化が無ければリマップする意味も無いので、依存も modImplementation ではなく
    // 素の implementation で入れる（Fabric 公式の 26.x テンプレートも同じ形）。
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // malilib。isTransitive = false は必須。
    // malilib の POM は fabric-loader 0.13.3 を推移依存として引き連れてくるので、
    // これを切らないと dev 実行時に loader がクラスパスへ二重に載って起動しなくなる。
    implementation("maven.modrinth:malilib:$malilibVersion") { isTransitive = false }

    // Item Scroller（任意の連携先）。
    // massCraft の代行で Item Scroller のクラスに割り込むので、コンパイル時だけ参照する。
    // 配布物には入れず、fabric.mod.json でも depends ではなく suggests にしてある
    // （Item Scroller が無いクライアントでも、その機能が無いだけで普通に動く）。
    compileOnly("maven.modrinth:item-scroller:$itemScrollerVersion") { isTransitive = false }

    // 開発用クライアント（runClient など）では Item Scroller も一緒に読み込む。
    // -PnoItemScroller を付けると外して起動でき、Item Scroller が無い環境でも落ちないことを確かめられる。
    //   例: ./gradlew runClient -PnoItemScroller
    if (!project.hasProperty("noItemScroller")) {
        runtimeOnly("maven.modrinth:item-scroller:$itemScrollerVersion") { isTransitive = false }
    }
}

// ── Loom の設定 ───────────────────────────────────────────────────────
loom {
    mixin {
        // **useLegacyMixinAp は絶対に有効化しない。**
        // 非難読化環境では Loom の MixinAPMappingService が
        // 「Cannot get mappings configuration in a non-obfuscated environment」で必ず落ちる。
        // 既定の static（refmap レス）方式に任せる。
        useLegacyMixinAp = false
    }

    // runClient / runServer の作業ディレクトリ。
    // リポジトリ直下が options.txt やワールドで散らからないよう run/ にまとめる。
    runConfigs.all {
        runDirectory = rootProject.file("run")
    }

    // ── 検証用のクライアント起動設定 ──────────────────────────────────
    // どれも「Gradle タスク runXxx」として使える（例: ./gradlew runClientSync）。
    runs {
        // 通常の runClient にもユーザー名を固定で渡す。
        // 何も渡さないと Minecraft が "Player123" のようにランダムな名前を付けるため、
        // サーバー側で OP を事前に付与しておくことができない。
        // テスト用サーバーの ops.json には、この名前のオフライン UUID を登録してある。
        named("client") {
            programArgs("--username", TEST_USERNAME)
        }

        // Nexus-Sync 入りの開発用サーバー（localhost:25565）へ起動と同時に接続する。
        // → サーバー経由で修理される経路の確認用。
        register("clientSync") {
            inherit(getByName("client"))
            name("Minecraft Client (→ Nexus-Sync server :25565)")
            programArgs("--quickPlayMultiplayer", "localhost:25565")
        }

        // Nexus-Sync の入っていない純バニラサーバー（localhost:25566）へ接続する。
        // → 「このサーバーは非対応です」と出て何も起きないことの確認用。
        register("clientVanilla") {
            inherit(getByName("client"))
            name("Minecraft Client (→ vanilla server :25566)")
            programArgs("--quickPlayMultiplayer", "localhost:25566")
        }

        // Nexus-Sync も同時に読み込んだ状態でクライアントを起動する。
        // → シングルプレイで両方入れたとき、パケット型の衝突で落ちないことと、
        //   通信を使わず統合サーバーへの直接適用で修理されることの確認用。
        // fabric.addMods は Fabric Loader の機能で、指定した jar を追加の MOD として読み込ませる。
        register("clientBoth") {
            inherit(getByName("client"))
            name("Minecraft Client (+ Nexus-Sync, singleplayer)")
            vmArg("-Dfabric.addMods=${findNexusSyncJar().absolutePath}")
        }
    }
}

// ── 検証環境で使うヘルパー ────────────────────────────────────────────
/**
 * 隣の Nexus-Sync プロジェクトがビルドした jar を探す。
 *
 * バージョン番号を決め打ちにすると、Nexus-Sync 側を上げたときに追従できないので、
 * build/libs の中から「sources ではない nexus-sync-*.jar」を探して一番新しいものを使う。
 * 見つからない場合は、起動したときに分かるよう存在しないパスを返す（Loader が明確なエラーを出す）。
 */
fun findNexusSyncJar(): File {
    // 兄弟ディレクトリの Nexus-Sync/build/libs を見る。
    val libs = rootProject.file("../Nexus-Sync/build/libs")

    // 候補を集めて、更新日時が新しい順に並べる。
    val candidate = libs.listFiles { f ->
        f.name.startsWith("nexus-sync-") && f.name.endsWith(".jar") && !f.name.endsWith("-sources.jar")
    }?.maxByOrNull { it.lastModified() }

    // 見つからなければ「先に Nexus-Sync を build してね」と分かるパスを返す。
    return candidate ?: File(libs, "nexus-sync-NOT-BUILT-run-gradlew-build-in-Nexus-Sync.jar")
}

// ── コンパイル設定 ────────────────────────────────────────────────────
tasks.withType<JavaCompile>().configureEach {
    // 日本語コメントを含むので UTF-8 固定。
    options.encoding = "UTF-8"
    // toolchain ではなく --release を使う。実際に使う JDK のバージョンに依存せず
    // 「Java 25 の API だけを使う」ことを保証できる。
    options.release.set(javaVersionNumber)
}

java {
    // ソース jar も出す（IDE で MOD の中身を追えるように）。
    withSourcesJar()
    sourceCompatibility = JavaVersion.toVersion(javaVersionNumber)
    targetCompatibility = JavaVersion.toVersion(javaVersionNumber)
}

// runClient / runServer を起動する JVM は Java 25 を明示的に選ぶ。
// 26.x は Java 25 必須なので、Gradle 自身が別バージョンで動いていても取り違えないようにする。
val javaToolchains = extensions.getByType(JavaToolchainService::class.java)
tasks.withType<JavaExec>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(javaVersionNumber))
    })
}

// ── リソースのテンプレート展開 ────────────────────────────────────────
// fabric.mod.json と mixins.json の中の ${...} を実際の値へ置き換える。
tasks.processResources {
    val props = mapOf(
        "id"           to modId,
        "name"         to modName,
        "version"      to project.version.toString(),
        "minecraft"    to minecraftRange,
        "loader"       to loaderVersion,
        "java"         to javaVersionNumber.toString(),
        "fabric_api"   to fabricApiModId,
        "malilib_min"  to malilibMin,
        "itemscroller_min" to itemScrollerMin,
    )
    // inputs.property に入れておかないと、値だけ変えたときに Gradle が
    // 「変更なし」と判断して再生成をスキップしてしまう。
    props.forEach { (k, v) -> inputs.property(k, v) }

    filesMatching("fabric.mod.json") { expand(props) }
    filesMatching("*.mixins.json") { expand("java" to mixinCompatLevel) }
}

// ── 成果物について ────────────────────────────────────────────────────
// 非難読化ターゲット（26.x）では Loom が remapJar タスクを登録しないため、
// 配布用の jar は通常の jar タスクの出力そのもの（build/libs/ に出る）。
// 単一プロジェクト構成なので、別ディレクトリへ集約するコピータスクは用意しない。
// （ルートと自分の build ディレクトリが同一なので、コピーすると自分自身を
//   上書きして 0 バイトの jar ができてしまう。）
