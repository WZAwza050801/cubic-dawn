package dev.cubicdawn.world;

/** edit_test 快捷栏。数量不减少，不能当成生存模式的物品。 */
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
            BlockIds.DIRT,
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
