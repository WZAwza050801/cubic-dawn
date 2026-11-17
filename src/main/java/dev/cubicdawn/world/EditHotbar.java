package dev.cubicdawn.world;

/** 旧编辑验收的材料表。游戏实际快捷栏使用 Inventory 的前 9 格及其数量。 */
public final class EditHotbar {

    public static final int SLOTS = 9;

    private static final int[] IDS = {
            BlockIds.GRASS,
            BlockIds.DIRT,
            BlockIds.STONE,
            BlockIds.MARKER_POSITIVE_X,
            BlockIds.MARKER_NEGATIVE_X,
            BlockIds.MARKER_POSITIVE_Z,
            BlockIds.MARKER_NEGATIVE_Z,
            BlockIds.SAND,
            BlockIds.STONE
    };

    private EditHotbar() {
    }

    public static int idAt(int slot) {
        return IDS[Math.floorMod(slot, SLOTS)];
    }

    public static int shift(int slot, int delta) {
        return Math.floorMod(slot + delta, SLOTS);
    }
}
