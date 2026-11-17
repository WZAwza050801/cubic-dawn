package dev.cubicdawn;

import dev.cubicdawn.world.*;

public final class ProductionChecks {
    public static void run() throws Exception { durability(); furnace(); }
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
    private static void furnace() throws Exception {
        BlockWorld world=BlockWorld.empty(); world.placeBlock(1,0,0,0,BlockIds.FURNACE,0,null);
        BlockContainer oven=world.containerAt(0,0,0); Inventory bag=new Inventory();
        bag.add(BlockIds.SAND,2); bag.add(BlockIds.PLANKS,1);
        oven.deposit(bag,0,false); oven.deposit(bag,0,false); oven.deposit(bag,1,true);
        for(int i=0;i<60;i++) world.tick(null);
        check("FURNACE_HALF", oven.progress()==60 && oven.burn()==240 && oven.slot(2).count()==0);
        var saved=SnapshotIO.parse(SnapshotIO.encode(world,null,world.revision(),bag));
        BlockWorld resumed=BlockWorld.empty(); resumed.applySnapshot(saved);
        for(int i=0;i<60;i++){ world.tick(null); resumed.tick(null); }
        check("FURNACE_RESTORE", world.containerStates().equals(resumed.containerStates())
                && oven.slot(2).id()==BlockIds.GLASS && oven.slot(2).count()==1 && oven.slot(0).count()==1);
        check("FURNACE_BREAK_GUARD", !world.breakBlock(2,0,0,0,world.query(0,0,0).revision()).accepted());
        for(int i=0;i<120;i++) world.tick(null);
        check("FURNACE_TWO", oven.slot(2).count()==2 && oven.slot(0).count()==0 && oven.burn()==60);
        Inventory full=new Inventory(); full.add(BlockIds.DIRT,27*64);
        check("FURNACE_OUTPUT_FULL_BAG", !oven.withdraw(full,2) && oven.slot(2).count()==2);
        check("FURNACE_COLLECT", oven.withdraw(bag,2) && bag.count(BlockIds.GLASS)==2);
        for(int i=0;i<60;i++) world.tick(null);
        check("FURNACE_EMPTY_BREAK", world.breakBlock(3,0,0,0,world.query(0,0,0).revision()).accepted()
                && world.containerStates().isEmpty());
        BlockContainer charcoal=new BlockContainer(BlockIds.FURNACE); Inventory wood=new Inventory();
        wood.add(BlockIds.LOG,1); wood.add(BlockIds.PLANKS,1);
        charcoal.deposit(wood,0,false); charcoal.deposit(wood,1,true);
        for(int i=0;i<120;i++) charcoal.tick();
        check("FURNACE_CHARCOAL", charcoal.slot(2).id()==ItemIds.CHARCOAL);
        check("FURNACE_REJECT_INPUT", !charcoal.deposit(full,0,false) && full.count(BlockIds.DIRT)==27*64);
        for(int fps : new int[]{30,60,120,144}) {
            BlockContainer machine=new BlockContainer(BlockIds.FURNACE); Inventory input=new Inventory();
            input.add(BlockIds.SAND,1); input.add(BlockIds.PLANKS,1);
            machine.deposit(input,0,false); machine.deposit(input,1,true);
            FixedStep clock=new FixedStep();
            for(int f=0;f<fps*2;f++) clock.advance(1.0/fps,true,false,(s,j)->machine.tick());
            check("FURNACE_RATE_"+fps,machine.slot(2).count()==1 && machine.burn()==180);
        }
        BlockContainer blocked=BlockContainer.restore(new BlockContainer.State(0,0,0,BlockIds.FURNACE,0,0,
                java.util.List.of(new Inventory.Stack(BlockIds.SAND,1),new Inventory.Stack(BlockIds.PLANKS,1),new Inventory.Stack(BlockIds.GLASS,64))));
        for(int i=0;i<200;i++) blocked.tick();
        check("FURNACE_BLOCKED", blocked.slot(1).count()==1 && blocked.burn()==0 && blocked.progress()==0);
    }

}
