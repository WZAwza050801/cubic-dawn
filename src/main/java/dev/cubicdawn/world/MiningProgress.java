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
                || !HarvestRules.allowed(hit.stateId(),item) || !Float.isFinite(seconds) || seconds <= 0) {
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
            return ItemIds.axe(item) ? (ItemIds.tier(item)==3 ? .125f : ItemIds.tier(item)==2 ? .2f : .3f) : .9f;
        }
        if (block == BlockIds.COAL_ORE || block == BlockIds.IRON_ORE || block == BlockIds.STONE || block == BlockIds.STONE_BRICKS || block == BlockIds.SANDSTONE) {
            return ItemIds.pickaxe(item) ? (ItemIds.tier(item)==3 ? .25f : ItemIds.tier(item)==2 ? .4f : .6f) : 1.8f;
        }
        if (block == BlockIds.GRASS || block == BlockIds.DIRT || block == BlockIds.SAND) {
            return ItemIds.shovel(item) ? (ItemIds.tier(item)==3 ? .075f : ItemIds.tier(item)==2 ? .1f : .15f) : .4f;
        }
        return 0.5f;
    }
}
