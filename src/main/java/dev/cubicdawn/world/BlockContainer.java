package dev.cubicdawn.world;

import java.util.List;

/** A chest or a three-slot furnace. No wall-clock catch-up; every update is one simulation tick. */
public final class BlockContainer {
    public static final int COOK_TICKS = 120;
    public record State(int x, int y, int z, int type, int burn, int progress, List<Inventory.Stack> items) {
        public State {
            if (x < -16 || x > 16 || y < -16 || y > 16 || z < -16 || z > 16
                    || (type != BlockIds.CHEST && type != BlockIds.FURNACE)
                    || burn < 0 || burn > 1200 || progress < 0 || progress >= COOK_TICKS
                    || (type == BlockIds.CHEST && (burn != 0 || progress != 0))
                    || items.size() != (type == BlockIds.CHEST ? 27 : 3)) {
                throw new IllegalArgumentException("Invalid container state");
            }
            items = List.copyOf(items);
            if (type == BlockIds.FURNACE) {
                if ((items.get(0).count() > 0 && resultFor(items.get(0).id()) == 0)
                        || (items.get(1).count() > 0 && fuelTicks(items.get(1).id()) == 0)
                        || (items.get(2).count() > 0 && items.get(2).id() != BlockIds.GLASS && items.get(2).id() != ItemIds.CHARCOAL && items.get(2).id() != ItemIds.IRON_INGOT)
                        || (progress > 0 && items.get(0).count() == 0)) throw new IllegalArgumentException("Invalid furnace slots");
            }
        }
    }
    private final int type;
    private Inventory contents = new Inventory();
    private int burn, progress;
    public BlockContainer(int type) {
        if (type != BlockIds.CHEST && type != BlockIds.FURNACE) throw new IllegalArgumentException("Not a container");
        this.type = type;
    }
    public int type() { return type; }
    public int burn() { return burn; }
    public int progress() { return progress; }
    public Inventory.Stack slot(int slot) { return contents.slot(slot); }
    public boolean empty() { return contents.snapshot().stream().allMatch(s -> s.count() == 0) && burn == 0 && progress == 0; }
    public State state(int x, int y, int z) {
        return new State(x,y,z,type,burn,progress,contents.snapshot().subList(0,type==BlockIds.CHEST ? 27 : 3));
    }
    public static BlockContainer restore(State state) {
        BlockContainer container = new BlockContainer(state.type());
        var items = new java.util.ArrayList<>(state.items());
        while (items.size() < 27) items.add(new Inventory.Stack(0,0));
        container.contents = Inventory.restore(items); container.burn=state.burn(); container.progress=state.progress();
        return container;
    }
    public static int resultFor(int input) {
        return input==ItemIds.RAW_IRON ? ItemIds.IRON_INGOT : input == BlockIds.SAND ? BlockIds.GLASS : input == BlockIds.LOG ? ItemIds.CHARCOAL : 0;
    }
    public static int fuelTicks(int input) {
        return input == ItemIds.CHARCOAL || input==ItemIds.COAL ? 1200 : input == BlockIds.LOG ? 600
                : input == BlockIds.PLANKS ? 300 : input == ItemIds.STICK ? 60 : 0;
    }
    public boolean deposit(Inventory source, int sourceSlot, boolean fuel) {
        Inventory.Stack stack = source.slot(sourceSlot);
        if (stack.count() == 0) return false;
        if (type == BlockIds.CHEST) return source.transferTo(contents,sourceSlot);
        int target = fuel ? 1 : 0;
        if ((fuel ? fuelTicks(stack.id()) : resultFor(stack.id())) == 0) return false;
        Inventory.Stack current = contents.slot(target);
        if (current.count() == 64 || (current.count() > 0 && current.id() != stack.id())) return false;
        var next = new java.util.ArrayList<>(contents.snapshot());
        next.set(target,new Inventory.Stack(stack.id(),current.count()+1));
        contents=Inventory.restore(next); source.remove(sourceSlot,1); return true;
    }
    public boolean withdraw(Inventory target, int slot) {
        if (slot < 0 || slot >= (type==BlockIds.CHEST ? 27 : 3)) return false;
        boolean moved = contents.transferTo(target,slot);
        if (moved && type==BlockIds.FURNACE && slot==0) progress=0;
        return moved;
    }
    public boolean tick() {
        if (type != BlockIds.FURNACE) return false;
        int beforeBurn=burn, beforeProgress=progress;
        Inventory.Stack input=contents.slot(0), fuel=contents.slot(1), output=contents.slot(2);
        int product=resultFor(input.id());
        boolean canCook=input.count()>0 && product!=0 && (output.count()==0 || (output.id()==product && output.count()<64));
        if (burn==0 && canCook && fuel.count()>0) { burn=fuelTicks(fuel.id()); contents.remove(1,1); }
        if (burn>0) {
            burn--;
            if (canCook && ++progress==COOK_TICKS) {
                var next=new java.util.ArrayList<>(contents.snapshot());
                next.set(0,input.count()==1 ? new Inventory.Stack(0,0) : new Inventory.Stack(input.id(),input.count()-1));
                next.set(2,new Inventory.Stack(product,output.count()+1));
                contents=Inventory.restore(next); progress=0;
            }
        }
        return beforeBurn!=burn || beforeProgress!=progress;
    }
}
