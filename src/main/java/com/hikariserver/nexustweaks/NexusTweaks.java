package com.hikariserver.nexustweaks;

import fi.dy.masa.malilib.event.InitializationHandler;
import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * MOD のエントリポイント。
 *
 * fabric.mod.json の environment が "client" なので、このクラスは
 * クライアントでしか読み込まれない。
 *
 * ここでは malilib へ初期化ハンドラを渡すだけにして、
 * 実際の登録処理は InitHandler へ寄せてある。
 * malilib 本体より先にこのクラスが読み込まれる可能性があるため、
 * ここで malilib の設定やキーバインドを触ってはいけない。
 */
public class NexusTweaks implements ModInitializer {

    /** ログ出力用。ログの行頭に MOD 名が出るので、どの MOD の出力か分かる。 */
    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    @Override
    public void onInitialize() {
        // malilib の初期化が終わったタイミングで InitHandler.registerModHandlers() が呼ばれる。
        InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
    }
}
