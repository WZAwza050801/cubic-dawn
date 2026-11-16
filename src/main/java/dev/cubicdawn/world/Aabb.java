package dev.cubicdawn.world;

/** 轴对齐盒子。max 是盒子的远平面，单位立方格 [x, x+1) 的 max 为 x+1。 */
public record Aabb(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

    public static Aabb unitCell(int x, int y, int z) {
        return new Aabb(x, y, z, x + 1f, y + 1f, z + 1f);
    }

    public boolean containsStrict(float x, float y, float z) {
        return x > minX && x < maxX
                && y > minY && y < maxY
                && z > minZ && z < maxZ;
    }
}
