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
    /** The tracker's gates on what of a player's movement goes out ({@link PositionRelay}); unset, every move at the interval. */
    public final @Nullable FieldValue<PlayerContext, PositionRelay> positionRelay;
    /**
     * Whether a count change on the stack in use (a Q drop, a pickup) ends the client's use, as clients through 1.14
     * do; the server's use runs on either way. Unset leaves each client as it is.
     */
    public final @Nullable FieldValue<PlayerContext, Boolean> countChangeEndsUse;
    /** Where the cursor item goes when its window closes. Unset leaves it on the cursor, as Minestom does. */
    public final @Nullable FieldValue<PlayerContext, CursorOnClose> cursorOnClose;

    /**
     * The tracker's gates on a player's relayed movement ({@code EntityTrackerEntry}). {@code impulse}: a knockback, a
     * jump or a metadata change is checked off the beat. Moves under {@code move} blocks per axis and turns under
     * {@code turn} degrees wait, except on every {@code keepAlive}th tick. {@code groundSync}: an on-ground change goes
     * out absolute. {@code fullSync}: ticks between absolute syncs.
     */
    public record PositionRelay(boolean impulse, double move, double turn, int keepAlive, boolean groundSync, int fullSync) {
        /** 1.8; {@code fullSync} is its 400 updates at 2 ticks each. */
        public static final PositionRelay LEGACY = new PositionRelay(true, 4 / 32.0, 360 * 4 / 256.0, 60, true, 800);
        /** Minestom's own. */
        public static final PositionRelay EVERY_MOVE = new PositionRelay(false, 0, 0, 0, false, 20);
    }

    public enum CursorOnClose {
        /** Through 1.17. */
        DROP,
        /** From 1.17.1: into the inventory as 26.1 places it back, dropping what does not fit. */
        RETURN
    }

    private PlayerConfig(Builder b) {
        positionBroadcastInterval = b.positionBroadcastInterval;
        positionRelay = b.positionRelay;
        countChangeEndsUse = b.countChangeEndsUse;
        cursorOnClose = b.cursorOnClose;
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
