package io.github.term4.polyp.mechanics.explosion;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.Config;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.entity.PrimedTnt;
import io.github.term4.polyp.mechanics.explosion.TntConfigResolver.TntContext;
import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Immutable primed-TNT config; unset knobs resolve to the vanilla values ({@link PrimedTnt#VANILLA}). {@code kinds}
 * are stamped items' own knobs over these (a Hypixel TNT in a vanilla world).
 */
@GenerateBuilder
public final class TntConfig extends Config<TntContext, TntConfig> {

    public final @Nullable FieldValue<TntContext, Integer> fuseTicks;
    public final @Nullable FieldValue<TntContext, Float> power;
    public final @Nullable FieldValue<TntContext, Boolean> detonateAtFeet;
    public final @Nullable FieldValue<TntContext, PrimedTnt.Wire> wire;
    public final @Nullable FieldValue<TntContext, Boolean> bounce;
    public final @Nullable FieldValue<TntContext, Double> tntVictimScale;
    public final @Nullable FieldValue<TntContext, Boolean> igniteOnPlace;
    public final Map<Key, TntConfig> kinds;

    private TntConfig(Builder b) {
        super(b.subConfig);
        this.fuseTicks = b.fuseTicks;
        this.power = b.power;
        this.detonateAtFeet = b.detonateAtFeet;
        this.wire = b.wire;
        this.bounce = b.bounce;
        this.tntVictimScale = b.tntVictimScale;
        this.igniteOnPlace = b.igniteOnPlace;
        this.kinds = Map.copyOf(b.kinds);
    }

    /** The kind's knobs over these, or null for a kind nobody registered. */
    public @Nullable TntConfig kind(Key kind) {
        TntConfig own = kinds.get(kind);
        return own != null ? own.fromBase(this) : null;
    }

    @Override
    public TntConfig fromBase(TntConfig base) {
        Map<Key, TntConfig> merged = new LinkedHashMap<>(base.kinds);
        kinds.forEach((key, kind) -> merged.merge(key, kind, (under, over) -> over.fromBase(under)));
        Builder b = new Builder();
        b.subConfig(subConfig != null ? subConfig : base.subConfig);
        b.mergeKnobs(this, base);
        return b.kinds(merged).build();
    }

    public static Builder builder() { return new Builder(); }

    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder extends TntConfigBuilderBase<Builder> {
        @Override protected Builder self() { return this; }

        private Function<TntContext, TntConfig> subConfig;
        private final Map<Key, TntConfig> kinds = new LinkedHashMap<>();

        Builder() {}

        Builder(TntConfig c) {
            super(c);
            subConfig = c.subConfig;
            kinds.putAll(c.kinds);
        }

        public Builder subConfig(Function<TntContext, TntConfig> fn) { subConfig = fn; return this; }
        public Builder kind(Key kind, TntConfig config) { kinds.put(kind, config); return this; }
        public Builder kinds(Map<Key, TntConfig> entries) { kinds.putAll(entries); return this; }

        public TntConfig build() { return new TntConfig(this); }
    }
}
