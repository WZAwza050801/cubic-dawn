package dev.cubicdawn.world;

import java.util.HashSet;

/**
 * 固定范围的方块数组。范围外是世界之外，不是空气。
 * 未加载是单独标记，读取它不会把那一格写成空气，也不会创建新区域。
 */
public final class BlockWorld implements VoxelRay.WorldProbe {

    public static final int BLOCK_MIN = -16;
    public static final int BLOCK_MAX = 16;
    public static final int SEED = 20261116;
    private static final int TERRAIN_MIN = -10;
    private static final int TERRAIN_MAX = 10;
    private static final int COLUMN_THICKNESS = 4;
    private static final int SPAN = BLOCK_MAX - BLOCK_MIN + 1;

    private final int[] ids = new int[SPAN * SPAN * SPAN];
    private final int[] revisions = new int[SPAN * SPAN * SPAN];
    private final boolean[] unloaded = new boolean[SPAN * SPAN * SPAN];
    private final HashSet<Long> acceptedCommands = new HashSet<>();
    private final BlockTicks blockTicks = new BlockTicks();
    private long revision;
    private boolean dirty;
    private int mutations;

    public static BlockWorld empty() {
        return new BlockWorld();
    }

    public static BlockWorld generateIsland() {
        BlockWorld world = new BlockWorld();
        for (int x = TERRAIN_MIN; x <= TERRAIN_MAX; x++) {
            for (int z = TERRAIN_MIN; z <= TERRAIN_MAX; z++) {
                int top = surface(x, z);
                int bottom = top - (COLUMN_THICKNESS - 1);
                for (int y = bottom; y <= top; y++) {
                    int id = BlockIds.STONE;
                    if (y == top) {
                        id = BlockIds.GRASS;
                    } else if (y == top - 1) {
                        id = BlockIds.DIRT;
                    }
                    world.put(x, y, z, id, 1);
                }
            }
        }
        world.revision = 1L;
        world.dirty = true;
        return world;
    }

    public static BlockWorld generateForestIsland() {
        BlockWorld world = generateIsland();
        world.plantTree(-1, -5, surface(-5, -4) + 1, -4, null);
        world.plantTree(-2, 5, surface(5, -5) + 1, -5, null);
        return world;
    }

