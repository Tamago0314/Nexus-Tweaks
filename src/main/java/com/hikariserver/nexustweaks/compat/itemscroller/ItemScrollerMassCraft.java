package com.hikariserver.nexustweaks.compat.itemscroller;

import java.util.ArrayList;
import java.util.List;

import com.hikariserver.nexustweaks.feature.MassCraftClient;
import com.hikariserver.nexustweaks.network.MassCraftRequestPayload;

import fi.dy.masa.itemscroller.config.Configs;
import fi.dy.masa.itemscroller.config.Hotkeys;
import fi.dy.masa.itemscroller.recipes.CraftingHandler;
import fi.dy.masa.itemscroller.recipes.RecipePattern;
import fi.dy.masa.itemscroller.recipes.RecipeStorage;
import fi.dy.masa.malilib.util.GuiUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Item Scroller の massCraft を、サーバー（Nexus-Sync）への代行要求に置き換える橋渡し。
 *
 * Item Scroller の状態（massCraft のキー・選択中のレシピ・対象の画面）を読んで要求を組み立て、
 * 代行できるときは Item Scroller 自身のクリック連打を止める。
 * 代行できないとき（サーバーが非対応・形が合わないなど）は何もせず、Item Scroller の通常の処理に任せる。
 *
 * Item Scroller のクラスを参照してよいのは、このパッケージと mixin.itemscroller だけ。
 * それ以外から参照すると、Item Scroller が入っていない環境でクラスの読み込みに失敗する。
 * このクラスは mixin からしか呼ばれないので、Item Scroller が無ければ読み込まれることも無い。
 */
public final class ItemScrollerMassCraft {

    /**
     * Item Scroller の massCraft 1 回（massCraftIterations の 1 回分）で進むクラフト回数の目安。
     *
     * Item Scroller は 1 回ごとにグリッドの各マスへ最大 1 スタック（64 個）積んで出力を投げるので、
     * 1 回で最大 64 回クラフトされる。要望値はこれに回数を掛けたもの（実際の上限はサーバー設定で決まる）。
     */
    private static final int CRAFTS_PER_ITERATION = 64;

    /** ユーティリティクラスなのでインスタンス化を禁止する。 */
    private ItemScrollerMassCraft() {
    }

    /**
     * Item Scroller が massCraft の処理を始める直前に、毎 tick 呼ばれる。
     *
     * @return true なら Item Scroller の massCraft の処理をこの tick は飛ばす（代行中、または応答待ち）。
     *         false なら Item Scroller にそのまま処理させる
     */
    public static boolean onMassCraftTick(Minecraft mc) {
        if (!MassCraftClient.isAvailable() || mc.player == null || mc.level == null) {
            return false;
        }

        // 発動条件は Item Scroller 自身の判定と同じにする（画面の種類・クリエイティブ除外・GUI のブラックリスト・
        // massCraft のキーを押しているか massCraftHold が ON か）。
        Screen screen = GuiUtils.getCurrentScreen();

        if (!(screen instanceof AbstractContainerScreen<?> gui)
                || screen instanceof CreativeModeInventoryScreen
                || Configs.GUI_BLACKLIST.contains(screen.getClass().getName())
                || !isMassCraftActive()) {
            MassCraftClient.onInactive();
            return false;
        }

        MassCraftRequestPayload request = buildRequest(gui);

        // 代行できない形（変形グリッド・レシピ未選択など）なら Item Scroller に任せる。
        if (request == null) {
            return false;
        }

        // 応答待ち、または空振り後の待ち時間。
        // ここで Item Scroller に処理させると、サーバーの代行とクライアントの連打が同時に走って
        // グリッドの中身を取り合うので、この tick は何もしない。
        if (!MassCraftClient.canSendNow()) {
            return true;
        }

        // 送れなかった（大きすぎるなど）ときだけ Item Scroller に任せる。
        return MassCraftClient.send(request);
    }

    /** massCraft のキーを押しているか、massCraftHold（押しっぱなし扱い）が ON かを返す。 */
    private static boolean isMassCraftActive() {
        return Hotkeys.MASS_CRAFT.getKeybind().isKeybindHeld()
                || Configs.Generic.MASS_CRAFT_HOLD.getBooleanValue();
    }

    /**
     * 今の画面と Item Scroller で選択中のレシピから、代行の要求を組み立てる。
     *
     * @return 代行できないときは null
     */
    private static MassCraftRequestPayload buildRequest(AbstractContainerScreen<?> gui) {
        AbstractContainerMenu menu = gui.getMenu();

        // サーバー側は AbstractCraftingMenu（作業台・インベントリ）だけを扱う。
        if (!(menu instanceof AbstractCraftingMenu craftingMenu)) {
            return null;
        }

        // Item Scroller がクラフト画面として認識している画面に限る（ユーザーの設定に従う）。
        Slot outputSlot = CraftingHandler.getFirstCraftingOutputSlotForGui(gui);

        if (outputSlot == null) {
            return null;
        }

        CraftingHandler.SlotRange range = CraftingHandler.getCraftingGridSlots(gui, outputSlot);
        RecipePattern recipe = RecipeStorage.getInstance().getSelectedRecipe();

        if (range == null || recipe == null || !recipe.isValid()) {
            return null;
        }

        int width = craftingMenu.getGridWidth();
        int height = craftingMenu.getGridHeight();
        int slotCount = width * height;

        // 記録したときのグリッドと今のグリッドの大きさが違う場合（3x3 で記録したレシピを 2x2 で使うなど）は
        // 並びの対応が決まらないので、Item Scroller に任せる。
        if (width > MassCraftRequestPayload.MAX_GRID_SIDE
                || height > MassCraftRequestPayload.MAX_GRID_SIDE
                || range.getSlotCount() != slotCount
                || recipe.getRecipeLength() != slotCount) {
            return null;
        }

        // Item Scroller の配列をそのまま渡さず、写しを作る（向こうのデータを書き換えないため）。
        ItemStack[] items = recipe.getRecipeItems();
        List<ItemStack> pattern = new ArrayList<>(slotCount);
        boolean hasIngredient = false;

        for (int i = 0; i < slotCount; i++) {
            ItemStack stack = items[i] == null ? ItemStack.EMPTY : items[i].copy();
            hasIngredient |= !stack.isEmpty();
            pattern.add(stack);
        }

        if (!hasIngredient) {
            return null;
        }

        // 1 回の要求で回したい回数。Item Scroller の massCraftIterations をそのまま使い、
        // ユーザーが設定した感覚を変えないようにする（実際の上限はサーバー設定で切り詰められる）。
        int iterations = Math.max(1, Configs.Generic.MASS_CRAFT_ITERATIONS.getIntegerValue());
        int maxCrafts = (int) Math.min(Integer.MAX_VALUE, (long) iterations * CRAFTS_PER_ITERATION);

        return new MassCraftRequestPayload(menu.containerId, width, height,
                pattern, recipe.getResult().copy(), maxCrafts);
    }
}
