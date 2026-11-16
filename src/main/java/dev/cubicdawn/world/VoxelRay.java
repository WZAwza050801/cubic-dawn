package dev.cubicdawn.world;

/**
 * 规格格子里的射线：方块 (x,y,z) 占 [x, x+1)。
 * 现有画面里的方块中心在整数坐标，世界点 p 对应这里的 p+0.5。
 * 距离和法线在这个平移下不变，格子编号和画面用的是同一套。
 */
public final class VoxelRay {

    private static final float TIE = 1.0e-4f;
    private static final int STEP_FUSE = 10000;

    private VoxelRay() {
    }

    public interface WorldProbe {
        Cell inspect(int x, int y, int z);

        final class Cell {
            final boolean unloaded;
            final int stateId;
            final int revision;
            final Aabb[] boxes;

            private Cell(boolean unloaded, int stateId, int revision, Aabb[] boxes) {
                this.unloaded = unloaded;
                this.stateId = stateId;
                this.revision = revision;
                this.boxes = boxes;
            }

            public static Cell pass() {
                return new Cell(false, 0, 0, null);
            }

            public static Cell unloaded() {
                return new Cell(true, 0, 0, null);
            }

            public static Cell shape(int stateId, int revision, Aabb... boxes) {
                return new Cell(false, stateId, revision, boxes);
            }
        }
    }

    public enum Kind {
        MISS,
        HIT,
        BLOCKED
    }

    public record Hit(
            Kind kind,
            int x,
            int y,
            int z,
            int stateId,
            int revision,
            int shapePart,
            float distance,
            boolean hasNormal,
            boolean startedInside,
            int normalX,
            int normalY,
            int normalZ,
            float pointX,
            float pointY,
            float pointZ
    ) {
    }

    public static Hit castFromEngineEye(
            float eyeX,
            float eyeY,
            float eyeZ,
            float directionX,
            float directionY,
            float directionZ,
            float reach,
            WorldProbe probe
    ) {
        return cast(
                eyeX + 0.5f,
                eyeY + 0.5f,
                eyeZ + 0.5f,
                directionX,
                directionY,
                directionZ,
                reach,
                probe
        );
    }

    public static Hit cast(
            float originX,
            float originY,
            float originZ,
            float directionX,
            float directionY,
            float directionZ,
            float reach,
            WorldProbe probe
    ) {
        if (!finite(originX) || !finite(originY) || !finite(originZ)
                || !finite(directionX) || !finite(directionY) || !finite(directionZ)
                || !finite(reach) || reach < 0f) {
            return miss();
        }
        float length = (float) Math.sqrt(
                directionX * directionX + directionY * directionY + directionZ * directionZ
        );
        if (!(length > 0f) || !finite(length)) {
            return miss();
        }
        directionX /= length;
        directionY /= length;
        directionZ /= length;

        int x = startCell(originX, directionX);
        int y = startCell(originY, directionY);
        int z = startCell(originZ, directionZ);
        int stepX = sign(directionX);
        int stepY = sign(directionY);
        int stepZ = sign(directionZ);
        float tMaxX = boundaryTime(originX, x, directionX, stepX);
        float tMaxY = boundaryTime(originY, y, directionY, stepY);
        float tMaxZ = boundaryTime(originZ, z, directionZ, stepZ);
        float tDeltaX = stepX == 0 ? Float.POSITIVE_INFINITY : Math.abs(1f / directionX);
        float tDeltaY = stepY == 0 ? Float.POSITIVE_INFINITY : Math.abs(1f / directionY);
        float tDeltaZ = stepZ == 0 ? Float.POSITIVE_INFINITY : Math.abs(1f / directionZ);

        float cellEntry = 0f;
        for (int guard = 0; guard < STEP_FUSE && cellEntry <= reach; guard++) {
            WorldProbe.Cell cell = probe.inspect(x, y, z);
            if (cell.unloaded) {
                return new Hit(
                        Kind.BLOCKED, x, y, z, 0, 0, -1, cellEntry,
                        false, false, 0, 0, 0, 0f, 0f, 0f
                );
            }
            float nextBoundary = minFinite(tMaxX, tMaxY, tMaxZ);
            float cellExit = nextBoundary < reach ? nextBoundary : reach;
            if (cell.boxes != null) {
                Hit hit = nearestShape(
                        originX, originY, originZ,
                        directionX, directionY, directionZ,
                        reach, cellEntry, cellExit,
                        x, y, z, cell
                );
                if (hit != null) {
                    return hit;
                }
            }
            if (!(nextBoundary <= reach) || !finite(nextBoundary)) {
                break;
            }
            boolean moved = false;
            if (stepX != 0 && tMaxX <= nextBoundary + TIE) {
                x += stepX;
                tMaxX += tDeltaX;
                moved = true;
            }
            if (stepY != 0 && tMaxY <= nextBoundary + TIE) {
                y += stepY;
                tMaxY += tDeltaY;
                moved = true;
            }
            if (stepZ != 0 && tMaxZ <= nextBoundary + TIE) {
                z += stepZ;
                tMaxZ += tDeltaZ;
                moved = true;
            }
            if (!moved || nextBoundary + TIE < cellEntry) {
                break;
            }
            cellEntry = nextBoundary;
        }
        return miss();
    }

