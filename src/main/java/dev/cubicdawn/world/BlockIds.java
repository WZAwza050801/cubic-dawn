package dev.cubicdawn.world;

/** 稳定的数字编号。存档写这些编号；未知正编号保留，不换成空气。 */
public final class BlockIds {

    public static final int AIR = 0;
    public static final int GRASS = 1;
    public static final int DIRT = 2;
    public static final int STONE = 3;
    public static final int MARKER_POSITIVE_X = 4;
    public static final int MARKER_NEGATIVE_X = 5;
    public static final int MARKER_POSITIVE_Z = 6;
    public static final int MARKER_NEGATIVE_Z = 7;
    public static final int SAND = 8;
    public static final int SANDSTONE = 9;
    public static final int STONE_BRICKS = 10;
    public static final int LOG = 11;
    public static final int LEAVES = 12;
    public static final int PLANKS = 13;
    public static final int WORKBENCH = 14;
    public static final int FURNACE = 15;
    public static final int CHEST = 16;
    public static final int GLASS = 17;
    /** 只给支撑测试用，不放进快捷栏。 */
    public static final int SUPPORT_PROBE = 100;

    private BlockIds() {
    }

    public static boolean replaceable(int id) {
        return id == AIR;
    }

    public static boolean hotbarMaterial(int id) {
        return id == FURNACE || id == CHEST || id == GLASS || id == LOG || id == LEAVES || id == PLANKS || id == WORKBENCH
                || id == SANDSTONE || id == STONE_BRICKS || id == SAND
                || id == GRASS
                || id == DIRT
                || id == STONE
                || (id >= MARKER_POSITIVE_X && id <= MARKER_NEGATIVE_Z);
    }

    public static boolean placeable(int id) {
        return hotbarMaterial(id) || id == SUPPORT_PROBE;
    }

    /** 完整方块始终算有支撑。探针必须踩在已加载的实心格上。 */
    public static boolean needsSupportBelow(int id) {
        return id == SUPPORT_PROBE;
    }
}
