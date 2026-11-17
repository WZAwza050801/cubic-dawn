import dev.cubicdawn.world.*;
import java.nio.file.Path;
public class SurvivalFixture {
 public static void main(String[] args) throws Exception {
  BlockWorld world=BlockWorld.generateForestIsland();
  world.placeBlock(1,-2,BlockWorld.surface(-2,0)+1,0,BlockIds.CHEST,0,null);
  Inventory storage=new Inventory();storage.add(BlockIds.GLASS,3);
  world.containerAt(-2,BlockWorld.surface(-2,0)+1,0).deposit(storage,0,false);
  var slots=new java.util.ArrayList<>(new Inventory().snapshot());
  slots.set(0,new Inventory.Stack(ItemIds.WOOD_PICKAXE,1,7));slots.set(1,new Inventory.Stack(BlockIds.DIRT,5));
  SnapshotIO.save(Path.of("build/survival-smoke.txt"),world,new PlayerState(2,12,3,0,0,0,2,-1,12),Inventory.restore(slots));
 }
}
