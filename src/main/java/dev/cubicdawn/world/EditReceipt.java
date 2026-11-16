package dev.cubicdawn.world;

public record EditReceipt(boolean accepted, String reason, long revision) {

    public static EditReceipt ok(long revision) {
        return new EditReceipt(true, "", revision);
    }

    public static EditReceipt reject(String reason) {
        return new EditReceipt(false, reason, 0L);
    }
}
