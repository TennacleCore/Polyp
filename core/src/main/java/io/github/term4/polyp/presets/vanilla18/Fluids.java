package io.github.term4.polyp.presets.vanilla18;

import io.github.term4.polyp.mechanics.fluids.Flow;
import io.github.term4.polyp.mechanics.fluids.FluidConfig;
import io.github.term4.polyp.mechanics.fluids.FluidsConfig;
import io.github.term4.polyp.mechanics.fluids.Mixing;
import net.minestom.server.instance.block.Block;

/** 1.8's water and lava. */
public final class Fluids {

    private Fluids() {}

    public static FluidsConfig config() {
        return FluidsConfig.builder()
                .buckets(true)
                .bucketReach(5.0) // Item.getMovingObjectPositionFromPlayer, either mode
                .defaults(FluidConfig.builder()
                        .slopeDistance(4)
                        .sourceNeighbors(2)
                        .updates(true)
                        .flows(Flow.ANY)
                        .blocked(io.github.term4.polyp.mechanics.fluids.Fluids.BLOCKED_18)
                        .replaces(io.github.term4.polyp.mechanics.fluids.Fluids.NONE)
                        .build())
                .fluid(Block.WATER, water())
                .fluid(Block.LAVA, lava())
                .build();
    }

    public static FluidConfig water() {
        return FluidConfig.builder()
                .tickRate(5)
                .dropOff(1)
                .infiniteSource(true)
                .hesitates(false)
                .washes(true)
                .build();
    }

    public static FluidConfig lava() {
        return FluidConfig.builder()
                .tickRate(30) // the overworld's; the nether's 10 is a world's own knob
                .dropOff(2)
                .infiniteSource(false)
                .hesitates(true)
                .washes(false)
                .replaces(io.github.term4.polyp.mechanics.fluids.Fluids.WATER)
                .mixing(Mixing.LAVA_18)
                .build();
    }
}
