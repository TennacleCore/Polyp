package io.github.term4.polyp.platform.fixes.visuals.legacy_1_8;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.platform.fixes.FixToggleConfig.FixContext;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** The 1.8 arrow-visibility fix's knobs, per scope; the subject is the shooter. */
@GenerateBuilder
public final class LegacyArrowVisibilityConfig {

    public final @Nullable FieldValue<FixContext, Boolean> enabled;
    public final @Nullable FieldValue<FixContext, Boolean> deflectParticles;

    private LegacyArrowVisibilityConfig(Builder b) {
        this.enabled = b.enabled;
        this.deflectParticles = b.deflectParticles;
    }

    public boolean enabled(@Nullable Entity subject) {
        return Boolean.TRUE.equals(FieldValue.resolve(enabled, new FixContext(subject)));
    }

    public boolean deflectParticles(@Nullable Entity subject) {
        return Boolean.TRUE.equals(FieldValue.resolve(deflectParticles, new FixContext(subject)));
    }

    public LegacyArrowVisibilityConfig fromBase(LegacyArrowVisibilityConfig base) {
        Builder b = new Builder();
        b.mergeKnobs(this, base);
        return b.build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable LegacyArrowVisibilityConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder extends LegacyArrowVisibilityConfigBuilderBase<Builder> {
        @Override protected Builder self() { return this; }
        Builder() {}
        Builder(LegacyArrowVisibilityConfig c) { super(c); }
        public LegacyArrowVisibilityConfig build() { return new LegacyArrowVisibilityConfig(this); }
    }
}
