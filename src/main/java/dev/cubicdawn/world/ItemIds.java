package dev.cubicdawn.world;

/** Items that cannot be placed as ordinary blocks. IDs remain stable in snapshot v6. */
public final class ItemIds {
    public static final int STICK = 200;
    public static final int WOOD_PICKAXE = 201;
    public static final int WOOD_AXE = 202;
    public static final int WOOD_SHOVEL = 203;
    public static final int SAPLING = 205;
    private ItemIds() { }
    public static boolean tool(int id) { return id >= WOOD_PICKAXE && id <= WOOD_SHOVEL; }
    public static int stackLimit(int id) { return tool(id) ? 1 : Inventory.STACK_LIMIT; }
}
