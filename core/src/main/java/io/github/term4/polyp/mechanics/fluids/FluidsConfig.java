package io.github.term4.polyp.mechanics.fluids;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.Config;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.mechanics.fluids.FluidsConfigResolver.FluidContext;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** The fluid module's config: the bucket knobs plus one {@link FluidConfig} per fluid block over {@code defaults}. */
@GenerateBuilder
public final class FluidsConfig extends Config<FluidContext, FluidsConfig> {

    /** Whether the module handles bucket uses at all. */
    public final @Nullable FieldValue<FluidContext, Boolean> buckets;
    /** How far a bucket reaches; {@code <= 0} reads the player's interaction range (modern). */
    public final @Nullable FieldValue<FluidContext, Double> bucketReach;
    /** Blocks with a {@code waterlogged} property hold water (26.1); off, they are plain blocks whatever the property says. */
    public final @Nullable FieldValue<FluidContext, Boolean> waterlogging;
    public final @Nullable FluidConfig defaults;
    public final Map<Key, FluidConfig> fluids;

    private FluidsConfig(Builder b) {
        super(b.subConfig);
        this.buckets = b.buckets;
        this.bucketReach = b.bucketReach;
        this.waterlogging = b.waterlogging;
        this.defaults = b.defaults;
        this.fluids = Map.copyOf(b.fluids);
    }

    /** The fluid's knobs over {@code defaults}, or null when the block is no fluid this config knows. */
    public @Nullable FluidConfig fluid(Block block) {
        FluidConfig own = fluids.get(block.key());
        if (own == null) return null;
        return defaults == null ? own : own.fromBase(defaults);
    }

    public boolean knows(Block block) {
        return fluids.containsKey(block.key());
    }

    @Override
    public FluidsConfig fromBase(FluidsConfig base) {
        Map<Key, FluidConfig> merged = new LinkedHashMap<>(base.fluids);
        fluids.forEach((key, fluid) -> merged.merge(key, fluid, (under, over) -> over.fromBase(under)));
        FluidConfig mergedDefaults = defaults == null ? base.defaults
                : base.defaults == null ? defaults : defaults.fromBase(base.defaults);
        Builder b = new Builder();
        b.subConfig(subConfig != null ? subConfig : base.subConfig);
        b.mergeKnobs(this, base);
        return b.defaults(mergedDefaults).fluids(merged).build();
    }

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder extends FluidsConfigBuilderBase<Builder> {
        @Override protected Builder self() { return this; }

        private Function<FluidContext, FluidsConfig> subConfig;
        private @Nullable FluidConfig defaults;
        private final Map<Key, FluidConfig> fluids = new LinkedHashMap<>();

        Builder() {}

        Builder(FluidsConfig c) {
            super(c);
            subConfig = c.subConfig;
            defaults = c.defaults;
            fluids.putAll(c.fluids);
        }

        public Builder subConfig(Function<FluidContext, FluidsConfig> fn) { subConfig = fn; return this; }
        public Builder defaults(@Nullable FluidConfig defaults) { this.defaults = defaults; return this; }
        public Builder fluid(Block block, FluidConfig fluid) { fluids.put(block.key(), fluid); return this; }
        public Builder fluids(Map<Key, FluidConfig> entries) { fluids.putAll(entries); return this; }

        public FluidsConfig build() { return new FluidsConfig(this); }
    }
}
