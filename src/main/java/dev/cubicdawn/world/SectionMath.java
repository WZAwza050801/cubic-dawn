package dev.cubicdawn.world;

/**
 * 分区坐标。N=16。负数必须用 floor，不能用向零截断的整数除法。
 * 这里只负责换算，不创建分区。
 */
public final class SectionMath {

    public static final int EDGE = 16;

    private SectionMath() {
    }

    public static int section(int world) {
        return Math.floorDiv(world, EDGE);
    }

    public static int local(int world) {
        return Math.floorMod(world, EDGE);
    }

    public static int compose(int section, int local) {
        return section * EDGE + local;
    }

    /** i = (localY * N + localZ) * N + localX，范围 [0, N^3)。 */
    public static int index(int localX, int localY, int localZ) {
        return (localY * EDGE + localZ) * EDGE + localX;
    }

    public static int localX(int index) {
        return Math.floorMod(index, EDGE);
    }

    public static int localZ(int index) {
        return Math.floorMod(Math.floorDiv(index, EDGE), EDGE);
    }

    public static int localY(int index) {
        return Math.floorDiv(index, EDGE * EDGE);
    }
}
