package io.github.term4.polyp.mechanics.fluids;

import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What this fluid turns into beside {@code other} (1.8 BlockLiquid.checkForMixing): a source becomes
 * {@code intoSource}, a flow at or under {@code flowLevelMax} becomes {@code intoFlow}, and a flow falling onto
 * {@code other} leaves {@code underFlow} there instead of spreading.
 */
public record Mixing(@NotNull Block other, @Nullable Block intoSource, @Nullable Block intoFlow, int flowLevelMax,
                     @Nullable Block underFlow) {

    /** 1.8 lava meeting water: obsidian, cobblestone up to level 4, stone under a fall. */
    public static final Mixing LAVA_18 = new Mixing(Block.WATER, Block.OBSIDIAN, Block.COBBLESTONE, 4, Block.STONE);

    public boolean reacts(@NotNull Block block) {
        return block.compare(other);
    }

    /** The block a fluid of {@code level} beside {@code other} becomes, or null to stay. */
    public @Nullable Block beside(int level) {
        if (level == 0) return intoSource;
        return level <= flowLevelMax ? intoFlow : null;
    }
}
