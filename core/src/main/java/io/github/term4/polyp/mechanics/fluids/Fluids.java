package io.github.term4.polyp.mechanics.fluids;

import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import net.minestom.server.registry.RegistryTag;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/** The block predicates the presets name, and what a cell carries. */
public final class Fluids {

    private static final RegistryTag<Block> DOORS = tag("minecraft:doors");
    private static final RegistryTag<Block> SIGNS = tag("minecraft:all_signs");

    private Fluids() {}

    private static @Nullable RegistryTag<Block> tag(String key) {
        return Block.staticRegistry().getTag(Key.key(key));
    }

    private static boolean in(@Nullable RegistryTag<Block> tag, Block block) {
        return tag != null && tag.contains(block);
    }

    /** 1.8 BlockDynamicLiquid.isBlocked: what stops a flow and what it paths around. */
    public static final Predicate<Block> BLOCKED_18 = block -> block.blocksMotion() || in(DOORS, block) || in(SIGNS, block)
            || block.compare(Block.LADDER) || block.compare(Block.SUGAR_CANE) || block.compare(Block.NETHER_PORTAL);

    /** 26.1 FlowingFluid.canHoldAnyFluid, inverted; a block holding water is never blocked. */
    public static final Predicate<Block> BLOCKED_MODERN = block -> BLOCKED_18.test(block) || block.compare(Block.BUBBLE_COLUMN)
            || block.compare(Block.END_PORTAL) || block.compare(Block.END_GATEWAY) || block.compare(Block.STRUCTURE_VOID);

    public static final Predicate<Block> NONE = block -> false;

    /** 1.8 canFlowInto for lava: water is no bar, and the mixing decides what is left. */
    public static final Predicate<Block> WATER = block -> block.compare(Block.WATER);

    /** 26.1 LavaFluid.canBeReplacedWith: water takes lava still four levels tall. */
    public static final Predicate<Block> LOW_LAVA = block -> block.compare(Block.LAVA) && Spread.level(block) >= 1 && Spread.level(block) <= 4;

    /** A block holding water inside its shape. */
    public static boolean waterlogged(Block block) {
        return "true".equals(block.getProperty("waterlogged"));
    }

    /** A block that could hold water and does not. */
    public static boolean waterloggable(Block block) {
        return "false".equals(block.getProperty("waterlogged"));
    }

    /** The fluid the cell carries: the liquid itself, or the water a block holds when {@code waterlogging} counts it; null for none. */
    public static @Nullable Block fluidOf(Block block, boolean waterlogging) {
        if (block.liquid()) return block.defaultState();
        return waterlogging && waterlogged(block) ? Block.WATER : null;
    }
}
