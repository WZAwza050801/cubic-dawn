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
        PlayerVitals vitals=new PlayerVitals();vitals.respawn(10);
        vitals.tick(1.41f,true);
        check("FALL_DAMAGE",vitals.health()==14);
        vitals.tick(1.41f,true);check("FALL_ONCE",vitals.health()==14);
        vitals.respawn(1.41f);vitals.tick(2.6f,false);vitals.tick(1.41f,true);
        check("FALL_JUMP_SAFE",vitals.health()==20);
        vitals.tick(-25,false);check("VOID_LETHAL",!vitals.alive());
        PlayerState falling=new PlayerState(2,5,3,0,0,0,14,-8,10);
        var parsed=SnapshotIO.parse(SnapshotIO.encode(world,falling,world.revision(),Inventory.starter()));
        check("VITALS_SAVE",parsed.player().equals(falling));
        PlayerVitals resumed=new PlayerVitals();resumed.restore(parsed.player().health(),parsed.player().fallPeak());
        resumed.tick(1.41f,true);check("FALL_RESTORE",resumed.health()==8);
        int completions=-1;boolean equal=true;
        for(int fps:new int[]{30,60,120,144}) {
            PlayerVitals v=new PlayerVitals();v.respawn(10);
            PlayerMotion.Pose pose=new PlayerMotion.Pose(0,10,0,0);FixedStep clock=new FixedStep();
            for(int f=0;f<fps*3;f++) clock.advance(1.0/fps,true,false,(dt,j)-> {
                PlayerMotion.step(pose,dt,0,0,false,(x,y,z)->y<=0);
                v.tick(pose.y,PlayerBody.onGround(pose.x,pose.y,pose.z,(x,y,z)->y<=0));
            });
            if(completions<0) completions=v.health();equal&=completions==v.health();
        }
        check("FALL_FRAMERATES",equal && completions==14);
        boolean rejected=false;
        try {new PlayerState(0,2,0,0,0,0,21,0,2);}catch(IllegalArgumentException expected){rejected=true;}
        check("VITALS_INVALID",rejected);

    }
}
