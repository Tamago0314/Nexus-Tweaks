package com.hikariserver.nexustweaks.mixin.itemscroller;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Item Scroller 向けの mixin を適用するかどうかを決める。
 *
 * Item Scroller が入っていない、または割り込み先が見当たらないときは mixin を丸ごと適用しない。
 * これで Item Scroller が無くても起動でき、向こうの作りが変わった版でも
 * 黙って壊れることは無い（代行しないだけで、Item Scroller は通常どおり動く）。
 *
 * 判定は「バージョン番号」ではなく「割り込み先のメソッドが実在するか」で行う。
 * バージョンで縛ると、中身が変わっていない新しい版まで弾いてしまうため。
 * クラスを読めないなど判定できない場合だけ、確認済みのバージョン範囲で判断する。
 */
public class ItemScrollerMixinPlugin implements IMixinConfigPlugin {

    /** 割り込み先のクラス。 */
    private static final String TARGET_CLASS = "fi/dy/masa/itemscroller/event/KeybindCallbacks";

    /** 割り込み先のメソッド名。 */
    private static final String TARGET_METHOD = "onClientTickMassCraftImpl";

    /**
     * 割り込み先のメソッドの形。
     *
     * 26.x は難読化されないので、実行時も Mojang の名前のまま。
     */
    private static final String TARGET_DESCRIPTOR = "(Lnet/minecraft/client/Minecraft;)V";

    /**
     * 実際に動かして確認済みのバージョン範囲。
     *
     * 0.32.0 / 0.32.1 / 0.32.2 で、割り込み先に massCraft の判定と処理があることを確認してある。
     * 上のメソッドの有無を調べられなかったときの、代わりの判断材料として使う。
     */
    private static final String KNOWN_GOOD_ITEMSCROLLER = ">=0.32.0 <0.33.0";

    /**
     * ログ出力用。
     *
     * mixin の設定を読み込む時点ではまだ他のクラスを巻き込みたくないので、
     * NexusTweaks.LOGGER は使わず同じ名前で独立に取る。
     */
    private static final Logger LOGGER = LogManager.getLogger("Nexus-Tweaks");

    /** mixin を適用するかどうか。onLoad で 1 度だけ決める。 */
    private boolean applicable;

    @Override
    public void onLoad(String mixinPackage) {
        this.applicable = this.checkApplicable();

        // 代行が効いているかどうかは、見た目では「massCraft が速いか遅いか」でしか分からない。
        // 切り分けのために、有効／無効を必ず 1 行残す。
        LOGGER.info("massCraft の代行（Item Scroller 連携）: {}", this.applicable ? "有効" : "無効");
    }

    /** Item Scroller が入っていて、割り込み先が実在するかを調べる。 */
    private boolean checkApplicable() {
        ModContainer container = FabricLoader.getInstance().getModContainer("itemscroller").orElse(null);

        if (container == null) {
            return false;
        }

        String version = container.getMetadata().getVersion().getFriendlyString();
        Boolean found = findTargetMethod(container);

        if (found == null) {
            // クラスを読めなかった。バージョン番号で判断するしかない。
            return checkKnownGoodVersion(container, version);
        }

        if (!found) {
            LOGGER.warn("Item Scroller {} に割り込み先（{}.{}）が見当たらないため、massCraft の代行を無効にします。",
                    version, TARGET_CLASS.substring(TARGET_CLASS.lastIndexOf('/') + 1), TARGET_METHOD);
            return false;
        }

        LOGGER.info("Item Scroller {} に割り込み先を確認しました。", version);
        return true;
    }

    /**
     * Item Scroller のクラスを読んで、割り込み先のメソッドがあるかを調べる。
     *
     * @return ある／無い。クラスを読めなかったときは null
     */
    private Boolean findTargetMethod(ModContainer container) {
        try {
            byte[] bytes = readClassBytes(container);

            if (bytes == null) {
                return null;
            }

            // メソッドの一覧さえ分かればよいので、中身は読み飛ばす。
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            for (MethodNode method : node.methods) {
                if (TARGET_METHOD.equals(method.name) && TARGET_DESCRIPTOR.equals(method.desc)) {
                    return true;
                }
            }

            return false;
        } catch (Throwable t) {
            // 読めない／解釈できない形式だった。ここで機能を落とさず、バージョン番号での判断に回す。
            LOGGER.warn("Item Scroller のクラスを確認できませんでした: {}", t.toString());
            return null;
        }
    }

    /** Item Scroller の jar（開発環境ではクラスの置き場）から、割り込み先のクラスを読み出す。 */
    private byte[] readClassBytes(ModContainer container) throws Exception {
        Optional<Path> path = container.findPath(TARGET_CLASS + ".class");

        if (path.isPresent()) {
            return Files.readAllBytes(path.get());
        }

        // mod の中から見つからない場合は、クラスパス経由で探す。
        try (InputStream stream = this.getClass().getClassLoader()
                .getResourceAsStream(TARGET_CLASS + ".class")) {
            return stream == null ? null : stream.readAllBytes();
        }
    }

    /** 割り込み先を確認できなかったときに、バージョン番号で判断する。 */
    private boolean checkKnownGoodVersion(ModContainer container, String version) {
        try {
            if (!VersionPredicate.parse(KNOWN_GOOD_ITEMSCROLLER).test(container.getMetadata().getVersion())) {
                LOGGER.warn("Item Scroller {} は確認済みの範囲 ({}) の外なので、massCraft の代行を無効にします。",
                        version, KNOWN_GOOD_ITEMSCROLLER);
                return false;
            }
        } catch (Exception e) {
            // バージョン文字列を解釈できないだけで機能を落とす理由は無いので、適用して試す。
            LOGGER.warn("Item Scroller のバージョンを判定できませんでした: {}", e.toString());
        }

        return true;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return this.applicable;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
