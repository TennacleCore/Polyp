package io.github.term4.polyp.mechanics.mobs.ai;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntSupplier;

/** 1.8 EntityAITasks: goals by priority, re-picked every {@code tickRate} ticks, ticked every tick. */
public final class GoalSelector {

    private record Entry(int priority, Goal goal) {}

    private final List<Entry> entries = new ArrayList<>();
    private final List<Entry> executing = new ArrayList<>();
    private final IntSupplier tickRate;
    private int tickCount;

    public GoalSelector(IntSupplier tickRate) {
        this.tickRate = tickRate;
    }

    public void add(int priority, Goal goal) {
        entries.add(new Entry(priority, goal));
    }

    public void remove(Goal goal) {
        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            if (e.goal != goal) continue;
            if (executing.contains(e)) {
                e.goal.resetTask();
                executing.remove(e);
            }
            it.remove();
        }
    }

    public void tick() {
        if (tickCount++ % Math.max(1, tickRate.getAsInt()) == 0) {
            for (Entry e : entries) {
                boolean running = executing.contains(e);
                if (running) {
                    if (canUse(e) && e.goal.continueExecuting()) continue;
                    e.goal.resetTask();
                    executing.remove(e);
                }
                if (canUse(e) && e.goal.shouldExecute()) {
                    e.goal.startExecuting();
                    executing.add(e);
                }
            }
        } else {
            Iterator<Entry> it = executing.iterator();
            while (it.hasNext()) {
                Entry e = it.next();
                if (!e.goal.continueExecuting()) {
                    e.goal.resetTask();
                    it.remove();
                }
            }
        }
        for (Entry e : List.copyOf(executing)) e.goal.updateTask();
    }

    private boolean canUse(Entry entry) {
        for (Entry other : entries) {
            if (other == entry) continue;
            if (entry.priority >= other.priority) {
                if ((entry.goal.mutexBits() & other.goal.mutexBits()) != 0 && executing.contains(other)) return false;
            } else if (!other.goal.isInterruptible() && executing.contains(other)) {
                return false;
            }
        }
        return true;
    }
}
