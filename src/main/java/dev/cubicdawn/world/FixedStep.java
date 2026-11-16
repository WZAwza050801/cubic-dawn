package dev.cubicdawn.world;

/** Render time is admitted in bounded batches; paused time never becomes simulation debt. */
public final class FixedStep {
    public static final double SECONDS = 1.0 / 60.0;
    public static final int MAX_STEPS = 3;
    private static final double EPSILON = 1e-10;
    private double remainder;
    private boolean pendingJump;

    @FunctionalInterface
    public interface Tick {
        void run(float seconds, boolean jump);
    }

    public int advance(double elapsed, boolean active, boolean jumpEdge, Tick tick) {
        if (!active) {
            reset();
            return 0;
        }
        pendingJump |= jumpEdge;
        if (Double.isFinite(elapsed) && elapsed > 0) {
            remainder += Math.min(elapsed, SECONDS * MAX_STEPS);
        }
        int count = 0;
        while (count < MAX_STEPS && remainder + EPSILON >= SECONDS) {
            tick.run((float) SECONDS, pendingJump);
            pendingJump = false;
            remainder = Math.max(0, remainder - SECONDS);
            count++;
        }
        return count;
    }

    public void reset() {
        remainder = 0;
        pendingJump = false;
    }
}