    /** Validate the complete footprint before changing anything; never overwrite buildings. */
    public EditReceipt plantTree(long command, int x, int y, int z, PlayerBody.SolidAt body) {
        if (acceptedCommands.contains(command)) return EditReceipt.reject("duplicate");
        CellView ground = query(x, y - 1, z);
        if (ground.kind() != CellKind.KNOWN || (ground.id() != BlockIds.GRASS && ground.id() != BlockIds.DIRT)) {
            return EditReceipt.reject("tree_needs_soil");
        }
        java.util.List<int[]> cells = new java.util.ArrayList<>();
        for (int dy = 0; dy < 3; dy++) cells.add(new int[] {x, y + dy, z, BlockIds.LOG});
        for (int dy = 2; dy <= 3; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dy == 2 && dx == 0 && dz == 0) continue;
            cells.add(new int[] {x + dx, y + dy, z + dz, BlockIds.LEAVES});
        }
        cells.add(new int[] {x, y + 4, z, BlockIds.LEAVES});
        for (int[] cell : cells) {
            CellView view = query(cell[0], cell[1], cell[2]);
            if (view.kind() != CellKind.KNOWN || view.id() != BlockIds.AIR) return EditReceipt.reject("tree_needs_space");
            if (body != null && body.solid(cell[0], cell[1], cell[2])) return EditReceipt.reject("player_overlap");
        }
        for (int[] cell : cells) {
            CellView view = query(cell[0], cell[1], cell[2]);
            put(cell[0], cell[1], cell[2], cell[3], view.revision() + 1);
        }
        revision++;
        dirty = true;
        acceptedCommands.add(command);
        return EditReceipt.ok(revision);
    }

    public void checkGeneratedIsland() {
        if (countSolid() != 21 * 21 * COLUMN_THICKNESS) {
            throw new IllegalStateException("Expected 1764 generated blocks.");
        }
        if (surface(0, 0) != 2 || surface(2, 3) != 1 || surface(10, 10) != 0) {
            throw new IllegalStateException("Surface heights drifted from the seed.");
        }
        if (fade(0f) != 0f || fade(1f) != 1f || fade(0.5f) != 0.5f) {
            throw new IllegalStateException("Smoothstep is wrong at 0, 1/2, or 1.");
        }
        int top = surface(2, 3);
        if (getId(2, top, 3) != BlockIds.GRASS || getId(2, top - 1, 3) != BlockIds.DIRT) {
            throw new IllegalStateException("The surface is not grass over dirt.");
        }
        if (getId(2, top - 2, 3) != BlockIds.STONE || getId(2, top + 1, 3) != BlockIds.AIR) {
            throw new IllegalStateException("The column is not stone under the dirt, or air above the grass.");
        }
        if (getId(2, top - (COLUMN_THICKNESS - 1), 3) == BlockIds.AIR
                || getId(2, top - COLUMN_THICKNESS, 3) != BlockIds.AIR) {
            throw new IllegalStateException("The column thickness is not 4.");
        }
        if (getId(11, 0, 0) != BlockIds.AIR) {
            throw new IllegalStateException("Terrain spilled past the island.");
        }
    }

    public CellView query(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return CellView.outside();
        }
        int index = index(x, y, z);
        if (unloaded[index]) {
            return CellView.unloaded();
        }
        return CellView.known(ids[index], revisions[index]);
    }

    @Override
    public Cell inspect(int x, int y, int z) {
        CellView view = query(x, y, z);
        if (view.kind() == CellKind.UNLOADED) {
            return Cell.unloaded();
        }
        if (view.kind() != CellKind.KNOWN || view.id() == BlockIds.AIR) {
            return Cell.pass();
        }
        return Cell.shape(view.id(), view.revision(), Aabb.unitCell(x, y, z));
    }

    /** 渲染用。世界之外和未加载都不画，调用方不能据此把它们当成空气去放置。 */
    public int getId(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return BlockIds.AIR;
        }
        int index = index(x, y, z);
        if (unloaded[index]) {
            return BlockIds.AIR;
        }
        return ids[index];
    }

    public boolean isSolidAt(int x, int y, int z) {
        CellView view = query(x, y, z);
        if (view.kind() == CellKind.OUTSIDE) {
            return false;
        }
        if (view.kind() == CellKind.UNLOADED) {
            return true;
        }
        return view.id() != BlockIds.AIR;
    }

    public void setSimulationLoaded(int x, int y, int z, boolean loaded) {
        if (!contains(x, y, z)) {
            throw new IllegalArgumentException("Cannot mark a cell outside the world.");
        }
        unloaded[index(x, y, z)] = !loaded;
    }

    public EditReceipt breakBlock(long commandId, int x, int y, int z, int expectedRevision) {
        if (acceptedCommands.contains(commandId)) {
            return EditReceipt.reject("duplicate");
        }
        CellView view = query(x, y, z);
        if (view.kind() == CellKind.OUTSIDE) {
            return EditReceipt.reject("outside");
        }
        if (view.kind() == CellKind.UNLOADED) {
            return EditReceipt.reject("unloaded");
        }
        if (view.revision() != expectedRevision) {
            return EditReceipt.reject("stale");
        }
        if (view.id() == BlockIds.AIR) {
            return EditReceipt.reject("not_solid");
        }
        blockTicks.cancel(x, y, z);
        put(x, y, z, BlockIds.AIR, view.revision() + 1);
        scheduleSand(x, y + 1, z);
        acceptedCommands.add(commandId);
        revision++;
        dirty = true;
        return EditReceipt.ok(revision);
    }

    public EditReceipt placeBlock(
            long commandId,
            int x,
            int y,
            int z,
            int id,
            int expectedRevision,
            PlayerBody.SolidAt body
    ) {
        if (acceptedCommands.contains(commandId)) {
            return EditReceipt.reject("duplicate");
        }
        CellView view = query(x, y, z);
        if (view.kind() == CellKind.OUTSIDE) {
            return EditReceipt.reject("outside");
        }
        if (view.kind() == CellKind.UNLOADED) {
            return EditReceipt.reject("unloaded");
        }
        if (view.revision() != expectedRevision) {
            return EditReceipt.reject("stale");
        }
        if (!BlockIds.placeable(id)) {
            return EditReceipt.reject("not_placeable");
        }
        if (!BlockIds.replaceable(view.id())) {
            return EditReceipt.reject("not_replaceable");
        }
        if (BlockIds.needsSupportBelow(id) && !supportedFromBelow(x, y, z)) {
            return EditReceipt.reject("unsupported");
        }
        if (body != null && body.solid(x, y, z)) {
            return EditReceipt.reject("player_overlap");
        }
        blockTicks.cancel(x, y, z);
        put(x, y, z, id, view.revision() + 1);
        scheduleSand(x, y, z);
        scheduleSand(x, y + 1, z);
        acceptedCommands.add(commandId);
        revision++;
        dirty = true;
        return EditReceipt.ok(revision);
    }

    public boolean supportedFromBelow(int x, int y, int z) {
        CellView below = query(x, y - 1, z);
        return below.kind() == CellKind.KNOWN && below.id() != BlockIds.AIR;
    }

    public void applySnapshot(SnapshotIO.Snapshot snapshot) {
        blockTicks.restore(snapshot.simulationTick(), snapshot.tasks());
        for (int i = 0; i < ids.length; i++) {
            ids[i] = BlockIds.AIR;
            revisions[i] = 0;
            unloaded[i] = false;
        }
        mutations++;
        for (int[] block : snapshot.blocks()) {
            put(block[0], block[1], block[2], block[3], 1);
        }
        revision = snapshot.revision() == null ? 1L : snapshot.revision();
        dirty = false;
        acceptedCommands.clear();
    }

    public BlockTicks blockTicks() { return blockTicks; }

    private void scheduleSand(int x, int y, int z) {
        CellView cell = query(x, y, z);
        if (cell.kind() == CellKind.KNOWN && cell.id() == BlockIds.SAND) {
            blockTicks.schedule(x, y, z);
        }
    }

    /** One atomic cell move. Unknown space and the player's body block falling. */
    public void tick(PlayerBody.SolidAt body) {
        for (BlockTicks.Task task : blockTicks.advance()) {
            int x = task.x(), y = task.y(), z = task.z();
            CellView source = query(x, y, z);
            CellView below = query(x, y - 1, z);
            if (source.kind() == CellKind.UNLOADED) {
                blockTicks.schedule(x, y, z);
                continue;
            }
            if (source.kind() != CellKind.KNOWN || source.id() != BlockIds.SAND) continue;
            if (below.kind() == CellKind.UNLOADED
                    || (below.kind() == CellKind.KNOWN && below.id() == BlockIds.AIR
                        && body != null && body.solid(x, y - 1, z))) {
                blockTicks.schedule(x, y, z);
                continue;
            }
            if (below.kind() != CellKind.KNOWN || below.id() != BlockIds.AIR) continue;
            put(x, y, z, BlockIds.AIR, source.revision() + 1);
            put(x, y - 1, z, BlockIds.SAND, below.revision() + 1);
            revision++;
            dirty = true;
            scheduleSand(x, y - 1, z);
            scheduleSand(x, y + 1, z);
        }
    }

    public int countSolid() {
        int count = 0;
        for (int id : ids) {
            if (id != BlockIds.AIR) {
                count++;
            }
        }
        return count;
    }

    public int mutations() {
        return mutations;
    }

    public long revision() {
        return revision;
    }

    public boolean dirty() {
        return dirty;
    }

    public void markCleanIfUnchanged(long savedRevision) {
        if (revision == savedRevision) {
            dirty = false;
        }
    }

    public boolean sameSolids(BlockWorld other) {
        for (int y = BLOCK_MIN; y <= BLOCK_MAX; y++) {
            for (int z = BLOCK_MIN; z <= BLOCK_MAX; z++) {
                for (int x = BLOCK_MIN; x <= BLOCK_MAX; x++) {
                    if (stored(x, y, z) != other.stored(x, y, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void forEachSolid(SolidConsumer consumer) {
        for (int y = BLOCK_MIN; y <= BLOCK_MAX; y++) {
            for (int z = BLOCK_MIN; z <= BLOCK_MAX; z++) {
                for (int x = BLOCK_MIN; x <= BLOCK_MAX; x++) {
                    int id = stored(x, y, z);
                    if (id != BlockIds.AIR) {
                        consumer.accept(x, y, z, id);
                    }
                }
            }
        }
    }

    private int stored(int x, int y, int z) {
        return ids[index(x, y, z)];
    }

    public int highestSolid(int x, int z) {
        for (int y = BLOCK_MAX; y >= BLOCK_MIN; y--) {
            if (isSolidAt(x, y, z)) {
                return y;
            }
        }
        return 0;
    }

    public interface SolidConsumer {
        void accept(int x, int y, int z, int id);
    }

    private void put(int x, int y, int z, int id, int cellRevision) {
        int index = index(x, y, z);
        ids[index] = id;
        revisions[index] = cellRevision;
        mutations++;
    }

    private static boolean contains(int x, int y, int z) {
        return x >= BLOCK_MIN && x <= BLOCK_MAX
                && y >= BLOCK_MIN && y <= BLOCK_MAX
                && z >= BLOCK_MIN && z <= BLOCK_MAX;
    }

    private static int index(int x, int y, int z) {
        return ((y - BLOCK_MIN) * SPAN + (z - BLOCK_MIN)) * SPAN + (x - BLOCK_MIN);
    }

    /** 种子定死之后，同一对整数永远得到同一个 32 位散列。 */
    private static int hash(int x, int z) {
        int n = x * 374761393 + z * 668265263 + SEED * 1442695041;
        n = (n ^ (n >>> 13)) * 1274126177;
        return n ^ (n >>> 16);
    }

    private static float fade(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float lattice(int p, int q) {
        return (hash(p, q) & 255) / 255f;
    }

    private static float heightNoise(int x, int z) {
        float gridX = x / 4f;
        float gridZ = z / 4f;
        int p = (int) Math.floor(gridX);
        int q = (int) Math.floor(gridZ);
        float fractionX = gridX - p;
        float fractionZ = gridZ - q;
        float sx = fade(fractionX);
        float sz = fade(fractionZ);
        float n00 = lattice(p, q);
        float n10 = lattice(p + 1, q);
        float n01 = lattice(p, q + 1);
        float n11 = lattice(p + 1, q + 1);
        float alongX0 = n00 + (n10 - n00) * sx;
        float alongX1 = n01 + (n11 - n01) * sx;
        return alongX0 + (alongX1 - alongX0) * sz;
    }

    public static int surface(int x, int z) {
        return (int) Math.floor(heightNoise(x, z) * 4f);
    }
}
