package io.github.term4.polyp.platform.fixes;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.config.SubjectContext;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** One client fix's switch. Install-time fixes read it with no subject; per-player ones with the player. */
@GenerateBuilder
public final class FixToggleConfig {

    /** What a fix knob resolves against: the player the fix would apply to, or nothing at install time. */
    public record FixContext(@Nullable Entity subject) implements SubjectContext {}

    public final @Nullable FieldValue<FixContext, Boolean> enabled;

    private FixToggleConfig(Builder b) { this.enabled = b.enabled; }

    private static final FixToggleConfig ON = builder().enabled(true).build();
    private static final FixToggleConfig OFF = builder().enabled(false).build();

    public static FixToggleConfig on() { return ON; }
    public static FixToggleConfig of(boolean enabled) { return enabled ? ON : OFF; }

    /** For {@code subject}; unset is off. */
    public boolean enabled(@Nullable Entity subject) {
        return Boolean.TRUE.equals(FieldValue.resolve(enabled, new FixContext(subject)));
    }

    /** The install-time read: no subject, so a targeted value answers with its fallback. */
    public boolean enabled() { return enabled(null); }

    public FixToggleConfig fromBase(FixToggleConfig base) {
        Builder b = new Builder();
        b.mergeKnobs(this, base);
        return b.build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }

    public static final class Builder extends FixToggleConfigBuilderBase<Builder> {
        @Override protected Builder self() { return this; }
        Builder() {}
        Builder(FixToggleConfig c) { super(c); }
        public FixToggleConfig build() { return new FixToggleConfig(this); }
    }
}
