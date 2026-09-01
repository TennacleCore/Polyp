package io.github.term4.polyp.config;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Named FACTORIES for behaviour-typed config values, so a data path can build one:
 * <pre>explosion/damageModel = flat(2.0)   fx/polyp:pearl_teleport = to(everywhere, sound(entity.player.teleport, player, 1, 1))</pre>
 * Not a fixed menu of instances - a factory takes arguments, so one registration covers every parameterisation,
 * and registering another extends the vocabulary for that type everywhere. Unknown names list what the type offers.
 *
 * <p>Parsing happens once, when a ruleset folds; what lands in the config is the built behaviour, so the runtime
 * path is the same lambda a preset would have written by hand.
 */
public final class FieldFns {

    private FieldFns() {}

    /** Builds a value of the registered type from a call's arguments. */
    @FunctionalInterface
    public interface Factory<T> {
        @NotNull T create(@NotNull Args args);
    }

    /** One registered way to build a value: what it is called, what it takes, and what it does. */
    public record Entry<T>(@NotNull String name, @NotNull String signature, @NotNull String doc, @NotNull Factory<T> factory) {}

    private static final Map<Class<?>, Map<String, Entry<?>>> BY_TYPE = new ConcurrentHashMap<>();

    /**
     * Registers {@code signature} as a way to build a {@code type}. The signature is the call shape a user
     * types ({@code within(blocks, audience)}); {@code doc} is one line explaining it. Both surface in errors
     * and in {@link #vocabulary}, which is the only reason anyone can discover this without reading the source.
     */
    public static <T> void register(@NotNull Class<T> type, @NotNull String signature, @NotNull String doc,
                                    @NotNull Factory<T> factory) {
        int paren = signature.indexOf('(');
        String name = (paren < 0 ? signature : signature.substring(0, paren)).trim();
        BY_TYPE.computeIfAbsent(type, t -> new ConcurrentHashMap<>())
                .put(name, new Entry<>(name, signature.trim(), doc, factory));
    }

    /** The factory names {@code type} offers, for errors and pickers. */
    public static @NotNull Set<String> names(@NotNull Class<?> type) {
        Map<String, Entry<?>> named = BY_TYPE.get(type);
        return named != null ? Set.copyOf(named.keySet()) : Set.of();
    }

    /** Every way to build a {@code type}, signature first, sorted - what a help command prints. */
    public static @NotNull List<String> vocabulary(@NotNull Class<?> type) {
        Map<String, Entry<?>> named = BY_TYPE.get(type);
        if (named == null) return List.of();
        return named.values().stream()
                .sorted(java.util.Comparator.comparing(Entry::name))
                .map(e -> e.signature() + " - " + e.doc())
                .toList();
    }

    /** The types with a registered vocabulary. */
    public static @NotNull Set<Class<?>> types() { return Set.copyOf(BY_TYPE.keySet()); }

    public static boolean supports(@NotNull Class<?> type) {
        return BY_TYPE.containsKey(type);
    }

    /**
     * Builds the value {@code spec} names: {@code name} or {@code name(arg, arg, ...)}. Throws
     * {@link IllegalArgumentException} naming the offending part - never returns a half-built value.
     */
    @SuppressWarnings("unchecked")
    public static <T> @NotNull T parse(@NotNull Class<T> type, @NotNull String spec, @NotNull String where) {
        Map<String, Entry<?>> named = BY_TYPE.get(type);
        if (named == null) {
            throw new IllegalArgumentException(type.getSimpleName() + " has no registered factories: " + where);
        }
        String trimmed = spec.trim();
        int open = trimmed.indexOf('(');
        String name = (open < 0 ? trimmed : trimmed.substring(0, open)).trim();
        if (open >= 0 && !trimmed.endsWith(")")) {
            throw new IllegalArgumentException("unbalanced '(' in '" + spec + "': " + where);
        }
        List<String> raw = open < 0 ? List.of() : split(trimmed.substring(open + 1, trimmed.length() - 1));
        Entry<?> entry = named.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("no " + type.getSimpleName() + " named '" + name + "' (known: "
                    + new java.util.TreeSet<>(named.keySet()) + "): " + where);
        }
        return (T) entry.factory().create(new Args(entry.signature(), raw, where));
    }

    /** Applies an already-registered factory to {@code args} - lets one type's vocabulary reuse another's. */
    @SuppressWarnings("unchecked")
    public static <T> @NotNull T build(@NotNull Class<T> type, @NotNull String name, @NotNull Args args) {
        Map<String, Entry<?>> named = BY_TYPE.get(type);
        Entry<?> entry = named != null ? named.get(name) : null;
        if (entry == null) throw new IllegalArgumentException("no " + type.getSimpleName() + " named '" + name + "'");
        return (T) entry.factory().create(args);
    }

    /** Splits on top-level commas so a nested call can be an argument later. */
    private static List<String> split(String body) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                out.add(body.substring(start, i));
                start = i + 1;
            }
        }
        String last = body.substring(start).trim();
        if (!last.isEmpty() || !out.isEmpty()) out.add(last);
        return out.stream().map(String::trim).toList();
    }

    /** A factory call's arguments, typed on access so a bad one names the factory and position. */
    public static final class Args {

        private final String fn;
        private final List<String> values;
        private final String where;

        Args(String fn, List<String> values, String where) {
            this.fn = fn;
            this.values = values;
            this.where = where;
        }

        public int size() { return values.size(); }

        /** Requires exactly {@code n} arguments. */
        public @NotNull Args arity(int n) {
            if (values.size() != n) {
                throw new IllegalArgumentException(fn + " takes " + n + " argument(s), got " + values.size() + ": " + where);
            }
            return this;
        }

        public @NotNull String str(int i) {
            if (i >= values.size()) {
                throw new IllegalArgumentException(fn + " is missing argument " + (i + 1) + ": " + where);
            }
            return values.get(i);
        }

        public double dbl(int i) { return parsed(i, Double::parseDouble, "a number"); }

        public float flt(int i) { return (float) dbl(i); }

        public int integer(int i) { return parsed(i, Integer::parseInt, "an integer"); }

        /** A namespaced id; a bare name takes the {@code minecraft} namespace. */
        public @NotNull Key key(int i) {
            return parsed(i, v -> v.indexOf(':') < 0 ? Key.key("minecraft", v) : Key.key(v), "a key");
        }

        public <E extends Enum<E>> @NotNull E enumOf(int i, @NotNull Class<E> type) {
            return parsed(i, v -> Enum.valueOf(type, v.toUpperCase(Locale.ROOT).replace('-', '_')),
                    "one of " + java.util.Arrays.toString(type.getEnumConstants()));
        }

        /** A nested factory call as an argument: {@code to(everywhere, sound(...))}. */
        public <T> @NotNull T of(int i, @NotNull Class<T> type) {
            return FieldFns.parse(type, str(i), where);
        }

        /** {@code i}-th argument, or {@code fallback} when absent. */
        public double dblOr(int i, double fallback) { return i < values.size() ? dbl(i) : fallback; }

        private <R> R parsed(int i, Function<String, R> parse, String expected) {
            String v = str(i);
            try {
                return parse.apply(v);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(fn + " argument " + (i + 1) + " ('" + v + "') must be "
                        + expected + ": " + where);
            }
        }
    }
}
