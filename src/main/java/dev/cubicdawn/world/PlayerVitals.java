package dev.cubicdawn.world;

/** Project rules: 20 health, damage above a 3-cell fall, lethal below the finite world. */
public final class PlayerVitals {
    private int health=20;
    private float peak;
    public int health() { return health; }
    public boolean alive() { return health>0; }
    public float peak() { return peak; }
    public void restore(int health,float peak) {
        if(health<0||health>20||!Float.isFinite(peak)) throw new IllegalArgumentException("Invalid vitals");
        this.health=health;this.peak=peak;
    }
    public void respawn(float y) { restore(20,y); }
    public void tick(float y,boolean grounded) {
        if(!alive()) return;
        peak=Math.max(peak,y);
        if(y < -24) { health=0; return; }
        if(grounded) {
            float distance=peak-y;
            if(distance>3.01f) health=Math.max(0,health-(int)Math.ceil(distance-3.01f));
            peak=y;
        }
    }
}
