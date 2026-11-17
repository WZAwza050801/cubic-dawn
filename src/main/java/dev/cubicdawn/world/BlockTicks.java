package dev.cubicdawn.world;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persistent simulation-time work, deduplicated by cell and ordered independently of hash iteration. */
public final class BlockTicks {
    public static final int FALL_DELAY = 6;
    public static final int MAX_PER_TICK = 256;
    public record Task(long due, int x, int y, int z) {
        public Task {
            if (due < 0 || x < BlockWorld.BLOCK_MIN || x > BlockWorld.BLOCK_MAX
                    || y < BlockWorld.BLOCK_MIN || y > BlockWorld.BLOCK_MAX
                    || z < BlockWorld.BLOCK_MIN || z > BlockWorld.BLOCK_MAX) {
                throw new IllegalArgumentException("Invalid scheduled block tick");
            }
        }
    }
    private record Position(int x, int y, int z) {}
    private static final Comparator<Task> ORDER = Comparator.comparingLong(Task::due)
            .thenComparingInt(Task::x).thenComparingInt(Task::y).thenComparingInt(Task::z);
    private final Map<Position, Task> pending = new HashMap<>();
    private long tick;

    public long tick() { return tick; }
    public List<Task> tasks() { return pending.values().stream().sorted(ORDER).toList(); }

    public void schedule(int x, int y, int z) {
        Task task = new Task(Math.addExact(tick, FALL_DELAY), x, y, z);
        pending.merge(new Position(x, y, z), task, (old, next) -> old.due() <= next.due() ? old : next);
    }

    public void cancel(int x, int y, int z) {
        pending.remove(new Position(x, y, z));
    }

    public List<Task> advance() {
        tick = Math.incrementExact(tick);
        List<Task> due = pending.values().stream().filter(t -> t.due() <= tick)
                .sorted(ORDER).limit(MAX_PER_TICK).toList();
        for (Task task : due) pending.remove(new Position(task.x(), task.y(), task.z()));
        return due;
    }

    public void restore(long savedTick, List<Task> tasks) {
        if (savedTick < 0 || savedTick > Long.MAX_VALUE - FALL_DELAY - 1) {
            throw new IllegalArgumentException("Invalid simulation tick");
        }
        Map<Position, Task> validated = new HashMap<>();
        for (Task task : tasks) {
            if (validated.put(new Position(task.x(), task.y(), task.z()), task) != null) {
                throw new IllegalArgumentException("Duplicate scheduled cell");
            }
        }
        tick = savedTick;
        pending.clear();
        pending.putAll(validated);
    }
}
