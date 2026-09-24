// Loom プラグインの取得元。Fabric の maven が無いと fabric-loom を解決できない。
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
    }
}

// IDE 上の表示名。archives_base_name とは別物。
rootProject.name = "nexus-tweaks"
