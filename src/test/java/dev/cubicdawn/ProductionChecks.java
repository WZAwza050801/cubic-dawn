package dev.cubicdawn;

import dev.cubicdawn.world.*;

public final class ProductionChecks {
    public static void run() throws Exception { durability(); }
    private static void check(String name, boolean condition) {
        if (!condition) throw new IllegalStateException("FAIL " + name);
        System.out.println("PASS " + name);
    }
    private static void durability() {
        Inventory bag = new Inventory(); bag.add(ItemIds.WOOD_PICKAXE, 1);
        BlockWorld world = BlockWorld.empty();
        world.placeBlock(1,0,0,0,BlockIds.STONE,0,null);
        check("DURABILITY_FAIL", !PlayerActions.mineBlock(world,bag,0,2,0,0,0,99).accepted()
                && bag.slot(0).durability()==32);
        check("DURABILITY_USE", PlayerActions.mineBlock(world,bag,0,3,0,0,0,world.query(0,0,0).revision()).accepted()
                && bag.slot(0).durability()==31 && bag.count(BlockIds.STONE)==1);
        Inventory stored = new Inventory(); bag.transferTo(stored,0);
        check("DURABILITY_TRANSFER", stored.slot(0).durability()==31 && bag.count(ItemIds.WOOD_PICKAXE)==0);
        var snapshot = SnapshotIO.parse(SnapshotIO.encode(world,null,world.revision(),stored));
        check("DURABILITY_SAVE", snapshot.inventory().get(0).durability()==31);
        for(int i=0;i<31;i++) stored.damageTool(0);
        check("DURABILITY_BREAK", stored.slot(0).count()==0);
        boolean rejected=false;
        try { new Inventory.Stack(ItemIds.WOOD_AXE,1,0); } catch(IllegalArgumentException expected){ rejected=true; }
        check("DURABILITY_INVALID", rejected);
    }
}
