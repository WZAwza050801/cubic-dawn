import dev.cubicdawn.world.*;
import java.nio.file.Path;
public class ResourceFixture {
 public static void main(String[] args)throws Exception {
  if(args.length>0 && args[0].equals("upgrade")) {
   BlockWorld old=BlockWorld.generateForestIsland();
   SnapshotIO.save(Path.of("build/ore-upgrade-smoke.txt"),old,new PlayerState(2,2.41f,3,0,0,0),Inventory.starter());return;
  }
  BlockWorld w=BlockWorld.empty();long cmd=1;
  for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)w.placeBlock(cmd++,x,0,z,BlockIds.DIRT,0,null);
  w.placeBlock(cmd++,0,1,1,BlockIds.COAL_ORE,0,null);
  for(int z=0;z>=-2;z--)w.placeBlock(cmd++,0,1,z,BlockIds.IRON_ORE,0,null);
  w.placeBlock(cmd++,2,1,0,BlockIds.FURNACE,0,null);w.placeBlock(cmd++,1,1,2,BlockIds.WORKBENCH,0,null);
  Inventory b=new Inventory();b.add(ItemIds.WOOD_PICKAXE,1);b.add(ItemIds.STONE_PICKAXE,1);b.add(ItemIds.STICK,2);
  SnapshotIO.save(Path.of("build/resource-smoke.txt"),w,new PlayerState(0,1.4f,3,0,0,0),b);
 }
}
