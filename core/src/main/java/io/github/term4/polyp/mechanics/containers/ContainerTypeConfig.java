package io.github.term4.polyp.mechanics.containers;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.Config;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.mechanics.containers.ContainersConfigResolver.ContainerContext;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * One kind of container, held in a {@link ContainersConfig} by block: rows and title of the window, the
 * {@link ContainerKey key} its contents file under, the {@link ContainerFill fill} they start as, the
 * {@link Spill spill} on break. Unset knobs fall back to the config's defaults, then to a plain chest's.
 */
@GenerateBuilder
public final class ContainerTypeConfig extends Config<ContainerContext, ContainerTypeConfig> {

    public final @Nullable FieldValue<ContainerContext, Integer> rows;
    public final @Nullable FieldValue<ContainerContext, Component> title;
    public final @Nullable FieldValue<ContainerContext, ContainerKey> key;
    public final @Nullable FieldValue<ContainerContext, ContainerFill> fill;
    public final @Nullable FieldValue<ContainerContext, Spill> spill;
    public final @Nullable FieldValue<ContainerContext, Pairing> pairing;

    /** Two side by side opening as one window, and which half takes the upper rows. */
    public enum Pairing {
        NONE,
        /** 1.8 {@code InventoryLargeChest}: the west or north half on top, whichever way the pair faces. */
        LEGACY,
        /** Modern {@code ChestBlock}: the {@code right} half on top, so the viewer's left is always the upper rows. */
        MODERN
    }

    ContainerTypeConfig(Builder b) {
        super(b.subConfig);
        this.rows = b.rows;
        this.title = b.title;
        this.key = b.key;
        this.fill = b.fill;
        this.spill = b.spill;
        this.pairing = b.pairing;
    }

    @Override
    public ContainerTypeConfig fromBase(ContainerTypeConfig base) {
        Builder b = new Builder();
        b.mergeKnobs(this, base);
        b.subConfig = subConfig != null ? subConfig : base.subConfig;
        return b.build();
    }

    public static Builder builder() { return new Builder(); }
    public static Builder builder(ContainerTypeConfig base) { return new Builder(base); }
    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder extends ContainerTypeConfigBuilderBase<Builder> {

        @Override protected Builder self() { return this; }

        private Function<ContainerContext, ContainerTypeConfig> subConfig;

        Builder() {}
        Builder(ContainerTypeConfig c) {
            super(c);
            subConfig = c.subConfig;
        }

        public Builder subConfig(Function<ContainerContext, ContainerTypeConfig> fn) { subConfig = fn; return this; }

        public ContainerTypeConfig build() { return new ContainerTypeConfig(this); }
    }
}
