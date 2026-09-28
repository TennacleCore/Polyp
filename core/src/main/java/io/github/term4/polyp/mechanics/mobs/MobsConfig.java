package io.github.term4.polyp.mechanics.mobs;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.Config;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.mechanics.mobs.MobsConfigResolver.MobContext;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/** The mob module's config: world-wide knobs plus one {@link MobKindConfig} per entity type over {@code defaults}. */
@GenerateBuilder
public final class MobsConfig extends Config<MobContext, MobsConfig> {

    public final @Nullable FieldValue<MobContext, Sides> sides;
    public final @Nullable FieldValue<MobContext, Difficulty> difficulty;
    public final @Nullable FieldValue<MobContext, DifficultyScaling> difficultyScaling;
    /** Ticks between goal re-selection (1.8 EntityAITasks 3, 26.1 every other tick). */
    public final @Nullable FieldValue<MobContext, Integer> goalTickRate;
    public final @Nullable MobKindConfig defaults;
    public final Map<Key, MobKindConfig> kinds;

    private MobsConfig(Builder b) {
        super(b.subConfig);
        this.sides = b.sides;
        this.difficulty = b.difficulty;
        this.difficultyScaling = b.difficultyScaling;
        this.goalTickRate = b.goalTickRate;
        this.defaults = b.defaults;
        this.kinds = Map.copyOf(b.kinds);
    }

    /** The kind's knobs over {@code defaults}, or null when neither knows the type. */
    public @Nullable MobKindConfig kind(EntityType type) {
        MobKindConfig own = kinds.get(type.key());
        if (defaults == null) return own;
        return own == null ? defaults : own.fromBase(defaults);
    }

    @Override
    public MobsConfig fromBase(MobsConfig base) {
        Map<Key, MobKindConfig> merged = new LinkedHashMap<>(base.kinds);
        kinds.forEach((key, kind) -> merged.merge(key, kind, (under, over) -> over.fromBase(under)));
        MobKindConfig mergedDefaults = defaults == null ? base.defaults
                : base.defaults == null ? defaults : defaults.fromBase(base.defaults);
        Builder b = new Builder();
        b.subConfig(subConfig != null ? subConfig : base.subConfig);
        b.mergeKnobs(this, base);
        return b.defaults(mergedDefaults).kinds(merged).build();
    }

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder extends MobsConfigBuilderBase<Builder> {
        @Override protected Builder self() { return this; }

        private Function<MobContext, MobsConfig> subConfig;
        private @Nullable MobKindConfig defaults;
        private final Map<Key, MobKindConfig> kinds = new LinkedHashMap<>();

        Builder() {}

        Builder(MobsConfig c) {
            super(c);
            subConfig = c.subConfig;
            defaults = c.defaults;
            kinds.putAll(c.kinds);
        }

        public Builder subConfig(Function<MobContext, MobsConfig> fn) { subConfig = fn; return this; }
        public Builder defaults(@Nullable MobKindConfig defaults) { this.defaults = defaults; return this; }
        public Builder kind(EntityType type, MobKindConfig kind) { kinds.put(type.key(), kind); return this; }
        public Builder kinds(Map<Key, MobKindConfig> entries) { kinds.putAll(entries); return this; }

        public MobsConfig build() { return new MobsConfig(this); }
    }
}
