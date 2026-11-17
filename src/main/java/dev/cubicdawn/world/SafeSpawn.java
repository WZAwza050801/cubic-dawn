package dev.cubicdawn.world;

import java.util.Optional;

/** Deterministic search, no terrain replacement and no spawning inside unknown cells. */
public final class SafeSpawn {
    public record Point(float x,float y,float z) { }
    private SafeSpawn() { }
    public static Optional<Point> find(BlockWorld world) {
        for(int radius=0;radius<=34;radius++) for(int x=-16;x<=16;x++) for(int z=-16;z<=16;z++) {
            if(Math.max(Math.abs(x-2),Math.abs(z-3))!=radius) continue;
            for(int y=14;y>=-16;y--) {
                CellView floor=world.query(x,y,z);
                if(floor.kind()!=CellKind.KNOWN || floor.id()==BlockIds.AIR) continue;
                float eye=y+1.41f;
                if(!PlayerBody.overlapsSolid(x,eye,z,(bx,by,bz)-> {
                    CellView cell=world.query(bx,by,bz);
                    return cell.kind()!=CellKind.KNOWN || cell.id()!=BlockIds.AIR;
                })) return Optional.of(new Point(x,eye,z));
            }
        }
        return Optional.empty();
    }
    public static boolean clear(BlockWorld world,float x,float y,float z) {
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z) || y < -20 || y>16 || x < -16 || x>16 || z < -16 || z>16) return false;
        return !PlayerBody.overlapsSolid(x,y,z,(bx,by,bz)-> {
            CellView cell=world.query(bx,by,bz);
            return cell.kind()!=CellKind.KNOWN || cell.id()!=BlockIds.AIR;
        });
    }
}
