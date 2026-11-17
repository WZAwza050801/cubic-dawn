package dev.cubicdawn.world;

/** Single-threaded player transactions: rejected world edits never consume or grant items. */
public final class PlayerActions {
    private PlayerActions() { }
    public static EditReceipt breakBlock(BlockWorld world, Inventory inventory, long command,
                                         int x, int y, int z, int expectedRevision) {
        return harvest(world,inventory,0,command,x,y,z,expectedRevision);
    }
    private static EditReceipt harvest(BlockWorld world,Inventory inventory,int held,long command,
                                       int x,int y,int z,int expectedRevision) {
        CellView cell = world.query(x, y, z);
        if(!HarvestRules.allowed(cell.id(),held)) return EditReceipt.reject("need_correct_pickaxe");
        int drop=HarvestRules.drop(cell.id());
        if (cell.kind() == CellKind.KNOWN && cell.id() != BlockIds.AIR
                && !inventory.canAdd(drop, 1)) return EditReceipt.reject("inventory_full");
        EditReceipt result = world.breakBlock(command, x, y, z, expectedRevision);
        if (result.accepted()) inventory.add(drop, 1);
        return result;
    }
    public static EditReceipt mineBlock(BlockWorld world, Inventory inventory, int toolSlot, long command,
                                        int x, int y, int z, int expectedRevision) {
        EditReceipt result = harvest(world, inventory, inventory.slot(toolSlot).id(), command, x, y, z, expectedRevision);
        if (result.accepted()) inventory.damageTool(toolSlot);
        return result;
    }
    public static EditReceipt placeBlock(BlockWorld world, Inventory inventory, int slot, long command,
                                         int x, int y, int z, int expectedRevision, PlayerBody.SolidAt body) {
        Inventory.Stack stack = inventory.slot(slot);
        if (stack.count() == 0) return EditReceipt.reject("no_items");
        EditReceipt result;
        if (stack.id() == ItemIds.SAPLING) {
            if (world.query(x, y, z).revision() != expectedRevision) return EditReceipt.reject("stale");
            result = world.plantTree(command, x, y, z, body);
        } else result = world.placeBlock(command, x, y, z, stack.id(), expectedRevision, body);
        if (result.accepted()) inventory.remove(slot, 1);
        return result;
    }
}
