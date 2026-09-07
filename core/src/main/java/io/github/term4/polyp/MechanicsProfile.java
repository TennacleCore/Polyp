package io.github.term4.polyp;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A bundle of mechanics configs assignable to a scope (player / world / instance / global) via {@link MechanicsProfiles}.
 * Members are keyed by {@link ConfigKey} (built-ins in {@link MechanicsKeys}); a partial profile (e.g. knockback only)
 * overrides just that member and lets the rest fall through to the next scope. A member may also be
 * {@link Builder#target targeted}: filed whole for the players a predicate accepts, read ahead of the untargeted
 * one by {@link #get(ConfigKey, Entity)} - how a member with no knob table inside it still varies per seat.
 */
public final class MechanicsProfile {

    /** A member for the players {@code who} accepts. */
    public record Targeted<C>(@NotNull Predicate<Player> who, @NotNull C value) {}

    private final Map<ConfigKey<?>, Object> values;
    private final Map<ConfigKey<?>, List<Targeted<?>>> targeted;

    private MechanicsProfile(Map<ConfigKey<?>, Object> values, Map<ConfigKey<?>, List<Targeted<?>>> targeted) {
        this.values = values;
        this.targeted = targeted;
    }

    /** The member everyone reads - never a targeted one. */
    @SuppressWarnings("unchecked")
    public <C> @Nullable C get(ConfigKey<C> key) { return (C) values.get(key); }

    /** {@link #get} for {@code subject}: the latest targeted member naming them, else the untargeted; anything but a player reads the untargeted. */
    @SuppressWarnings("unchecked")
    public <C> @Nullable C get(ConfigKey<C> key, @Nullable Entity subject) {
        if (subject instanceof Player p) {
            List<Targeted<?>> entries = targeted.get(key);
            if (entries != null) {
                for (int i = entries.size() - 1; i >= 0; i--) {
                    Targeted<?> entry = entries.get(i);
                    if (entry.who().test(p)) return (C) entry.value();
                }
            }
        }
        return get(key);
    }

    /** The targeted members under {@code key}, earliest first; the last one naming a player wins. */
    @SuppressWarnings("unchecked")
    public <C> @NotNull List<Targeted<C>> targeted(ConfigKey<C> key) {
        List<Targeted<?>> entries = targeted.get(key);
        return entries == null ? List.of() : (List<Targeted<C>>) (List<?>) entries;
    }

    public Builder toBuilder() { return new Builder(values, targeted); }
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<ConfigKey<?>, Object> values;
        private final Map<ConfigKey<?>, List<Targeted<?>>> targeted = new HashMap<>();

        Builder() { this.values = new HashMap<>(); }

        Builder(Map<ConfigKey<?>, Object> base, Map<ConfigKey<?>, List<Targeted<?>>> targeted) {
            this.values = new HashMap<>(base);
            targeted.forEach((key, entries) -> this.targeted.put(key, new ArrayList<>(entries)));
        }

        /** {@code null} clears the member, its targeted entries with it. */
        public <C> Builder set(ConfigKey<C> key, @Nullable C value) {
            if (value == null) {
                values.remove(key);
                targeted.remove(key);
            } else {
                values.put(key, value);
            }
            return this;
        }

        /** The member currently in this builder, or {@code null}. */
        @SuppressWarnings("unchecked")
        public <C> @Nullable C get(ConfigKey<C> key) { return (C) values.get(key); }

        /**
         * Edits the member IN PLACE of replacing it: {@code edit} receives the builder's current value
         * ({@code null} if unset - seed with {@link #set} or fall back inside the edit) and its result is
         * stored. The composable route for sparse overrides - a wholesale {@code set} of a hand-assembled
         * config is how base tuning gets wiped.
         */
        public <C> Builder mutate(ConfigKey<C> key, java.util.function.UnaryOperator<@Nullable C> edit) {
            return set(key, edit.apply(get(key)));
        }

        /**
         * {@code value} as the member for the players {@code who} accepts, ahead of the untargeted one and of
         * every earlier target - so a seat's entry goes after its team's. Untouched by a later untargeted
         * {@link #set}; cleared with the member.
         */
        public <C> Builder target(ConfigKey<C> key, @NotNull Predicate<Player> who, @NotNull C value) {
            targeted.computeIfAbsent(key, k -> new ArrayList<>()).add(new Targeted<>(who, value));
            return this;
        }

        public MechanicsProfile build() {
            Map<ConfigKey<?>, List<Targeted<?>>> frozen = new HashMap<>();
            targeted.forEach((key, entries) -> frozen.put(key, List.copyOf(entries)));
            return new MechanicsProfile(Map.copyOf(values), Map.copyOf(frozen));
        }
    }
}
