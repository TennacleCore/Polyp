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
 * <pre>explosion/damageModel = flat(2.0)   fx/polyp:pearl_teleport = to(at-listener(watchers), sound(entity.player.teleport, player, 1, 1))</pre>
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

    /** Builds a value FROM the inherited one - the edit form, where a {@link Factory} replaces it whole. */
    @FunctionalInterface
    public interface Mutation<T> {
        /** {@code inherited} is {@code null} when no base sets the knob; the mutation decides what that means. */
        @NotNull T apply(@Nullable T inherited, @NotNull Args args);
    }

    /** One registered mutation: its name, call shape, one line of doc, and the edit. */
    public record MutationEntry<T>(@NotNull String name, @NotNull String signature, @NotNull String doc,
                                   @NotNull Mutation<T> mutation) {}

    private static final Map<Class<?>, Map<String, MutationEntry<?>>> MUTATIONS = new ConcurrentHashMap<>();

    /**
     * Registers {@code signature} as a way to build a {@code type}. The signature is the call shape a user
     * types ({@code within(blocks, audience)}); {@code doc} is one line explaining it. Both surface in errors
     * and in {@link #vocabulary}, which is the only reason anyone can discover this without reading the source.
     */
    public static <T> void register(@NotNull Class<T> type, @NotNull String signature, @NotNull String doc,
                                    @NotNull Factory<T> factory) {
        Vocabulary.ensure(); // a mode's name must meet the shipped ones NOW, not at somebody's first parse
        String name = head(signature);
        Map<String, MutationEntry<?>> mutations = MUTATIONS.get(type);
        if (mutations != null && mutations.containsKey(name)) {
            throw new IllegalStateException(type.getSimpleName() + " already edits with '" + name + "'; a factory cannot share the name");
        }
        Entry<?> clash = BY_TYPE.computeIfAbsent(type, t -> new ConcurrentHashMap<>())
                .putIfAbsent(name, new Entry<>(name, signature.trim(), doc, factory));
        // last-write-wins would let a mode silently replace a shipped factory (or its own, re-installed)
        if (clash != null) {
            throw new IllegalStateException(type.getSimpleName() + " already has '" + name + "' (" + clash.signature()
                    + "); unregister it first if the replacement is deliberate");
        }
    }

    /**
     * The registered nullary name whose product IS {@code value}, or {@code null}: the reverse of {@link #parse}
     * for constants, so a behaviour can be written to NBT by the name it was built from.
     */
    public static <T> @Nullable String nameOf(@NotNull Class<T> type, @NotNull T value) {
        Vocabulary.ensure();
        Map<String, Entry<?>> named = BY_TYPE.get(type);
        if (named == null) return null;
        for (Entry<?> e : named.values()) {
            if (e.signature().contains("(")) continue;
            if (e.factory().create(new Args(e.signature(), List.of(), "nameOf")).equals(value)) return e.name();
        }
        return null;
    }

    /** Removes {@code name} for {@code type}; {@code false} when it was not registered. Tests and re-installs use this. */
    public static boolean unregister(@NotNull Class<?> type, @NotNull String name) {
        Vocabulary.ensure();
        Map<String, Entry<?>> named = BY_TYPE.get(type);
        return named != null && named.remove(name) != null;
    }

    /**
     * Registers {@code signature} as a way to EDIT a {@code type} the base already holds, where a factory replaces
     * it: {@code damage/enabledTypes = without(minecraft:fall)} hands the mutation the inherited constant. The
     * worked example is {@link KeySet}'s {@code with}/{@code without}; any list-shaped knob follows the same shape.
     */
    public static <T> void registerMutation(@NotNull Class<T> type, @NotNull String signature, @NotNull String doc,
                                            @NotNull Mutation<T> mutation) {
        Vocabulary.ensure();
        String name = head(signature);
        Map<String, Entry<?>> factories = BY_TYPE.get(type);
        if (factories != null && factories.containsKey(name)) {
            throw new IllegalStateException(type.getSimpleName() + " already builds with '" + name + "'; a mutation cannot share the name");
        }
        MutationEntry<?> clash = MUTATIONS.computeIfAbsent(type, t -> new ConcurrentHashMap<>())
                .putIfAbsent(name, new MutationEntry<>(name, signature.trim(), doc, mutation));
        if (clash != null) {
            throw new IllegalStateException(type.getSimpleName() + " already has the mutation '" + name + "' ("
                    + clash.signature() + "); unregister it first if the replacement is deliberate");
        }
    }

    public static boolean unregisterMutation(@NotNull Class<?> type, @NotNull String name) {
        Vocabulary.ensure();
        Map<String, MutationEntry<?>> named = MUTATIONS.get(type);
        return named != null && named.remove(name) != null;
    }

    /** Whether {@code spec} names a mutation of {@code type} - an edit of the inherited value, not a fresh one. */
    public static boolean mutates(@NotNull Class<?> type, @NotNull String spec) {
        Vocabulary.ensure();
        Map<String, MutationEntry<?>> named = MUTATIONS.get(type);
        return named != null && named.containsKey(head(spec));
    }

    /** Applies the mutation {@code spec} names to {@code inherited}; {@link #mutates} says whether it names one. */
    @SuppressWarnings("unchecked")
    public static <T> @NotNull T mutate(@NotNull Class<T> type, @NotNull String spec, @Nullable T inherited,
                                        @NotNull String where) {
        Vocabulary.ensure();
        Map<String, MutationEntry<?>> named = MUTATIONS.getOrDefault(type, Map.of());
        Call call = call(spec, where);
        MutationEntry<T> entry = (MutationEntry<T>) named.get(call.name());
        if (entry == null) {
            throw new IllegalArgumentException("no " + type.getSimpleName() + " mutation named '" + call.name()
                    + "' (known: " + new java.util.TreeSet<>(named.keySet()) + "): " + where);
        }
        return entry.mutation().apply(inherited, new Args(entry.signature(), call.args(), where));
    }

    private static String head(String spec) {
        int paren = spec.indexOf('(');
        return (paren < 0 ? spec : spec.substring(0, paren)).trim();
    }

    private record Call(String name, List<String> args) {}

    private static Call call(String spec, String where) {
        String trimmed = spec.trim();
        int open = trimmed.indexOf('(');
        if (open >= 0 && !trimmed.endsWith(")")) {
            throw new IllegalArgumentException("unbalanced '(' in '" + spec + "': " + where);
        }
        return new Call(head(trimmed), open < 0 ? List.of() : split(trimmed.substring(open + 1, trimmed.length() - 1)));
    }

    /** Builds a value for a name no registered factory matches, or returns {@code null} to decline. */
    @FunctionalInterface
    public interface Fallback<T> {
        @Nullable T create(@NotNull String name, @NotNull Args args);
    }

    private record FallbackEntry<T>(@NotNull String doc, @NotNull Fallback<T> fallback) {}

    private static final Map<Class<?>, FallbackEntry<?>> FALLBACKS = new ConcurrentHashMap<>();

    /**
     * A catch-all consulted after the named factories - how one vocabulary can lend its names to another
     * (a bare {@code sound(...)} as an {@code FxHandler}) without snapshotting them at registration time.
     */
    public static <T> void fallback(@NotNull Class<T> type, @NotNull String doc, @NotNull Fallback<T> fallback) {
        Vocabulary.ensure();
        FallbackEntry<?> clash = FALLBACKS.putIfAbsent(type, new FallbackEntry<>(doc, fallback));
        if (clash != null) {
            throw new IllegalStateException(type.getSimpleName() + " already has a fallback (" + clash.doc()
                    + "); clearFallback it first if the replacement is deliberate");
        }
    }

    public static boolean clearFallback(@NotNull Class<?> type) {
        Vocabulary.ensure();
        return FALLBACKS.remove(type) != null;
    }

    /** The factory names {@code type} offers, for errors and pickers. */
    public static @NotNull Set<String> names(@NotNull Class<?> type) {
        Vocabulary.ensure();
        Map<String, Entry<?>> named = BY_TYPE.get(type);
        return named != null ? Set.copyOf(named.keySet()) : Set.of();
    }

    /** Every way to build a {@code type}, signature first, sorted - what a help command prints. */
    public static @NotNull List<String> vocabulary(@NotNull Class<?> type) {
        Vocabulary.ensure();
        Map<String, Entry<?>> named = BY_TYPE.get(type);
        List<String> out = new ArrayList<>();
        if (named != null) {
            named.values().stream().sorted(java.util.Comparator.comparing(Entry::name))
                    .map(e -> e.signature() + " - " + e.doc()).forEach(out::add);
        }
        Map<String, MutationEntry<?>> mutations = MUTATIONS.get(type);
        if (mutations != null) {
            mutations.values().stream().sorted(java.util.Comparator.comparing(MutationEntry::name))
                    .map(e -> e.signature() + " - " + e.doc() + " (edits the inherited value)").forEach(out::add);
        }
        FallbackEntry<?> fallback = FALLBACKS.get(type);
        if (fallback != null) out.add(fallback.doc());
        return List.copyOf(out);
    }

    /** The types with a registered vocabulary. */
    public static @NotNull Set<Class<?>> types() {
        Vocabulary.ensure();
        return Set.copyOf(BY_TYPE.keySet());
    }

    public static boolean supports(@NotNull Class<?> type) {
        Vocabulary.ensure();
        return BY_TYPE.containsKey(type) || FALLBACKS.containsKey(type);
    }

    /**
     * Builds the value {@code spec} names: {@code name} or {@code name(arg, arg, ...)}. Throws
     * {@link IllegalArgumentException} naming the offending part - never returns a half-built value.
     */
    @SuppressWarnings("unchecked")
    public static <T> @NotNull T parse(@NotNull Class<T> type, @NotNull String spec, @NotNull String where) {
        Vocabulary.ensure();
        // an empty vocabulary reports like an unknown name: one message shape, and it lists what exists (nothing)
        Map<String, Entry<?>> named = BY_TYPE.getOrDefault(type, Map.of());
        Call call = call(spec, where);
        String name = call.name();
        List<String> raw = call.args();
        Entry<?> entry = named.get(name);
        if (entry == null) {
            if (mutates(type, name)) {
                throw new IllegalArgumentException("'" + name + "' edits an inherited " + type.getSimpleName()
                        + " - it needs a base to edit, and only a path write over one has it: " + where);
            }
            FallbackEntry<?> fallback = FALLBACKS.get(type);
            Object built = fallback != null ? fallback.fallback().create(name, new Args(name + "(...)", raw, where)) : null;
            if (built != null) return (T) built;
            throw new IllegalArgumentException("no " + type.getSimpleName() + " named '" + name + "' (known: "
                    + new java.util.TreeSet<>(named.keySet()) + "): " + where);
        }
        // a signature with no parens takes nothing: silently ignoring "curve(9)" hides a real mistake
        if (!entry.signature().contains("(") && !raw.isEmpty()) {
            throw new IllegalArgumentException(name + " takes no arguments: " + where);
        }
        return (T) entry.factory().create(new Args(entry.signature(), raw, where));
    }

    /** Applies an already-registered factory to {@code args} - lets one type's vocabulary reuse another's. */
    @SuppressWarnings("unchecked")
    public static <T> @NotNull T build(@NotNull Class<T> type, @NotNull String name, @NotNull Args args) {
        Vocabulary.ensure();
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

        /** A nested factory call as an argument: {@code to(at-listener(watchers), sound(...))}. */
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
