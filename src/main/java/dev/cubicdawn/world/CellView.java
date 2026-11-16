package dev.cubicdawn.world;

public record CellView(CellKind kind, int id, int revision) {

    public static CellView known(int id, int revision) {
        return new CellView(CellKind.KNOWN, id, revision);
    }

    public static CellView unloaded() {
        return new CellView(CellKind.UNLOADED, 0, 0);
    }

    public static CellView outside() {
        return new CellView(CellKind.OUTSIDE, 0, 0);
    }
}
