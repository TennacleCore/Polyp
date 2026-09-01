package io.github.term4.polyp;

import io.github.term4.polyp.world.MechanicsWorld;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minestom.server.entity.Entity;
import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.tag.Tag;
import net.minestom.server.tag.Taggable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Scoped {@link MechanicsProfile} registry: assign profiles per player, per world (a virtual game world), per instance,
 * or globally. Configs are immutable, so a runtime swap takes effect on the next hit. Every assignment takes
 * {@code null} to clear. Resolution is per <em>member</em>, highest scope first:
 * <pre>player profile -> world profile -> instance profile -> global profile -> the system's install config</pre>
 * so a partial profile (e.g. knockback only) overrides just that system. Resolve a single member with {@link #resolve};
 * a hit reading several members should use {@link #resolved} to walk the scopes once.
 *
 * <p><b>Scope subject.</b> Attack resolves against the <em>attacker</em>; damage and knockback resolve against the
 * <em>victim</em>. Both usually share the instance, so the distinction only matters for player overrides.
 *
 * <p>Player and instance assignments live in transient tags, so they clean up with their holder (a player's profile
 * drops on disconnect).
 */
public final class MechanicsProfiles {

    private static final Tag<MechanicsProfile> PROFILE = Tag.Transient("polyp:profile");
    /** Player scope is a stack of owned contributions, newest LAST. Immutable and swapped WHOLESALE through
     *  {@code updateTag}: a game tick and a join land on different threads. A list, not a map, because the
     *  read is hot (every hit) and wants the newest in O(1) - the rewrite on write is the rare path. */
    private static final Tag<List<Contribution>> CONTRIBUTIONS = Tag.Transient("polyp:profile-stack");

    /** @param where which worlds it applies in, or {@code null} to follow the player everywhere */
    private record Contribution(Key owner, MechanicsProfile profile,
                                @Nullable Predicate<MechanicsWorld> where) {}

    private volatile @Nullable MechanicsProfile global;
    // fires with the affected player, or null for a wider scope (global/world/instance)
    private volatile @Nullable Consumer<@Nullable Player> changeHook;

    MechanicsProfiles() {}

    void onChange(Consumer<@Nullable Player> hook) { this.changeHook = hook; }

    private void changed(@Nullable Player player) {
        var hook = changeHook;
        if (hook != null) hook.accept(player);
    }

    /** The server-wide fallback profile. */
    public void setGlobal(@Nullable MechanicsProfile profile) {
        this.global = profile;
        changed(null);
    }
    public @Nullable MechanicsProfile global() { return global; }

    /** Re-fires the change hook - call after moving a player between worlds. */
    public void refresh() { changed(null); }

    private static void assign(Taggable holder, @Nullable MechanicsProfile profile) {
        if (profile == null) holder.removeTag(PROFILE);
        else holder.setTag(PROFILE, profile);
    }

    public void setWorld(MechanicsWorld world, @Nullable MechanicsProfile profile) {
        assign(world, profile);
        changed(null);
    }
    public @Nullable MechanicsProfile world(MechanicsWorld world) { return world.getTag(PROFILE); }

    public void setInstance(Instance instance, @Nullable MechanicsProfile profile) {
        assign(instance, profile);
        changed(null);
    }
    public @Nullable MechanicsProfile instance(Instance instance) { return instance.getTag(PROFILE); }

    /** Sets the ANONYMOUS contribution ({@link #DEFAULT_OWNER}); see {@link #setPlayer(Player, Key, MechanicsProfile)}. */
    public void setPlayer(Player player, @Nullable MechanicsProfile profile) {
        setPlayer(player, DEFAULT_OWNER, profile);
    }

    /**
     * The player's EFFECTIVE profile: the newest contribution that APPLIES WHERE THEY ARE, or {@code null}.
     * A world-bound contribution is inert outside its world, so a lobby's per-player setup cannot outrank the
     * game world a player walked into - the thing that made player scope dangerous for ambient setup.
     */
    public @Nullable MechanicsProfile player(Player player) {
        List<Contribution> stack = player.getTag(CONTRIBUTIONS);
        if (stack == null || stack.isEmpty()) return null;
        MechanicsWorld here = player.getInstance() != null ? MechanicsWorld.of(player) : null;
        for (int i = stack.size() - 1; i >= 0; i--) {
            Contribution held = stack.get(i);
            if (held.where() == null || (here != null && held.where().test(here))) return held.profile();
        }
        return null;
    }

    /** The owner of the anonymous {@link #setPlayer(Player, MechanicsProfile)} overload. */
    public static final Key DEFAULT_OWNER = Key.key("polyp:player-profile");

    /**
     * Contributes {@code profile} to {@code player} under {@code owner}. The NEWEST contribution is the
     * player scope outright - lower ones stay put, shadowed, and re-emerge when it is cleared; a caller who
     * wants a blend builds the blended profile and contributes that. Re-setting an existing owner keeps its
     * position, so a mid-game refit cannot leapfrog a contribution added after it.
     *
     * <p>Every scope below (world, instance, global) still resolves per member as always, so a partial
     * profile here overrides only what it sets.
     */
    public void setPlayer(Player player, @NotNull Key owner, @Nullable MechanicsProfile profile) {
        setPlayer(player, owner, profile, null);
    }

    /**
     * {@link #setPlayer(Player, Key, MechanicsProfile)} bound to the worlds {@code where} accepts: it applies
     * only while the player is in one of them and goes inert (not lost) anywhere else. One world is
     * {@code w -> w == lobby}; a nested shard tree is {@code w -> w.isUnder(shard)}; a handful is
     * {@code set::contains}. {@code null} follows the player everywhere.
     *
     * <p>Keep the test cheap - it runs whenever the player's scope resolves. {@link MechanicsWorld#isUnder}
     * is O(depth); a snapshot of {@link MechanicsWorld#family()} would miss layers added later.
     */
    public void setPlayer(Player player, @NotNull Key owner, @Nullable MechanicsProfile profile,
                          @Nullable Predicate<MechanicsWorld> where) {
        player.updateTag(CONTRIBUTIONS, stack -> {
            List<Contribution> current = stack != null ? stack : List.of();
            List<Contribution> next = new ArrayList<>(current.size() + 1);
            boolean replaced = false;
            for (Contribution held : current) {
                if (!held.owner().equals(owner)) next.add(held);
                else if (profile != null) { next.add(new Contribution(owner, profile, where)); replaced = true; }
            }
            if (profile != null && !replaced) next.add(new Contribution(owner, profile, where));
            return next.isEmpty() ? null : List.copyOf(next);
        });
        changed(player);
    }

    /** Drops {@code owner}'s contribution; whatever it shadowed applies again. */
    public void clearPlayer(Player player, @NotNull Key owner) {
        setPlayer(player, owner, null);
    }

    /**
     * Drops {@code owner}'s contribution from every online player - the one call a game makes when it ends,
     * so nobody carries its mechanics into wherever they go next. Leaving players should be cleared as they
     * go; this is the backstop for whoever is still aboard (and for an end path that threw).
     */
    public void clearOwner(@NotNull Key owner) {
        for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            if (contributions(player).containsKey(owner)) clearPlayer(player, owner);
        }
    }

    /** {@code player}'s contributions, oldest first - the newest is the one in force. For inspection. */
    public @NotNull Map<Key, MechanicsProfile> contributions(Player player) {
        List<Contribution> stack = player.getTag(CONTRIBUTIONS);
        if (stack == null || stack.isEmpty()) return Map.of();
        Map<Key, MechanicsProfile> out = new LinkedHashMap<>();
        for (Contribution held : stack) out.put(held.owner(), held.profile());
        return Collections.unmodifiableMap(out);
    }

    // single-member assignment: merges into the scope's existing profile instead of replacing it

    private static MechanicsProfile with(@Nullable MechanicsProfile base, ConfigKey<?> key, @Nullable Object value) {
        MechanicsProfile.Builder b = base != null ? base.toBuilder() : MechanicsProfile.builder();
        @SuppressWarnings("unchecked")
        ConfigKey<Object> typed = (ConfigKey<Object>) key;
        return b.set(typed, value).build();
    }

    public <C> void setGlobal(ConfigKey<C> key, @Nullable C value) {
        setGlobal(with(global, key, value));
    }

    public <C> void setWorld(MechanicsWorld world, ConfigKey<C> key, @Nullable C value) {
        setWorld(world, with(world(world), key, value));
    }

    public <C> void setInstance(Instance instance, ConfigKey<C> key, @Nullable C value) {
        setInstance(instance, with(instance(instance), key, value));
    }

    public <C> void setPlayer(Player player, ConfigKey<C> key, @Nullable C value) {
        setPlayer(player, DEFAULT_OWNER, with(contributions(player).get(DEFAULT_OWNER), key, value));
    }

    /** The effective value of {@code key} for {@code subject}. For a hit reading several members, prefer {@link #resolved}. */
    public <C> @Nullable C resolve(@Nullable Entity subject, ConfigKey<C> key) {
        if (subject != null) {
            if (subject instanceof Player p) {
                C v = memberOf(player(p), key);
                if (v != null) return v;
            }
            Instance in = subject.getInstance();
            if (in != null) {
                C v = memberOf(MechanicsWorld.of(subject).getTag(PROFILE), key);
                if (v != null) return v;
                v = memberOf(in.getTag(PROFILE), key);
                if (v != null) return v;
            }
        }
        return memberOf(global, key);
    }

    /** The effective value of {@code key} for a WORLD with no entity in hand - a sourceless explosion, a
     *  block-driven effect. Walks the same chain minus the player: shard/world -&gt; instance -&gt; global. */
    public <C> @Nullable C resolveWorld(@Nullable MechanicsWorld world, ConfigKey<C> key) {
        if (world != null) {
            C v = memberOf(world.getTag(PROFILE), key);
            if (v != null) return v;
            Instance in = world.instance();
            if (in != null) {
                v = memberOf(in.getTag(PROFILE), key);
                if (v != null) return v;
            }
        }
        return memberOf(global, key);
    }

    /** {@link #resolve} with a fallback: the effective value of {@code key} for {@code subject}, else {@code fallback}. */
    public <C> C resolveOr(@Nullable Entity subject, ConfigKey<C> key, C fallback) {
        C scoped = resolve(subject, key);
        return scoped != null ? scoped : fallback;
    }

    private static <C> @Nullable C memberOf(@Nullable MechanicsProfile profile, ConfigKey<C> key) {
        return profile != null ? profile.get(key) : null;
    }

    /** Snapshots {@code subject}'s scopes once, then answers any key off it - one scope walk for a whole hit. */
    public Resolved resolved(@Nullable Entity subject) {
        MechanicsProfile player = subject instanceof Player p ? player(p) : null;
        MechanicsProfile world = null;
        MechanicsProfile instance = null;
        if (subject != null) {
            Instance in = subject.getInstance();
            if (in != null) {
                world = MechanicsWorld.of(subject).getTag(PROFILE);
                instance = in.getTag(PROFILE);
            }
        }
        return new Resolved(player, world, instance, global);
    }

    /** A one-shot resolution view over fixed player / world / instance / global scopes. */
    public static final class Resolved {
        private final @Nullable MechanicsProfile player;
        private final @Nullable MechanicsProfile world;
        private final @Nullable MechanicsProfile instance;
        private final @Nullable MechanicsProfile global;

        private Resolved(@Nullable MechanicsProfile player, @Nullable MechanicsProfile world,
                         @Nullable MechanicsProfile instance, @Nullable MechanicsProfile global) {
            this.player = player;
            this.world = world;
            this.instance = instance;
            this.global = global;
        }

        public <C> @Nullable C get(ConfigKey<C> key) {
            C v;
            if (player != null && (v = player.get(key)) != null) return v;
            if (world != null && (v = world.get(key)) != null) return v;
            if (instance != null && (v = instance.get(key)) != null) return v;
            return global != null ? global.get(key) : null;
        }
    }
}
