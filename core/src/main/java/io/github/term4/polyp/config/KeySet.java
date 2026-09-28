package io.github.term4.polyp.config;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which keys of a catalog a scope admits - "only these" or "everything except these". A selection over types
 * polyp already registers, not a way to define new ones, so it stays a plain predicate: a mode saying "only
 * melee and arrows hurt here" is choosing from the damage catalog, not authoring damage.
 *
 * <p>Registered for data paths, so a ruleset writes {@code damage/enabledTypes = only(minecraft:player_attack,
 * minecraft:thrown)} instead of restating one {@code enabled=false} per type; {@code without(minecraft:fall)} and
 * {@code with(...)} edit what the base admits instead of replacing it.
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
        return new Listed(false, Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(keys))));
    }

    /** Everything the catalog has, minus the listed keys. */
    static @NotNull KeySet except(@NotNull Key... keys) {
        return new Listed(true, Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(keys))));
    }

    /** An {@link #only} or {@link #except}: the one kind that writes back, its keys in the order given. */
    record Listed(boolean except, @NotNull Set<Key> keys) implements KeySet {

        @Override
        public boolean admits(@NotNull Key key) {
            return except != keys.contains(key);
        }

        @Override
        public @NotNull String toString() {
            return (except ? "except(" : "only(") + keys.stream().map(Key::asString).collect(Collectors.joining(", ")) + ")";
        }
    }

    /** {@code only(a, b)} or {@code except(a)}, a key without a namespace being {@code minecraft}'s. */
    static @NotNull KeySet parse(@NotNull String text) {
        String s = text.strip();
        boolean except = s.startsWith("except(");
        if (!except && !s.startsWith("only(") || !s.endsWith(")")) {
            throw new IllegalArgumentException("not only(...) or except(...): " + text);
        }
        String body = s.substring(s.indexOf('(') + 1, s.length() - 1).strip();
        List<Key> keys = new ArrayList<>();
        if (!body.isEmpty()) for (String key : body.split(",")) keys.add(Key.key(key.strip()));
        Key[] listed = keys.toArray(Key[]::new);
        return except ? except(listed) : only(listed);
    }

    /** Written as {@link #parse} reads it; a combined set has no text and does not encode. */
    Codec<KeySet> CODEC = Codec.STRING.transform(KeySet::parse, set -> {
        if (set instanceof Listed listed) return listed.toString();
        throw new IllegalArgumentException("only an only(...) or except(...) set is written");
    });

    /** Admitted by both. */
    default @NotNull KeySet and(@NotNull KeySet other) {
        return key -> admits(key) && other.admits(key);
    }

    /** Admitted by either. */
    default @NotNull KeySet or(@NotNull KeySet other) {
        return key -> admits(key) || other.admits(key);
    }

    static void registerFactories() {
        // no "all"/"none": they are except() and only() with nothing listed, and naming them would be naming cases
        FieldFns.register(KeySet.class, "only(key...)", "only the listed types; only() is nothing", args -> only(keys(args)));
        FieldFns.register(KeySet.class, "except(key...)", "every type but the listed; except() is everything", args -> except(keys(args)));
        // over what the base admits (everything, when it says nothing): a mode narrows or widens without restating
        FieldFns.registerMutation(KeySet.class, "without(key...)", "the inherited types minus the listed",
                (base, args) -> (base != null ? base : ALL).and(except(keys(args))));
        FieldFns.registerMutation(KeySet.class, "with(key...)", "the inherited types plus the listed",
                (base, args) -> (base != null ? base : ALL).or(only(keys(args))));
    }

    private static Key[] keys(FieldFns.Args args) {
        Key[] out = new Key[args.size()];
        for (int i = 0; i < out.length; i++) out[i] = args.key(i);
        return out;
    }
}
