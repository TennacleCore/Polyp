package io.github.term4.polyp.mechanics.containers;

import io.github.term4.polyp.api.event.item.ItemSpawnEvent;
import io.github.term4.polyp.entity.DroppedItemEntity;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

/** What becomes of a holder's contents when the holder goes: a broken container now, a killed entity later. */
public enum Spill {
    /** Scattered at the spot with vanilla drop physics. */
    DROP,
    /** Left under their key; an ender chest's contents outlive its block. */
    KEEP,
    /** Carried by the item the block drops as (a shulker box). */
    PACK,
    VANISH;

    static final int PICKUP_DELAY_TICKS = 10;

    /** Vanilla {@code Block.dropBlockAsItem}: the cell's middle half, a small upward kick, 10t pickup delay. */
    public static void scatter(@NotNull MechanicsWorld world, @NotNull Point at, @NotNull Iterable<ItemStack> stacks,
                               @Nullable Player by) {
        var rnd = ThreadLocalRandom.current();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.isAir()) continue;
            Pos pos = new Pos(at.blockX() + rnd.nextDouble() * 0.5 + 0.25, at.blockY() + rnd.nextDouble() * 0.5 + 0.25,
                    at.blockZ() + rnd.nextDouble() * 0.5 + 0.25);
            Vec kick = new Vec(rnd.nextDouble() * 0.2 - 0.1, 0.2, rnd.nextDouble() * 0.2 - 0.1);
            DroppedItemEntity.spawn(world, pos, kick, stack, null, PICKUP_DELAY_TICKS, ItemSpawnEvent.Cause.BLOCK_DROP, by);
        }
    }
}
