package io.github.term4.polyp.mechanics.hunger;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.config.SubjectContext;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Config for the hunger subsystem, assigned per scope via the {@code hunger} profile member. The PRESETS declare
 * every cost: each source charges through its {@link ExhaustionCost} entry (no entry = {@link ExhaustionCost#dynamic()}
 * for custom keys, inert for lib keys), times the global {@code exhaustionScale}.
 */
@GenerateBuilder
public final class HungerConfig {

    /** What a hunger knob resolves against: the player being fed, starved or charged. */
    public record HungerContext(@Nullable Entity subject) implements SubjectContext {}

    /** Unset = active. */
    public final @Nullable FieldValue<HungerContext, Boolean> enabled;
    /** The {@code naturalRegeneration} gamerule analog (unset = on). */
    public final @Nullable FieldValue<HungerContext, Boolean> naturalRegen;
    /** Food level required to regenerate (vanilla 18, both versions). */
    public final @Nullable FieldValue<HungerContext, Integer> regenFoodThreshold;
    /** Ticks between regen heals (vanilla 80, both versions). */
    public final @Nullable FieldValue<HungerContext, Integer> regenInterval;
    /** Modern saturation fast regen: at food 20 with saturation left, heal {@code min(sat,6)/6} every 10 ticks (1.9+; 1.8 has none). */
    public final @Nullable FieldValue<HungerContext, Boolean> saturationRegen;
    /** Global multiplier on every exhaustion cost (unset = 1; 0 = hunger never depletes, regen still heals). */
    public final @Nullable FieldValue<HungerContext, Float> exhaustionScale;
    /** Per-source cost rules, keyed by the {@link HungerSystem#exhaust} source. */
    public final Map<Key, ExhaustionCost> exhaustionCosts;

    private HungerConfig(Builder b) {
        this.enabled = b.enabled;
        this.naturalRegen = b.naturalRegen;
        this.regenFoodThreshold = b.regenFoodThreshold;
        this.regenInterval = b.regenInterval;
        this.saturationRegen = b.saturationRegen;
        this.exhaustionScale = b.exhaustionScale;
        this.exhaustionCosts = Map.copyOf(b.exhaustionCosts);
    }

    /** The cost rule for {@code source}, or null = {@link ExhaustionCost#dynamic()}. */
    public @Nullable ExhaustionCost exhaustionCost(Key source) { return exhaustionCosts.get(source); }

    /** Merges this config over {@code base}; per-source costs overlay entry-wise. */
    public HungerConfig fromBase(HungerConfig base) {
        Builder b = new Builder();
        b.mergeKnobs(this, base);
        b.exhaustionCosts.putAll(base.exhaustionCosts);
        b.exhaustionCosts.putAll(exhaustionCosts);
        return b.build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable HungerConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder extends HungerConfigBuilderBase<Builder> {

        @Override protected Builder self() { return this; }

        private final Map<Key, ExhaustionCost> exhaustionCosts = new LinkedHashMap<>();

        Builder() {}
        Builder(HungerConfig c) {
            super(c);
            exhaustionCosts.putAll(c.exhaustionCosts);
        }

        public Builder exhaustionCost(Key source, ExhaustionCost cost) { exhaustionCosts.put(source, cost); return this; }

        public HungerConfig build() { return new HungerConfig(this); }
    }
}
