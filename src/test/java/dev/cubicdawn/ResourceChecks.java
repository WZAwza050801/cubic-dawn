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
 }
}
