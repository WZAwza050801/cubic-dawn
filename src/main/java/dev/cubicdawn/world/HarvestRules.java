package dev.cubicdawn.world;

/** Ore gates apply only to the new resources; legacy materials remain collectable by hand. */
public final class HarvestRules {
    private HarvestRules() { }
    public static boolean allowed(int block,int held) {
        if(block==BlockIds.COAL_ORE) return ItemIds.pickaxe(held);
        if(block==BlockIds.IRON_ORE) return ItemIds.pickaxe(held) && ItemIds.tier(held)>=2;
        return true;
    }
    public static int drop(int block) {
        return block==BlockIds.COAL_ORE ? ItemIds.COAL : block==BlockIds.IRON_ORE ? ItemIds.RAW_IRON : block;
    }
}
