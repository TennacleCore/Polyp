package io.github.term4.polyp.presets.vanilla;

import io.github.term4.polyp.mechanics.fluids.FluidsConfig;
import net.minestom.server.instance.block.Block;

/** 26.1's numbers over the 1.8 loop: lava's shorter slope search and no sideways march into water, water taking low lava, the bucket at the interaction range. */
public final class Fluids {

    private Fluids() {}

    public static FluidsConfig config() {
        FluidsConfig base = io.github.term4.polyp.presets.vanilla18.Fluids.config();
        return base.toBuilder()
                .bucketReach(-1.0)
                .fluid(Block.WATER, base.fluids.get(Block.WATER.key()).toBuilder()
                        .replaces(io.github.term4.polyp.mechanics.fluids.Fluids.LOW_LAVA)
                        .build())
                .fluid(Block.LAVA, base.fluids.get(Block.LAVA.key()).toBuilder()
                        .slopeDistance(2)
                        .replaces(io.github.term4.polyp.mechanics.fluids.Fluids.NONE) // WaterFluid.canBeReplacedWith: only from above
                        .build())
                .build();
    }
}
