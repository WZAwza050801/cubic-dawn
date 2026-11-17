package dev.cubicdawn;
import dev.cubicdawn.world.*;

public final class SurvivalChecks {
    private static void check(String label,boolean ok) {
        if(!ok) throw new IllegalStateException("FAIL "+label);
        System.out.println("PASS "+label);
    }
    public static void run() {
        BlockWorld world=BlockWorld.generateForestIsland(); var first=SafeSpawn.find(world).orElseThrow();
        check("SPAWN_CLEAR",SafeSpawn.clear(world,first.x(),first.y(),first.z()));
        check("SPAWN_DETERMINISTIC",SafeSpawn.find(world).orElseThrow().equals(first));
        BlockWorld empty=BlockWorld.empty(); int before=empty.mutations();
        check("SPAWN_EMPTY",SafeSpawn.find(empty).isEmpty() && empty.mutations()==before);
        empty.placeBlock(1,2,0,3,BlockIds.STONE,0,null);empty.setSimulationLoaded(2,1,3,false);
        check("SPAWN_UNKNOWN",SafeSpawn.find(empty).isEmpty());
        empty.setSimulationLoaded(2,1,3,true);
        check("SPAWN_SUPPORTED",SafeSpawn.find(empty).isPresent());
        check("SPAWN_INVALID_POSITION",!SafeSpawn.clear(world,100,100,100) && !SafeSpawn.clear(world,0,-30,0));
    }
}
