package io.github.term4.polyp.mechanics.containers;

import io.github.term4.polyp.config.Config;
import io.github.term4.polyp.mechanics.containers.ContainersConfigResolver.ContainerContext;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Which blocks are containers and how each behaves, per scope via {@code MechanicsProfile}. A block is a container
 * iff it has an entry in {@link #blocks}; {@link #defaults} is what every entry inherits. A container an app
 * declares at one position ({@code ContainerSystem.declare}) needs no entry here.
 */
public final class ContainersConfig extends Config<ContainerContext, ContainersConfig> {

    public final @Nullable ContainerTypeConfig defaults;
    /** Block key -&gt; its kind; the keyset is exactly the container blocks. */
    public final Map<Key, ContainerTypeConfig> blocks;

    private ContainersConfig(Builder b) {
        super(b.subConfig);
        this.defaults = b.defaults;
        this.blocks = Map.copyOf(b.blocks);
    }

    public @Nullable ContainerTypeConfig defaults() { return defaults; }
    public @Nullable ContainerTypeConfig typeConfig(Block block) { return blocks.get(block.key()); }
    public boolean holds(Block block) { return blocks.containsKey(block.key()); }

    @Override
    public ContainersConfig fromBase(ContainersConfig base) {
        Map<Key, ContainerTypeConfig> merged = new LinkedHashMap<>(base.blocks);
        merged.putAll(blocks);
        ContainerTypeConfig mergedDefaults = defaults == null ? base.defaults
                : base.defaults == null ? defaults : defaults.fromBase(base.defaults);
        return new Builder()
                .subConfig(subConfig != null ? subConfig : base.subConfig)
                .defaults(mergedDefaults)
                .blocks(merged)
                .build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable ContainersConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {
        private Function<ContainerContext, ContainersConfig> subConfig;
        private @Nullable ContainerTypeConfig defaults;
        private final Map<Key, ContainerTypeConfig> blocks = new LinkedHashMap<>();

        Builder() {}
        Builder(ContainersConfig c) { subConfig = c.subConfig; defaults = c.defaults; blocks.putAll(c.blocks); }

        public Builder subConfig(Function<ContainerContext, ContainersConfig> fn) { subConfig = fn; return this; }
        public Builder defaults(@Nullable ContainerTypeConfig defaults) { this.defaults = defaults; return this; }

        public Builder block(Block block, ContainerTypeConfig type) { blocks.put(block.key(), type); return this; }

        /** Container blocks with no knobs of their own: they take {@link #defaults}. */
        public Builder blocks(Block... containers) {
            for (Block b : containers) blocks.putIfAbsent(b.key(), ContainerTypeConfig.builder().build());
            return this;
        }

        public Builder blocks(Map<Key, ContainerTypeConfig> entries) { blocks.putAll(entries); return this; }

        public ContainersConfig build() { return new ContainersConfig(this); }
    }
}
