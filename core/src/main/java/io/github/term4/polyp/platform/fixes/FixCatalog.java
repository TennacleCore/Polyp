package io.github.term4.polyp.platform.fixes;

import io.github.term4.polyp.tracking.ClientRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every fix toggle, the clients it applies to, and what it is for - the one place that answers "which fix is for
 * which version". {@link FixesConfig#TOGGLES} and the per-client scoping ({@link FixesConfig#scopedTo}) both read
 * it, so a toggle missing here is a compile-time-silent, test-caught omission rather than a quiet no-op.
 *
 * <p>{@link Fix#applies} is where a fix CAN act, which is not always the version that needs it: a note says so
 * when they differ.
 */
public final class FixCatalog {

    /** One toggle: its config name, the clients it can act on, and why it exists. */
    public record Fix(@NotNull String name, @NotNull ClientRange applies, @NotNull String note) {}

    private static final Map<String, Fix> BY_NAME = new LinkedHashMap<>();

    private static void add(String name, ClientRange applies, String note) {
        BY_NAME.put(name, new Fix(name, applies, note));
    }

    static {
        add("legacySelfPlacement", ClientRange.LEGACY,
                "a 1.8 client predicts a placement inside its own box and desyncs when the server refuses");
        add("equipmentFix", ClientRange.ANY,
                "empty slots stripped before grouping; ungated because a bulk resend is one packet for every viewer, "
                        + "and it is a LEGACY viewer's Via chain that hides the chestplate");
        add("legacyTabCompleteFix", ClientRange.ANY,
                "Minestom answers only an argument's own suggestions, so literals never complete - every client is "
                        + "short-changed, not just 1.8 (Minestom #3252)");
        add("legacyConsume", ClientRange.LEGACY,
                "1.8 neither gates its own eating nor learns the eaten count");
        add("legacyFireDouse", ClientRange.LEGACY,
                "1.8 extinguishes fire on a dig START, not a break");
        add("inventorySync", ClientRange.ANY,
                "EXPERIMENTAL remote-slot echo suppression; install-level, and the echo is every client's");
        add("legacyInventorySlot", ClientRange.LEGACY,
                "the player window arrives as -2 through ViaRewind. UNVERIFIED whether 1.8 needs it or only 1.7: "
                        + "narrow it to V1_7 only with a capture");
        add("effectResync", ClientRange.ANY,
                "a new viewer is owed the effects an entity already carries, on any client");
        add("legacyUseResync", ClientRange.LEGACY,
                "a 1.8 client draws on after a use the server refused, until its inventory is re-sent");
        add("legacyPlacementHalf", ClientRange.LEGACY,
                "a byte cursor cannot say 9/16, so a hit just above a side face's middle arrives as 0.5");
        add("legacyHealthRounding", ClientRange.LEGACY,
                "the 1.8 heart bar ceils; off by default, since captured networks send fractions");
        add("legacyTabSlots", ClientRange.ANY,
                "ONLY 1.7 reads it - its whole tab grid is Join Game's max players - but it rides a join, which "
                        + "lands before the protocol is known, and 1.8+ ignores the field");
    }

    private FixCatalog() {}

    /** Every fix, in declaration order. */
    public static @NotNull List<Fix> fixes() {
        return List.copyOf(BY_NAME.values());
    }

    public static @Nullable Fix of(@NotNull String name) {
        return BY_NAME.get(name);
    }

    /** Where {@code name} may act; an unlisted name is {@link ClientRange#ANY}, so a new toggle is never silently gated off. */
    public static @NotNull ClientRange applies(@NotNull String name) {
        Fix fix = BY_NAME.get(name);
        return fix != null ? fix.applies() : ClientRange.ANY;
    }

    /** One line per fix: {@code name  range  note} - what a listing command prints. */
    public static @NotNull List<String> describe() {
        return BY_NAME.values().stream().map(f -> f.name() + "  [" + f.applies() + "]  " + f.note()).toList();
    }
}
