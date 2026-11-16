package dev.cubicdawn.world;

/**
 * 玩家身体沿用现有尺寸：眼睛在盒子中心，半宽 0.3，半高 0.9。
 * 方块中心在整数坐标，半边长 0.5。边界相贴不算重叠。
 */
public final class PlayerBody {

    public static final float HALF_WIDTH = 0.3f;
    public static final float HALF_HEIGHT = 0.9f;
    public static final float CUBE_HALF = 0.5f;
    public static final float GRAVITY = 20f;
    public static final float JUMP_SPEED = 7.1f;
    public static final float GROUND_PROBE = 0.05f;
    public static final float WALK_SPEED = 2f;

    private PlayerBody() {
    }

    public interface SolidAt {
        boolean solid(int x, int y, int z);
    }

    public static boolean overlapsBlock(float x, float y, float z, int blockX, int blockY, int blockZ) {
        return Math.abs(x - blockX) < HALF_WIDTH + CUBE_HALF
                && Math.abs(y - blockY) < HALF_HEIGHT + CUBE_HALF
                && Math.abs(z - blockZ) < HALF_WIDTH + CUBE_HALF;
    }

    public static boolean overlapsSolid(float x, float y, float z, SolidAt solid) {
        int minX = lowestOverlappingIndex(x, HALF_WIDTH);
        int maxX = highestOverlappingIndex(x, HALF_WIDTH);
        int minY = lowestOverlappingIndex(y, HALF_HEIGHT);
        int maxY = highestOverlappingIndex(y, HALF_HEIGHT);
        int minZ = lowestOverlappingIndex(z, HALF_WIDTH);
        int maxZ = highestOverlappingIndex(z, HALF_WIDTH);
        for (int blockY = minY; blockY <= maxY; blockY++) {
            for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
                for (int blockX = minX; blockX <= maxX; blockX++) {
                    if (solid.solid(blockX, blockY, blockZ)
                            && overlapsBlock(x, y, z, blockX, blockY, blockZ)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean onGround(float x, float y, float z, SolidAt solid) {
        return overlapsSolid(x, y - GROUND_PROBE, z, solid);
    }

    private static int lowestOverlappingIndex(float center, float halfExtent) {
        return (int) Math.floor((center - halfExtent) - CUBE_HALF) + 1;
    }

    private static int highestOverlappingIndex(float center, float halfExtent) {
        return (int) Math.ceil((center + halfExtent) + CUBE_HALF) - 1;
    }
}
