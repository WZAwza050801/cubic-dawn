package dev.cubicdawn.world;

import java.util.List;

/** 27 fixed slots; operations either fit completely or leave every slot unchanged. */
public final class Inventory {
    public static final int SLOTS = 27;
    public static final int STACK_LIMIT = 64;
    public record Stack(int id, int count, int durability) {
        public Stack(int id, int count) { this(id, count, ItemIds.durability(id)); }
        public Stack {
            if (id < 0 || id > 65535 || count < 0 || count > ItemIds.stackLimit(id)
                    || (id == 0) != (count == 0)) throw new IllegalArgumentException("Invalid item stack");
            if (ItemIds.tool(id) ? durability < 1 || durability > ItemIds.durability(id) : durability != 0) {
                throw new IllegalArgumentException("Invalid item durability");
            }
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
    public boolean add(int id, int count) { return insert(id, count, ItemIds.durability(id)); }
    public boolean addStack(Stack stack) {
        return stack.count() == 0 || insert(stack.id(), stack.count(), stack.durability());
    }
    private boolean insert(int id, int count, int durability) {
        if (id <= 0 || id > 65535 || count <= 0) throw new IllegalArgumentException("Invalid items");
        int capacity = 0;
        for (Stack stack : slots) if ((stack.id() == id && stack.durability() == durability) || stack.id() == 0) capacity += ItemIds.stackLimit(id) - stack.count();
        if (capacity < count) return false;
        int remaining = count;
        // Fill existing stacks before occupying empty slots.
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < SLOTS && remaining > 0; i++) {
            Stack stack = slots[i];
            if (pass == 0 ? stack.id() != id || stack.durability() != durability : stack.id() != 0) continue;
            int amount = Math.min(ItemIds.stackLimit(id) - stack.count(), remaining);
            if (amount == 0) continue;
            slots[i] = new Stack(id, stack.count() + amount, durability);
            remaining -= amount;
        }
        return true;
    }
    public boolean remove(int slot, int count) {
        Stack stack = slots[slot];
        if (count <= 0 || stack.count() < count) return false;
        slots[slot] = stack.count() == count ? new Stack(0, 0) : new Stack(stack.id(), stack.count() - count, stack.durability());
        return true;
    }
    public void damageTool(int slot) {
        Stack stack = slots[slot];
        if (!ItemIds.tool(stack.id())) return;
        slots[slot] = stack.durability() == 1 ? new Stack(0, 0) : new Stack(stack.id(), 1, stack.durability() - 1);
    }
    public boolean transferTo(Inventory target, int slot) {
        if (target == this || slots[slot].count() == 0 || !target.addStack(slots[slot])) return false;
        slots[slot] = new Stack(0, 0);
        return true;
    }
    public void swap(int first, int second) {
        Stack temporary = slots[first]; slots[first] = slots[second]; slots[second] = temporary;
    }
    public record Ingredient(int id, int amount) { }
    public enum Recipe {
        SANDSTONE(BlockIds.SANDSTONE, 1, false, new Ingredient(BlockIds.SAND, 4)),
        STONE_BRICKS(BlockIds.STONE_BRICKS, 4, false, new Ingredient(BlockIds.STONE, 4)),
        PLANKS(BlockIds.PLANKS, 4, false, new Ingredient(BlockIds.LOG, 1)),
        STICKS(ItemIds.STICK, 4, false, new Ingredient(BlockIds.PLANKS, 2)),
        WORKBENCH(BlockIds.WORKBENCH, 1, false, new Ingredient(BlockIds.PLANKS, 4)),
        SAPLING(ItemIds.SAPLING, 1, false, new Ingredient(BlockIds.DIRT, 4)),
        PICKAXE(ItemIds.WOOD_PICKAXE, 1, true, new Ingredient(BlockIds.PLANKS, 3), new Ingredient(ItemIds.STICK, 2)),
        AXE(ItemIds.WOOD_AXE, 1, true, new Ingredient(BlockIds.PLANKS, 3), new Ingredient(ItemIds.STICK, 2)),
        SHOVEL(ItemIds.WOOD_SHOVEL, 1, true, new Ingredient(BlockIds.PLANKS, 1), new Ingredient(ItemIds.STICK, 2)),
        SAPLING_FROM_LEAVES(ItemIds.SAPLING, 1, false, new Ingredient(BlockIds.LEAVES, 4)),
        FURNACE(BlockIds.FURNACE, 1, true, new Ingredient(BlockIds.STONE, 8)),
        CHEST(BlockIds.CHEST, 1, true, new Ingredient(BlockIds.PLANKS, 8)),
        STONE_PICKAXE(ItemIds.STONE_PICKAXE,1,true,new Ingredient(BlockIds.STONE,3),new Ingredient(ItemIds.STICK,2)),
        STONE_AXE(ItemIds.STONE_AXE,1,true,new Ingredient(BlockIds.STONE,3),new Ingredient(ItemIds.STICK,2)),
        STONE_SHOVEL(ItemIds.STONE_SHOVEL,1,true,new Ingredient(BlockIds.STONE,1),new Ingredient(ItemIds.STICK,2)),
        IRON_PICKAXE(ItemIds.IRON_PICKAXE,1,true,new Ingredient(ItemIds.IRON_INGOT,3),new Ingredient(ItemIds.STICK,2)),
        IRON_AXE(ItemIds.IRON_AXE,1,true,new Ingredient(ItemIds.IRON_INGOT,3),new Ingredient(ItemIds.STICK,2)),
        IRON_SHOVEL(ItemIds.IRON_SHOVEL,1,true,new Ingredient(ItemIds.IRON_INGOT,1),new Ingredient(ItemIds.STICK,2));
        public final int output, amount;
        public final boolean needsBench;
        public final List<Ingredient> ingredients;
        Recipe(int output, int amount, boolean needsBench, Ingredient... ingredients) {
            this.output = output; this.amount = amount; this.needsBench = needsBench;
            this.ingredients = List.of(ingredients);
        }
    }
    public boolean craft(Recipe recipe) { return craft(recipe, false); }
    public boolean craft(Recipe recipe, boolean nearWorkbench) {
        if (recipe.needsBench && !nearWorkbench) return false;
        for (Ingredient input : recipe.ingredients) if (count(input.id()) < input.amount()) return false;
        Inventory next = copy();
        for (Ingredient input : recipe.ingredients) {
            int remaining = input.amount();
            for (int i = 0; i < SLOTS && remaining > 0; i++) {
                if (next.slot(i).id() != input.id()) continue;
                int amount = Math.min(remaining, next.slot(i).count());
                next.remove(i, amount);
                remaining -= amount;
            }
        }
        if (!next.add(recipe.output, recipe.amount)) return false;
        System.arraycopy(next.slots, 0, slots, 0, SLOTS);
        return true;
    }
    private Inventory copy() { return restore(snapshot()); }
}
