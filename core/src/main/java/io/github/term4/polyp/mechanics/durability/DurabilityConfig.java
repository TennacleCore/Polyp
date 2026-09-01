package io.github.term4.polyp.mechanics.durability;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.config.SubjectContext;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** Per-scope item durability, via the {@code durability} profile member. */
@GenerateBuilder
public final class DurabilityConfig {

    /** What a durability knob resolves against: the holder whose item wears. */
    public record DurabilityContext(@Nullable Entity subject) implements SubjectContext {}

    /** Unset = active. */
    public final @Nullable FieldValue<DurabilityContext, Boolean> enabled;

    private DurabilityConfig(Builder b) { this.enabled = b.enabled; }

    public DurabilityConfig fromBase(DurabilityConfig base) {
        Builder b = new Builder();
        b.mergeKnobs(this, base);
        return b.build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable DurabilityConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder extends DurabilityConfigBuilderBase<Builder> {
        @Override protected Builder self() { return this; }
        Builder() {}
        Builder(DurabilityConfig c) { super(c); }
        public DurabilityConfig build() { return new DurabilityConfig(this); }
    }
}
