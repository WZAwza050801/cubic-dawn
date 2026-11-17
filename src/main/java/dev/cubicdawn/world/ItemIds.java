package dev.cubicdawn.world;

/** Items that cannot be placed as ordinary blocks. IDs remain stable in snapshot v6. */
public final class ItemIds {
    public static final int STICK = 200;
    public static final int WOOD_PICKAXE = 201;
    public static final int WOOD_AXE = 202;
    public static final int WOOD_SHOVEL = 203;
    public static final int SAPLING = 205;
    public static final int CHARCOAL = 206;
    public static final int COAL=207, RAW_IRON=208, IRON_INGOT=209;
    public static final int STONE_PICKAXE=210, STONE_AXE=211, STONE_SHOVEL=212;
    public static final int IRON_PICKAXE=213, IRON_AXE=214, IRON_SHOVEL=215;
    private ItemIds() { }
    public static boolean tool(int id) { return (id >= WOOD_PICKAXE && id <= WOOD_SHOVEL) || (id>=210 && id<=215); }
    public static int durability(int id) { return tier(id)==3 ? 192 : tier(id)==2 ? 96 : tool(id) ? 32 : 0; }
    public static int tier(int id) { return id>=213 && id<=215 ? 3 : id>=210 && id<=212 ? 2 : id>=201 && id<=203 ? 1 : 0; }
    public static boolean pickaxe(int id) { return id==201 || id==210 || id==213; }
    public static boolean axe(int id) { return id==202 || id==211 || id==214; }
    public static boolean shovel(int id) { return id==203 || id==212 || id==215; }
    public static int stackLimit(int id) { return tool(id) ? 1 : Inventory.STACK_LIMIT; }
}
