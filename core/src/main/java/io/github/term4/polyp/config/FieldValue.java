package io.github.term4.polyp.config;

import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A single configurable value resolved against a context {@code CTX}: a constant, a context-aware function, or a
 * function with a constant fallback. Values built from {@link #constant(Object)} stay readable without a context via
 * {@link #constantOrNull()}.
 */
public record FieldValue<CTX, T>(Function<CTX, T> fn, @Nullable T constant) {

    public static <CTX, T> FieldValue<CTX, T> constant(T v) {
        return new FieldValue<>(ctx -> v, v);
    }

    public static <CTX, T> FieldValue<CTX, T> of(Function<CTX, T> f) {
        return new FieldValue<>(f, null);
    }

    /** Falls back to {@code fallback} when the function returns {@code null}. */
    public static <CTX, T> FieldValue<CTX, T> ofWithFallback(T fallback, Function<CTX, T> fn) {
        return new FieldValue<>(ctx -> {
            T r = fn.apply(ctx);
            return r != null ? r : fallback;
        }, null);
    }

    public T resolve(CTX ctx) {
        return fn.apply(ctx);
    }

    /** {@code null} when the field is unset or resolves to {@code null}. */
    public static <CTX, T> @Nullable T resolve(@Nullable FieldValue<CTX, T> field, CTX ctx) {
        return field != null ? field.resolve(ctx) : null;
    }

    /** An unset field or a {@code null} resolution falls back to {@code def}. */
    public static <CTX, T> T resolve(@Nullable FieldValue<CTX, T> field, CTX ctx, T def) {
        T v = field != null ? field.resolve(ctx) : null;
        return v != null ? v : def;
    }

    /** {@code null} when this value is context-dependent. */
    public @Nullable T constantOrNull() {
        return constant;
    }

    /** {@code a} layered over {@code b} ({@code a} wins, falling back per resolution); either side may be {@code null}. */
    public static <CTX, T> FieldValue<CTX, T> merge(FieldValue<CTX, T> a, FieldValue<CTX, T> b) {
        if (b == null) return a;
        if (a == null) return b;
        return a.or(b);
    }

    /** Uses {@code fallback} when this one resolves to {@code null}. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public FieldValue<CTX, T> or(FieldValue<CTX, T> fallback) {
        if (constant != null) return this; // a constant never resolves null - keep it introspectable through merges
        if (fn instanceof Targeted raw) { // same for a targeted value: the merge lands in its fallback
            FieldValue below = raw.fallback() == null ? fallback : raw.fallback().or(fallback);
            return new FieldValue<>(new Targeted(raw.who(), raw.value(), below), null);
        }
        return new FieldValue<>(ctx -> {
            T r = fn.apply(ctx);
            return r != null ? r : fallback.fn.apply(ctx);
        }, null);
    }

    /**
     * {@code value} when the context's {@link SubjectContext#subject() subject} is a player {@code who} accepts, else
     * {@code fallback} - the knob's inherited value. This is how a ruleset entry scoped to a team or a seat
     * lands in the ONE world profile a game owns, instead of a per-player scope that has to be pushed,
     * ordered and cleared. Inspectable: {@link #fn()} is a {@link Targeted}.
     */
    public static <CTX extends SubjectContext, T> FieldValue<CTX, T> targeted(Predicate<Player> who, FieldValue<CTX, T> value,
                                                                              @Nullable FieldValue<CTX, T> fallback) {
        return new FieldValue<>(new Targeted<>(who, value, fallback), null);
    }

    /**
     * The function behind a {@link #targeted} value, kept as a record so tooling can read it back. The context
     * bound is what makes a knob with no subject a compile error rather than a value that quietly never applies.
     */
    public record Targeted<CTX extends SubjectContext, T>(Predicate<Player> who, FieldValue<CTX, T> value,
                                                          @Nullable FieldValue<CTX, T> fallback) implements Function<CTX, T> {
        @Override
        public T apply(CTX ctx) {
            if (ctx.subject() instanceof Player p && who.test(p)) return value.resolve(ctx);
            return fallback != null ? fallback.resolve(ctx) : null;
        }
    }
}
