package dev.cubicdawn.world;

/** Simulation-time mining. A target revision or held-slot change always starts a fresh action. */
public final class MiningProgress {
    private VoxelRay.Hit target;
    private int slot, tool;
    private float elapsed;
    private float required = 1;
    public void reset() { target = null; elapsed = 0; }
    public float fraction() { return target == null ? 0 : Math.min(1, elapsed / required); }

    public boolean advance(float seconds, boolean held, VoxelRay.Hit hit, int selectedSlot, int item) {
        if (!held || hit.kind() != VoxelRay.Kind.HIT || hit.distance() > 5
                || !Float.isFinite(seconds) || seconds <= 0) {
            reset(); return false;
        }
        if (target == null || target.x() != hit.x() || target.y() != hit.y() || target.z() != hit.z()
                || target.revision() != hit.revision() || target.stateId() != hit.stateId()
                || slot != selectedSlot || tool != item) {
            target = hit; slot = selectedSlot; tool = item; elapsed = 0;
            required = secondsFor(hit.stateId(), item);
        }
        elapsed += seconds;
        if (elapsed + 0.000001f < required) return false;
        reset(); return true;
    }
    public static float secondsFor(int block, int item) {
        if (block == BlockIds.LEAVES) return 0.15f;
        if (block == BlockIds.LOG || block == BlockIds.PLANKS || block == BlockIds.WORKBENCH) {
            return item == ItemIds.WOOD_AXE ? 0.3f : 0.9f;
        }
        if (block == BlockIds.STONE || block == BlockIds.STONE_BRICKS || block == BlockIds.SANDSTONE) {
            return item == ItemIds.WOOD_PICKAXE ? 0.6f : 1.8f;
        }
        if (block == BlockIds.GRASS || block == BlockIds.DIRT || block == BlockIds.SAND) {
            return item == ItemIds.WOOD_SHOVEL ? 0.15f : 0.4f;
        }
        return 0.5f;
    }
}
