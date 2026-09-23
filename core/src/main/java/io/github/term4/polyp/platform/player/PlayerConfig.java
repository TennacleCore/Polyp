package io.github.term4.polyp.platform.player;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.config.SubjectContext;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable player platform config (per-player server behavior, not combat mechanics). Scoped via
 * {@code MechanicsProfile.player} and applied at spawn (join / instance change) by {@link PlayerConfigApplier}.
 * Unset fields are left unmanaged.
 */
@GenerateBuilder
public final class PlayerConfig {

    /** What a player platform knob resolves against: the player it is applied to. */
    public record PlayerContext(@Nullable Entity subject) implements SubjectContext {}

    /** Position broadcast interval in ticks (1 = every tick, the Minestom default). */
    public final @Nullable FieldValue<PlayerContext, Integer> positionBroadcastInterval;
    /**
     * Whether a count change on the stack in use (a Q drop, a pickup) ends the client's use, as clients through 1.14
     * do; the server's use runs on either way. Unset leaves each client as it is.
     */
    public final @Nullable FieldValue<PlayerContext, Boolean> countChangeEndsUse;

    private PlayerConfig(Builder b) {
        positionBroadcastInterval = b.positionBroadcastInterval;
        countChangeEndsUse = b.countChangeEndsUse;
    }

    public Builder toBuilder() { return new Builder(this); }

    public static Builder builder() { return builder(null); }
    public static Builder builder(@Nullable PlayerConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder extends PlayerConfigBuilderBase<Builder> {

        @Override protected Builder self() { return this; }

        Builder() {}
        Builder(PlayerConfig c) { super(c); }

        public PlayerConfig build() { return new PlayerConfig(this); }
    }
}
