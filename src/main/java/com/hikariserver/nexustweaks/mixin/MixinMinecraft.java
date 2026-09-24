package com.hikariserver.nexustweaks.mixin;

import com.hikariserver.nexustweaks.feature.AutoRepair;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * バニラの「アイテム使用（右クリック）」処理へ割り込むための mixin。
 *
 * Minecraft.startUseItem() は、使用キーが押されたときに
 * handleKeybinds() から呼ばれる private メソッド。
 * この中でブロック使用 / エンティティ使用 / アイテム使用の振り分けが行われる。
 *
 * ここでは処理の一番先頭に割り込んで、Auto Repair の条件を満たしていれば
 * バニラの処理をまるごとキャンセルして修理に差し替える。
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    /**
     * startUseItem() の先頭に割り込む。
     *
     * cancellable = true にしておかないと ci.cancel() が使えない。
     */
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void nexustweaks$onStartUseItem(CallbackInfo ci) {
        // this は Minecraft のインスタンスそのものなのでキャストして渡す。
        Minecraft mc = (Minecraft) (Object) this;

        // 条件を満たしていれば AutoRepair 側で修理を走らせ、true が返ってくる。
        if (AutoRepair.onStartUseItem(mc)) {
            // バニラのアイテム使用処理は行わせない。
            // これをしないと、修理と同時にアイテムが使われてしまう。
            ci.cancel();
        }
    }
}
