package dev.cubicdawn.world;

public record PlayerState(float x,float y,float z,float yaw,float pitch,int slot,
                          int health,float velocityY,float fallPeak) {
    public PlayerState(float x,float y,float z,float yaw,float pitch,int slot) {
        this(x,y,z,yaw,pitch,slot,20,0,y);
    }
    public PlayerState {
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||!Float.isFinite(yaw)||!Float.isFinite(pitch)
                || !Float.isFinite(velocityY)||!Float.isFinite(fallPeak)||fallPeak<y-.001f
                || slot<0||slot>=9||health<0||health>20) throw new IllegalArgumentException("Invalid player state");
    }
}
