package io.github.term4.polyp.config;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Which keys of a catalog a scope admits - "only these" or "everything except these". A selection over types
 * polyp already registers, not a way to define new ones, so it stays a plain predicate: a mode saying "only
 * melee and arrows hurt here" is choosing from the damage catalog, not authoring damage.
 *
 * <p>Registered for data paths, so a ruleset writes {@code damage/enabledTypes = only(minecraft:player_attack,
 * minecraft:thrown)} instead of restating one {@code enabled=false} per type.
 */
@FunctionalInterface
public interface KeySet {

    boolean admits(@NotNull Key key);

    /** Everything the catalog has - the default. */
    KeySet ALL = key -> true;

    /** Nothing at all. */
    KeySet NONE = key -> false;

    /** Only the listed keys. */
    static @NotNull KeySet only(@NotNull Key... keys) {
        Set<Key> allowed = new LinkedHashSet<>(Arrays.asList(keys));
        return allowed::contains;
    }

    /** Everything the catalog has, minus the listed keys. */
    static @NotNull KeySet except(@NotNull Key... keys) {
        Set<Key> denied = new LinkedHashSet<>(Arrays.asList(keys));
        return key -> !denied.contains(key);
    }

    /** Admitted by both. */
    default @NotNull KeySet and(@NotNull KeySet other) {
        return key -> admits(key) && other.admits(key);
    }

    static void registerFactories() {
        // no "all"/"none": they are except() and only() with nothing listed, and naming them would be naming cases
        FieldFns.register(KeySet.class, "only(key...)", "only the listed types; only() is nothing", args -> only(keys(args)));
        FieldFns.register(KeySet.class, "except(key...)", "every type but the listed; except() is everything", args -> except(keys(args)));
    }

    private static Key[] keys(FieldFns.Args args) {
        Key[] out = new Key[args.size()];
        for (int i = 0; i < out.length; i++) out[i] = args.key(i);
        return out;
    }
}
