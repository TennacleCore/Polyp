package io.github.term4.polyp.mechanics.fluids;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.Config;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.mechanics.fluids.FluidsConfigResolver.FluidContext;
import io.github.term4.polyp.vri.BlockDrops.DropRule;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

/** One fluid's knobs: the numbers and the rules of BlockDynamicLiquid and FlowingFluid as fields. */
@GenerateBuilder
public final class FluidConfig extends Config<FluidContext, FluidConfig> {

    /** Ticks between spread steps. */
    public final @Nullable FieldValue<FluidContext, Integer> tickRate;
    /** Levels lost per block of spread. */
    public final @Nullable FieldValue<FluidContext, Integer> dropOff;
    /** How far the slope search looks for a drop. */
    public final @Nullable FieldValue<FluidContext, Integer> slopeDistance;
    /** Two sources over solid ground make a third. */
    public final @Nullable FieldValue<FluidContext, Boolean> infiniteSource;
    public final @Nullable FieldValue<FluidContext, Integer> sourceNeighbors;
    /** Off, the fluid never ticks: it stays exactly as placed. */
    public final @Nullable FieldValue<FluidContext, Boolean> updates;
    /** Lava's three-in-four chance to take four times as long rising a level. */
    public final @Nullable FieldValue<FluidContext, Boolean> hesitates;
    /** What a block the fluid runs over drops, as a bare hand would get it; null, it is simply gone (lava). */
    public final @Nullable FieldValue<FluidContext, DropRule> washes;
    public final @Nullable FieldValue<FluidContext, Flow> flows;
    public final @Nullable FieldValue<FluidContext, Passage> passage;
    /** A block the fluid neither enters nor paths through. */
    public final @Nullable FieldValue<FluidContext, Predicate<Block>> blocked;
    /** Another fluid's blocks this one may flow into (none in 1.8; modern water takes low lava). */
    public final @Nullable FieldValue<FluidContext, Predicate<Block>> replaces;
    /** Sources beside a cell that falls make it spread sideways too (26.1 three; {@code <= 0} never). */
    public final @Nullable FieldValue<FluidContext, Integer> fallingSideSources;
    public final @Nullable FieldValue<FluidContext, Mixing> mixing;

    private FluidConfig(Builder b) {
        super(b.subConfig);
        this.tickRate = b.tickRate;
        this.dropOff = b.dropOff;
        this.slopeDistance = b.slopeDistance;
        this.infiniteSource = b.infiniteSource;
        this.sourceNeighbors = b.sourceNeighbors;
        this.updates = b.updates;
        this.hesitates = b.hesitates;
        this.washes = b.washes;
        this.flows = b.flows;
        this.passage = b.passage;
        this.blocked = b.blocked;
        this.replaces = b.replaces;
        this.fallingSideSources = b.fallingSideSources;
        this.mixing = b.mixing;
    }

    @Override
    public FluidConfig fromBase(FluidConfig base) {
        Builder b = new Builder();
        b.subConfig(subConfig != null ? subConfig : base.subConfig);
        b.mergeKnobs(this, base);
        return b.build();
    }

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder extends FluidConfigBuilderBase<Builder> {
        @Override protected Builder self() { return this; }

        private Function<FluidContext, FluidConfig> subConfig;

        Builder() {}

        Builder(FluidConfig c) {
            super(c);
            subConfig = c.subConfig;
        }

        public Builder subConfig(Function<FluidContext, FluidConfig> fn) { subConfig = fn; return this; }

        public FluidConfig build() { return new FluidConfig(this); }
    }
}
