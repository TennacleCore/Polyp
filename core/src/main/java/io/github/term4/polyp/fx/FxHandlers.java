package io.github.term4.polyp.fx;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The named alternatives an {@link Fx} key can be switched between by NAME rather than by code - what makes
 * {@code fx/polyp:pearl_teleport = game-wide} expressible in a ruleset. Handlers stay lambdas; this only gives
 * the ones worth choosing between a stable name. {@code none} resolves for every key ({@link FxHandler#NONE}).
 *
 * <p>Names are per key, so each key offers exactly its own alternatives and an unknown one can say what it knows.
 */
public final class FxHandlers {

    private FxHandlers() {}

    /** Registered for every key: silences it. */
    public static final String NONE = "none";

    private static final Map<Key, Map<String, FxHandler>> BY_KEY = new ConcurrentHashMap<>();

    static {
        // the pearl landing: positional everywhere, heard game-wide in BedWars
        register(Fx.PEARL_TELEPORT, "positional", Fx.pearlTeleport());
        register(Fx.PEARL_TELEPORT, "game-wide", Fx.pearlTeleportGameWide());
    }

    /** Names {@code handler} as an alternative for {@code fx}; a module registers its own the same way. */
    public static void register(@NotNull Key fx, @NotNull String name, @NotNull FxHandler handler) {
        BY_KEY.computeIfAbsent(fx, k -> new ConcurrentHashMap<>()).put(name, handler);
    }

    /** The handler named {@code name} for {@code fx}, or {@code null} if that key has no such alternative. */
    public static @Nullable FxHandler get(@NotNull Key fx, @NotNull String name) {
        if (NONE.equals(name)) return FxHandler.NONE;
        Map<String, FxHandler> named = BY_KEY.get(fx);
        return named != null ? named.get(name) : null;
    }

    /** The alternatives {@code fx} offers, {@code none} included - for errors and pickers. */
    public static @NotNull Set<String> names(@NotNull Key fx) {
        Map<String, FxHandler> named = BY_KEY.get(fx);
        Map<String, FxHandler> all = new LinkedHashMap<>();
        if (named != null) all.putAll(named);
        all.put(NONE, FxHandler.NONE);
        return Collections.unmodifiableSet(all.keySet());
    }
}
