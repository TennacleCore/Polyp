package io.github.term4.polyp.mechanics.mobs.ai;

/** 1.8 EntityAIBase. Mutex bits: 1 movement, 2 look, 4 swimming. */
public abstract class Goal {

    private int mutexBits;

    public abstract boolean shouldExecute();

    public boolean continueExecuting() { return shouldExecute(); }

    public boolean isInterruptible() { return true; }

    public void startExecuting() {}

    public void resetTask() {}

    public void updateTask() {}

    public void mutexBits(int bits) { mutexBits = bits; }

    public int mutexBits() { return mutexBits; }
}
