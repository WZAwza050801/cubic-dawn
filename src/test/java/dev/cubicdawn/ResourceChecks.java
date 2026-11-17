package dev.cubicdawn;
import dev.cubicdawn.world.*;
public final class ResourceChecks {
 static void check(String name,boolean ok){if(!ok)throw new IllegalStateException("FAIL "+name);System.out.println("PASS "+name);}
 public static void run(){
  BlockWorld world=BlockWorld.generateResourceIsland(),other=BlockWorld.generateResourceIsland();
  check("ORE_SEED",world.sameSolids(other)&&world.countSolid()==1806);
  int[] counts={0,0};world.forEachSolid((x,y,z,id)->{if(id==18)counts[0]++;if(id==19)counts[1]++;});
  check("ORE_BOTH",counts[0]>0 && counts[1]>0);check("ORE_IDEMPOTENT",world.seedOreVeins()==0);
  BlockWorld ore=BlockWorld.empty();ore.placeBlock(1,0,0,0,BlockIds.IRON_ORE,0,null);
  Inventory bag=new Inventory();bag.add(ItemIds.WOOD_PICKAXE,1);
  check("ORE_GATE",!PlayerActions.mineBlock(ore,bag,0,2,0,0,0,ore.query(0,0,0).revision()).accepted()
   &&bag.slot(0).durability()==32 && ore.getId(0,0,0)==19);
  bag.remove(0,1);bag.add(ItemIds.STONE_PICKAXE,1);
  check("ORE_DROP",PlayerActions.mineBlock(ore,bag,0,3,0,0,0,ore.query(0,0,0).revision()).accepted()
   && bag.count(ItemIds.RAW_IRON)==1 && bag.count(BlockIds.IRON_ORE)==0 &&bag.slot(0).durability()==95);
  check("ORE_NO_REFILL",ore.seedOreVeins()==0 &&ore.getId(0,0,0)==0);
  Inventory material=new Inventory();material.add(BlockIds.STONE,3);material.add(ItemIds.STICK,8);
  check("STONE_TOOL_CRAFT",material.craft(Inventory.Recipe.STONE_PICKAXE,true)&&material.count(ItemIds.STONE_PICKAXE)==1);
  Inventory input=new Inventory();input.add(ItemIds.RAW_IRON,3);input.add(ItemIds.COAL,1);
  BlockContainer furnace=new BlockContainer(BlockIds.FURNACE);
  for(int i=0;i<3;i++)furnace.deposit(input,0,false);furnace.deposit(input,1,true);
  for(int i=0;i<180;i++)furnace.tick();
  BlockContainer resumed=BlockContainer.restore(furnace.state(0,0,0));
  for(int i=0;i<180;i++){furnace.tick();resumed.tick();}
  check("IRON_SMELT",furnace.slot(2).id()==ItemIds.IRON_INGOT&&furnace.slot(2).count()==3&&furnace.burn()==840);
  check("IRON_PROGRESS_SAVE",furnace.state(0,0,0).equals(resumed.state(0,0,0)));
  furnace.withdraw(material,2);
  check("IRON_TOOL_CRAFT",material.craft(Inventory.Recipe.IRON_PICKAXE,true)&&material.count(ItemIds.IRON_PICKAXE)==1);
  check("TOOL_TIERS",ItemIds.durability(210)==96&&ItemIds.durability(213)==192
   &&MiningProgress.secondsFor(3,213)<MiningProgress.secondsFor(3,210));
  MiningProgress blocked=new MiningProgress();
  BlockWorld target=BlockWorld.empty();target.placeBlock(1,0,0,0,19,0,null);
  var hit=VoxelRay.castFromEngineEye(0,0,2,0,0,-1,5,target);
  check("ORE_PROGRESS_GATE",!blocked.advance(10,true,hit,0,201)&&blocked.fraction()==0);
  Inventory full=new Inventory();full.add(ItemIds.COAL,64);full.add(BlockIds.DIRT,26*64);
  BlockWorld coal=BlockWorld.empty();coal.placeBlock(1,0,0,0,18,0,null);
  full.remove(1,64);full.add(ItemIds.WOOD_PICKAXE,1);
  check("ORE_CAPACITY",!PlayerActions.mineBlock(coal,full,1,2,0,0,0,coal.query(0,0,0).revision()).accepted()
   &&coal.getId(0,0,0)==18&&full.slot(1).durability()==32);

  Inventory upgraded=new Inventory();upgraded.add(ItemIds.IRON_PICKAXE,1);upgraded.damageTool(0);
  var save=SnapshotIO.parse(SnapshotIO.encode(world,null,world.revision(),upgraded));
  check("IRON_WEAR_SAVE",save.inventory().get(0).durability()==191);
  BlockWorld protectedWorld=BlockWorld.generateForestIsland();int[] point={0,0,0};
  world.forEachSolid((x,y,z,id)->{if(id==18){point[0]=x;point[1]=y;point[2]=z;}});
  int x=point[0],y=point[1],z=point[2];
  protectedWorld.breakBlock(55,x,y,z,protectedWorld.query(x,y,z).revision());
  protectedWorld.placeBlock(56,x,y,z,BlockIds.PLANKS,protectedWorld.query(x,y,z).revision(),null);
  protectedWorld.seedOreVeins();
  check("ORE_BUILDING_PROTECTION",protectedWorld.getId(x,y,z)==BlockIds.PLANKS);

 }
}
