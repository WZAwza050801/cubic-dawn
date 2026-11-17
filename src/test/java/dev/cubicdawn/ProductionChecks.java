package dev.cubicdawn;

import dev.cubicdawn.world.*;

public final class ProductionChecks {
    public static void run() throws Exception { durability(); furnace(); chest(); formatAndPause(); }
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
        var last=new java.util.ArrayList<>(new Inventory().snapshot());
        last.set(0,new Inventory.Stack(ItemIds.WOOD_PICKAXE,1,1)); Inventory finalUse=Inventory.restore(last);
        world.placeBlock(4,1,0,0,BlockIds.STONE,0,null);
        check("DURABILITY_LAST_USE",PlayerActions.mineBlock(world,finalUse,0,5,1,0,0,world.query(1,0,0).revision()).accepted()
                && finalUse.count(ItemIds.WOOD_PICKAXE)==0 && finalUse.count(BlockIds.STONE)==1);

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

    private static void chest() {
        BlockWorld world=BlockWorld.empty(); world.placeBlock(1,0,0,0,BlockIds.CHEST,0,null);
        BlockContainer chest=world.containerAt(0,0,0); Inventory bag=new Inventory();
        bag.add(ItemIds.WOOD_AXE,1); bag.damageTool(0);
        check("CHEST_DEPOSIT",chest.deposit(bag,0,false) && bag.count(ItemIds.WOOD_AXE)==0 && chest.slot(0).durability()==31);
        check("CHEST_BREAK_GUARD",!world.breakBlock(2,0,0,0,world.query(0,0,0).revision()).accepted());
        var snap=SnapshotIO.parse(SnapshotIO.encode(world,null,world.revision(),bag));
        BlockWorld copy=BlockWorld.empty(); copy.applySnapshot(snap);
        check("CHEST_SAVE",copy.containerStates().equals(world.containerStates()));
        Inventory full=new Inventory();full.add(BlockIds.DIRT,27*64);
        check("CHEST_FULL",!chest.withdraw(full,0) && chest.slot(0).durability()==31);
        check("CHEST_WITHDRAW",chest.withdraw(bag,0) && bag.slot(0).durability()==31);
        check("CHEST_EMPTY_BREAK",world.breakBlock(3,0,0,0,world.query(0,0,0).revision()).accepted()
                && world.containerStates().isEmpty());
        Inventory sourceFull=new Inventory(); sourceFull.add(BlockIds.DIRT,27*64);
        BlockContainer packed=new BlockContainer(BlockIds.CHEST);
        for(int i=0;i<27;i++) packed.deposit(sourceFull,i,false);
        sourceFull.add(BlockIds.STONE,1);
        check("CHEST_FULL_DEPOSIT",!packed.deposit(sourceFull,0,false) && sourceFull.count(BlockIds.STONE)==1);
        // Independent capacity/merge check through ordinary transfers.
        BlockContainer merge=new BlockContainer(BlockIds.CHEST); Inventory source=new Inventory();
        source.add(BlockIds.SAND,40); merge.deposit(source,0,false); source.add(BlockIds.SAND,30); merge.deposit(source,0,false);
        check("CHEST_MERGE",merge.slot(0).count()==64 && merge.slot(1).count()==6);
    }

    private static String checked(String canonical) throws Exception {
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        int end=canonical.indexOf('\n');
        return canonical.substring(0,end+1)+"checksum sha256 "+digest+"\n"+canonical.substring(end+1);
    }
    private static void formatAndPause() throws Exception {
        String old="cubic-dawn-blocks 6\ntick 0\n0 0 0 15\n";
        for(int i=0;i<27;i++) old+="item "+i+" "+(i==0 ? "201 1" : "0 0")+"\n";
        var migrated=SnapshotIO.parse(checked(old)); BlockWorld loaded=BlockWorld.empty(); loaded.applySnapshot(migrated);
        check("V6_TOOL_CONTAINER_MIGRATE",migrated.inventory().get(0).durability()==32
                && loaded.containerAt(0,0,0).empty());
        String version7=old.replace("blocks 6","blocks 7");
        var lines=new java.util.ArrayList<String>();
        for(String line : version7.split("\n")) lines.add(line.startsWith("item ") ? line+(line.startsWith("item 0 ") ? " 7" : " 0") : line);
        check("V7_WEAR_MIGRATE",SnapshotIO.parse(checked(String.join("\n",lines)+"\n")).inventory().get(0).durability()==7);
        Inventory bag=new Inventory();bag.add(BlockIds.SAND,1);bag.add(BlockIds.PLANKS,1);
        BlockContainer furnace=loaded.containerAt(0,0,0);furnace.deposit(bag,0,false);furnace.deposit(bag,1,true);
        for(int i=0;i<30;i++) loaded.tick(null);
        var before=loaded.containerStates(); FixedStep clock=new FixedStep();
        clock.advance(3600,false,false,(seconds,jump)->loaded.tick(null));
        check("FURNACE_PAUSE",before.equals(loaded.containerStates()));
        loaded.setSimulationLoaded(0,0,0,false);
        for(int i=0;i<30;i++) loaded.tick(null);
        check("FURNACE_UNLOADED",before.equals(loaded.containerStates()));
        loaded.setSimulationLoaded(0,0,0,true);
        String text=SnapshotIO.encode(loaded,null,loaded.revision(),bag);
        String canonical=text.lines().filter(l->!l.startsWith("checksum ")).collect(java.util.stream.Collectors.joining("\n"))+"\n";
        String box=canonical.lines().filter(l->l.startsWith("box ")).findFirst().orElseThrow();
        boolean rejected=true;
        for(String invalid : new String[]{canonical.replace(box+"\n",""),canonical+box+"\n",
                canonical.replace("box 0 0 0 15","box 1 0 0 15"),
                canonical.replace(" 15 270 30 "," 15 -1 30 "),
                canonical.replace(" 15 270 30 "," 15 270 120 ")}) {
            try { SnapshotIO.parse(checked(invalid)); rejected=false; } catch(IllegalArgumentException|IllegalStateException expected) { }
        }
        check("CONTAINER_INVALID",rejected);
        boolean checksum=false;
        try { SnapshotIO.parse(text.replace(" 15 270 30 "," 15 270 31 ")); }
        catch(IllegalStateException expected){checksum=expected.getMessage().contains("checksum");}
        check("CONTAINER_CHECKSUM",checksum);
        java.nio.file.Path file=java.nio.file.Files.createTempDirectory("cubic-production").resolve("world.txt");
        SnapshotIO.save(file,loaded,null,bag);loaded.tick(null);SnapshotIO.save(file,loaded,null,bag);
        java.nio.file.Files.delete(file);var backup=SnapshotIO.load(file);
        check("CONTAINER_BACKUP",backup.restoredFromBackup() && backup.containers().equals(before));
    }

}
