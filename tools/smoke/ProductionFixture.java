import dev.cubicdawn.world.*;
import java.nio.file.Path;

/** Overwrites only build/production-smoke.txt. */
public class ProductionFixture {
    public static void main(String[] args) throws Exception {
        BlockWorld world=BlockWorld.empty(); long command=1;
        for(int x=-4;x<=4;x++) for(int z=-4;z<=4;z++) world.placeBlock(command++,x,0,z,BlockIds.DIRT,0,null);
        world.placeBlock(command++,0,1,0,BlockIds.FURNACE,0,null);
        world.placeBlock(command++,2,1,0,BlockIds.CHEST,0,null);
        Inventory bag=new Inventory(); bag.add(BlockIds.SAND,2); bag.add(BlockIds.PLANKS,2);
        bag.add(ItemIds.WOOD_PICKAXE,1); bag.damageTool(2); bag.add(BlockIds.LOG,1);
        SnapshotIO.save(Path.of("build/production-smoke.txt"),world,new PlayerState(0,1.4f,3,0,0,0),bag);
    }
}
