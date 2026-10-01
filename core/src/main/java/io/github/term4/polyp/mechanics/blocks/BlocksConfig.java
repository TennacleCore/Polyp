package io.github.term4.polyp.mechanics.blocks;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.Config;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.mechanics.blocks.BlocksConfigResolver.BlocksContext;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/** Block placement and digging per scope; unset knobs resolve modern. */
@GenerateBuilder
public final class BlocksConfig extends Config<BlocksContext, BlocksConfig> {

    /** Whose {@code BlockChest} places a chest. */
    public enum ChestPlacement {
        /** The placement rule the app registered: equal facings pair, left/right by the clockwise side. */
        MODERN,
        /** 1.8: adjacency pairs, the new chest turns the old one, and beside two (or a pair) nothing lands. */
        LEGACY
    }

    public final @Nullable FieldValue<BlocksContext, ChestPlacement> chestPlacement;
    /**
     * How much of a dig the server must have counted for a finish to break the block ({@code PlayerInteractManager}:
     * 0.7 in both eras); unset, any finish breaks it.
     */
    public final @Nullable FieldValue<BlocksContext, Double> digFinishShare;
    /** A finish short of the share still breaks the block once the server's own count reaches a whole dig (vanilla). */
    public final @Nullable FieldValue<BlocksContext, Boolean> digFinishLate;

    private BlocksConfig(Builder b) {
        super(b.subConfig);
        this.chestPlacement = b.chestPlacement;
        this.digFinishShare = b.digFinishShare;
        this.digFinishLate = b.digFinishLate;
    }

    @Override
    public BlocksConfig fromBase(BlocksConfig base) {
        Builder b = new Builder();
        b.subConfig(subConfig != null ? subConfig : base.subConfig);
        b.mergeKnobs(this, base);
        return b.build();
    }

    public static Builder builder() { return new Builder(); }
    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder extends BlocksConfigBuilderBase<Builder> {

        @Override protected Builder self() { return this; }

        private Function<BlocksContext, BlocksConfig> subConfig;

        Builder() {}
        Builder(BlocksConfig c) {
            super(c);
            subConfig = c.subConfig;
        }

        public Builder subConfig(Function<BlocksContext, BlocksConfig> fn) { subConfig = fn; return this; }

        public BlocksConfig build() { return new BlocksConfig(this); }
    }
}
