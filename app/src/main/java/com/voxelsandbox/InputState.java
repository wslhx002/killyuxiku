package com.voxelsandbox;

/**
 * 线程安全的输入状态：UI 线程写入，渲染线程读取。
 */
public class InputState {
    private float yawDelta, pitchDelta;
    private float moveX, moveZ;
    private boolean jumpQueued, breakQueued, placeQueued, flyQueued;
    public volatile int selectedSlot = 0;
    public volatile boolean flying = false;

    public synchronized void addLook(float dyaw, float dpitch) {
        yawDelta += dyaw;
        pitchDelta += dpitch;
    }

    public synchronized void setMove(float mx, float mz) {
        moveX = mx; moveZ = mz;
    }

    public synchronized void queueJump() { jumpQueued = true; }
    public synchronized void queueBreak() { breakQueued = true; }
    public synchronized void queuePlace() { placeQueued = true; }
    public synchronized void queueFlyToggle() { flyQueued = true; }

    public synchronized float consumeYaw() { float v = yawDelta; yawDelta = 0; return v; }
    public synchronized float consumePitch() { float v = pitchDelta; pitchDelta = 0; return v; }
    public synchronized float moveX() { return moveX; }
    public synchronized float moveZ() { return moveZ; }
    public synchronized boolean consumeJump() { boolean v = jumpQueued; jumpQueued = false; return v; }
    public synchronized boolean consumeBreak() { boolean v = breakQueued; breakQueued = false; return v; }
    public synchronized boolean consumePlace() { boolean v = placeQueued; placeQueued = false; return v; }
    public synchronized boolean consumeFlyToggle() { boolean v = flyQueued; flyQueued = false; return v; }
}
