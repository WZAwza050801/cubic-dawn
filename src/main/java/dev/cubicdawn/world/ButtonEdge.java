package dev.cubicdawn.world;

/**
 * 一次按下只产生一次动作。离开可操作状态时 disarm，
 * 回来的第一下如果键还按着，不算新的一次按下。
 */
public final class ButtonEdge {

    private boolean wasDown;
    private boolean armed = true;

    public void disarm() {
        armed = false;
    }

    public boolean poll(boolean down) {
        if (!armed) {
            wasDown = down;
            armed = true;
            return false;
        }
        boolean fired = down && !wasDown;
        wasDown = down;
        return fired;
    }
}
