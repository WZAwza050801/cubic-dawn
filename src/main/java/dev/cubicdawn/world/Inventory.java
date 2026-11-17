package dev.cubicdawn.world;

import java.util.List;

/** 27 fixed slots; operations either fit completely or leave every slot unchanged. */
public final class Inventory {
    public static final int SLOTS = 27;
    public static final int STACK_LIMIT = 64;
    public record Stack(int id, int count) {
        public Stack {
            if (id < 0 || id > 65535 || count < 0 || count > STACK_LIMIT
                    || (id == 0) != (count == 0)) throw new IllegalArgumentException("Invalid item stack");
        }
    }
    private final Stack[] slots = new Stack[SLOTS];
    public Inventory() {
        java.util.Arrays.fill(slots, new Stack(0, 0));
    }
    public static Inventory starter() {
        Inventory inventory = new Inventory();
        inventory.slots[0] = new Stack(BlockIds.GRASS, 16);
        inventory.slots[1] = new Stack(BlockIds.DIRT, 16);
        inventory.slots[2] = new Stack(BlockIds.STONE, 16);
        inventory.slots[7] = new Stack(BlockIds.SAND, 16);
        return inventory;
    }
    public static Inventory restore(List<Stack> saved) {
        if (saved.size() != SLOTS) throw new IllegalArgumentException("Expected 27 inventory slots");
        Inventory inventory = new Inventory();
        for (int i = 0; i < SLOTS; i++) inventory.slots[i] = java.util.Objects.requireNonNull(saved.get(i));
        return inventory;
    }
    public List<Stack> snapshot() { return List.of(slots.clone()); }
    public Stack slot(int slot) { return slots[slot]; }
    public int count(int id) {
        int count = 0;
        for (Stack stack : slots) if (stack.id() == id) count += stack.count();
        return count;
    }
    public boolean canAdd(int id, int count) { return copy().add(id, count); }
    public boolean add(int id, int count) {
        if (id <= 0 || id > 65535 || count <= 0) throw new IllegalArgumentException("Invalid items");
        int capacity = 0;
        for (Stack stack : slots) if (stack.id() == id || stack.id() == 0) capacity += STACK_LIMIT - stack.count();
        if (capacity < count) return false;
        int remaining = count;
        // Fill existing stacks before occupying empty slots.
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < SLOTS && remaining > 0; i++) {
            Stack stack = slots[i];
            if (pass == 0 ? stack.id() != id : stack.id() != 0) continue;
            int amount = Math.min(STACK_LIMIT - stack.count(), remaining);
            slots[i] = new Stack(id, stack.count() + amount);
            remaining -= amount;
        }
        return true;
    }
    public boolean remove(int slot, int count) {
        Stack stack = slots[slot];
        if (count <= 0 || stack.count() < count) return false;
        slots[slot] = stack.count() == count ? new Stack(0, 0) : new Stack(stack.id(), stack.count() - count);
        return true;
    }
    public void swap(int first, int second) {
        Stack temporary = slots[first]; slots[first] = slots[second]; slots[second] = temporary;
    }
    public enum Recipe {
        SANDSTONE(BlockIds.SAND, 4, BlockIds.SANDSTONE, 1),
        STONE_BRICKS(BlockIds.STONE, 4, BlockIds.STONE_BRICKS, 4);
        public final int input, cost, output, amount;
        Recipe(int input, int cost, int output, int amount) {
            this.input = input; this.cost = cost; this.output = output; this.amount = amount;
        }
    }
    public boolean craft(Recipe recipe) {
        if (count(recipe.input) < recipe.cost) return false;
        Inventory next = copy();
        int remaining = recipe.cost;
        for (int i = 0; i < SLOTS && remaining > 0; i++) {
            if (next.slot(i).id() != recipe.input) continue;
            int amount = Math.min(remaining, next.slot(i).count());
            next.remove(i, amount);
            remaining -= amount;
        }
        if (!next.add(recipe.output, recipe.amount)) return false;
        System.arraycopy(next.slots, 0, slots, 0, SLOTS);
        return true;
    }
    private Inventory copy() { return restore(snapshot()); }
}
