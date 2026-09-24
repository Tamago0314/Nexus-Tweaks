package com.hikariserver.nexustweaks.mixin.itemscroller;

import com.hikariserver.nexustweaks.compat.itemscroller.ItemScrollerMassCraft;

import fi.dy.masa.itemscroller.event.KeybindCallbacks;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Item Scroller の massCraft をサーバーへの代行要求に差し替える mixin。
 *
 * Item Scroller 0.32 系では、massCraft の処理はまるごと KeybindCallbacks.onClientTickMassCraftImpl() に
 * 切り出されている。この中で
 *   「画面の種類・クリエイティブ除外・GUI のブラックリスト・
 *     massCraft のキーを押している || massCraftHold が ON」
 * を判定し、条件を満たせばクリックの連打でクラフトする。
 *
 * ここではその先頭に割り込み、代行を頼めたときだけメソッドを丸ごと飛ばす。
 * 頼めないときはそのまま通すので、Item Scroller の通常の処理になる。
 *
 * 先頭で飛ばしても困らないことは確認済み。呼び出し元の onClientTick() が先に済ませている
 * クリックの送信バッファの処理（ClickPacketBuffer）は、このメソッドの外にある。
 *
 * このクラスは ItemScrollerMixinPlugin が「対応する Item Scroller が入っている」と
 * 判断したときにだけ適用される。
 */
@Mixin(KeybindCallbacks.class)
public abstract class MixinKeybindCallbacks {

    /**
     * onClientTickMassCraftImpl() の先頭に割り込む。
     *
     * cancellable = true にしておかないと ci.cancel() が使えない。
     */
    @Inject(method = "onClientTickMassCraftImpl", at = @At("HEAD"), cancellable = true)
    private void nexustweaks$delegateMassCraftToServer(Minecraft mc, CallbackInfo ci) {
        if (ItemScrollerMassCraft.onMassCraftTick(mc)) {
            ci.cancel();
        }
    }
}
