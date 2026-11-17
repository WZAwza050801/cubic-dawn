import dev.cubicdawn.world.*;
import java.nio.file.Path;
public class ToolsFixture {
 public static void main(String[] args) throws Exception {
  BlockWorld world=BlockWorld.empty(); long command=1;
  for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) world.placeBlock(command++,x,0,z,BlockIds.DIRT,0,null);
  world.placeBlock(command++,0,1,0,BlockIds.STONE,0,null);
  world.placeBlock(command++,0,1,1,BlockIds.LOG,0,null);
  Inventory bag=new Inventory(); bag.add(BlockIds.LOG,8); bag.add(BlockIds.DIRT,4);
  SnapshotIO.save(Path.of("build/tools-smoke.txt"),world,new PlayerState(0,1.4f,3,0,0,0),bag);
 }
}