    /** 有法线时，放置目标是命中格加上外法线。起点在盒子内部则没有可用来放置的法线。 */
    public static int[] placeTarget(Hit hit) {
        if (hit == null || hit.kind != Kind.HIT || !hit.hasNormal || hit.startedInside) {
            return null;
        }
        return new int[] {hit.x + hit.normalX, hit.y + hit.normalY, hit.z + hit.normalZ};
    }

    private static Hit nearestShape(
            float originX,
            float originY,
            float originZ,
            float directionX,
            float directionY,
            float directionZ,
            float reach,
            float cellEntry,
            float cellExit,
            int cellX,
            int cellY,
            int cellZ,
            WorldProbe.Cell cell
    ) {
        Hit best = null;
        for (int part = 0; part < cell.boxes.length; part++) {
            Aabb box = cell.boxes[part];
            if (box.containsStrict(originX, originY, originZ)) {
                return new Hit(
                        Kind.HIT, cellX, cellY, cellZ, cell.stateId, cell.revision, part,
                        0f, false, true, 0, 0, 0, originX, originY, originZ
                );
            }
            Slab slab = slab(originX, originY, originZ, directionX, directionY, directionZ, box);
            if (slab == null) {
                continue;
            }
            float impact = Math.max(slab.enter, 0f);
            if (!(slab.exit > impact)) {
                continue;
            }
            if (impact > reach || impact < cellEntry - TIE || impact > cellExit + TIE) {
                continue;
            }
            if (best != null && impact >= best.distance) {
                continue;
            }
            best = new Hit(
                    Kind.HIT,
                    cellX, cellY, cellZ,
                    cell.stateId,
                    cell.revision,
                    part,
                    impact,
                    true,
                    false,
                    slab.normalX,
                    slab.normalY,
                    slab.normalZ,
                    originX + directionX * impact,
                    originY + directionY * impact,
                    originZ + directionZ * impact
            );
        }
        return best;
    }

    private static Slab slab(
            float originX,
            float originY,
            float originZ,
            float directionX,
            float directionY,
            float directionZ,
            Aabb box
    ) {
        float enter = Float.NEGATIVE_INFINITY;
        float exit = Float.POSITIVE_INFINITY;
        int normalX = 0;
        int normalY = 0;
        int normalZ = 0;
        float[] origin = {originX, originY, originZ};
        float[] direction = {directionX, directionY, directionZ};
        float[] min = {box.minX(), box.minY(), box.minZ()};
        float[] max = {box.maxX(), box.maxY(), box.maxZ()};
        for (int axis = 0; axis < 3; axis++) {
            float component = direction[axis];
            if (component == 0f) {
                if (origin[axis] < min[axis] || origin[axis] >= max[axis]) {
                    return null;
                }
                continue;
            }
            float inverse = 1f / component;
            float atMin = (min[axis] - origin[axis]) * inverse;
            float atMax = (max[axis] - origin[axis]) * inverse;
            float near;
            float far;
            int signOnNear;
            if (atMin < atMax) {
                near = atMin;
                far = atMax;
                signOnNear = -1;
            } else if (atMax < atMin) {
                near = atMax;
                far = atMin;
                signOnNear = 1;
            } else {
                return null;
            }
            if (!finite(near) || !finite(far)) {
                return null;
            }
            if (near > enter + TIE) {
                enter = near;
                normalX = axis == 0 ? signOnNear : 0;
                normalY = axis == 1 ? signOnNear : 0;
                normalZ = axis == 2 ? signOnNear : 0;
            } else if (near > enter) {
                enter = near;
            }
            if (far < exit) {
                exit = far;
            }
        }
        return new Slab(enter, exit, normalX, normalY, normalZ);
    }

    private static int startCell(float origin, float direction) {
        if (direction < 0f && finite(origin) && origin == (float) Math.floor(origin)) {
            return (int) Math.floor(origin) - 1;
        }
        return (int) Math.floor(origin);
    }

    private static int sign(float direction) {
        if (direction > 0f) {
            return 1;
        }
        if (direction < 0f) {
            return -1;
        }
        return 0;
    }

    private static float boundaryTime(float origin, int cell, float direction, int step) {
        if (step == 0) {
            return Float.POSITIVE_INFINITY;
        }
        float boundary = step > 0 ? cell + 1f : cell;
        float time = (boundary - origin) / direction;
        if (time < 0f && time > -TIE) {
            return 0f;
        }
        return time;
    }

    private static float minFinite(float x, float y, float z) {
        return Math.min(x, Math.min(y, z));
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static Hit miss() {
        return new Hit(Kind.MISS, 0, 0, 0, 0, 0, -1, 0f, false, false, 0, 0, 0, 0f, 0f, 0f);
    }

    private record Slab(float enter, float exit, int normalX, int normalY, int normalZ) {
    }
}
