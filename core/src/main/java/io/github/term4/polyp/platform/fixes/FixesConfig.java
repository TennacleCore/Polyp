package io.github.term4.polyp.platform.fixes;

import io.github.term4.polyp.platform.fixes.visuals.VisualsConfig;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level config for the lib's client/protocol behavior <b>fixes</b> - both cross-version compatibility (the 1.8
 * arrow-visibility fix) and single-version client smoothing (the self-meta echo fix). Assigned per scope via the
 * {@link io.github.term4.polyp.MechanicsProfile} {@code fixes} member.
 *
 * <p>The toggles are held by name, from {@link FixCatalog}, so the merge, the per-client scoping and the by-name
 * accessors have no per-toggle code at all: a new fix is a catalog entry, one accessor and one builder setter.
 */
public final class FixesConfig {

    private final @Nullable VisualsConfig visuals;
    private final Map<String, FixToggleConfig> toggles;
    private final @Nullable Integer legacyTabSlots;

    private FixesConfig(Builder b) {
        this.visuals = b.visuals;
        this.toggles = Map.copyOf(b.toggles);
        this.legacyTabSlots = b.legacyTabSlots;
    }

    public @Nullable VisualsConfig visuals() { return visuals; }

    /** Paper's placer exclusion ({@code LegacySelfPlacementFix}); wraps the server-wide placement listener - install-level, not per-scope. */
    public @Nullable FixToggleConfig legacySelfPlacement() { return toggles.get("legacySelfPlacement"); }

    /** Strips empty slots from outgoing equipment packets, vanilla parity for every version ({@code EquipmentSlotsFix}); install-level. */
    public @Nullable FixToggleConfig equipmentFix() { return toggles.get("equipmentFix"); }

    /** Command-name tab completion for legacy clients ({@code LegacyTabCompleteFix}); replaces the packet listener - install-level. */
    public @Nullable FixToggleConfig legacyTabCompleteFix() { return toggles.get("legacyTabCompleteFix"); }

    /**
     * The legacy 1.8/Via consume fix (eating under lag): a 1.8 client neither gates its own consumption nor learns
     * the eaten count, so {@code ConsumableSystem} refuses a re-use mid-use, decrements the held slot silently, and
     * confirms each finish. Per-scope; legacy clients only.
     */
    public @Nullable FixToggleConfig legacyConsume() { return toggles.get("legacyConsume"); }

    /** 1.8 douses fire on the dig START, not on the break ({@code LegacyFireDouseFix}); legacy clients only. */
    public @Nullable FixToggleConfig legacyFireDouse() { return toggles.get("legacyFireDouse"); }

    /** EXPERIMENTAL: suppresses the server's echo of a slot the client already shows ({@code InventorySync}); install-level. */
    public @Nullable FixToggleConfig inventorySync() { return toggles.get("inventorySync"); }

    /** The player window arrives as {@code -2} through ViaRewind ({@code LegacyInventorySlotFix}); legacy clients only. */
    public @Nullable FixToggleConfig legacyInventorySlot() { return toggles.get("legacyInventorySlot"); }

    /** A new viewer is owed the effects an entity already carries ({@code EffectResyncFix}); any client. */
    public @Nullable FixToggleConfig effectResync() { return toggles.get("effectResync"); }

    /** A 1.8 client draws on after a refused use until its inventory is re-sent ({@code LegacyUseResyncFix}). */
    public @Nullable FixToggleConfig legacyUseResync() { return toggles.get("legacyUseResync"); }

    /** A byte cursor cannot say 9/16, so a hit above a side face's middle arrives as {@code 0.5} ({@code LegacyPlacementHalfFix}). */
    public @Nullable FixToggleConfig legacyPlacementHalf() { return toggles.get("legacyPlacementHalf"); }

    /** The 1.8 heart bar ceils; off by default, since captured networks send fractions ({@code LegacyHealthRoundingFix}). */
    public @Nullable FixToggleConfig legacyHealthRounding() { return toggles.get("legacyHealthRounding"); }

    /** 1.7's whole tab grid is Join Game's max players; {@code null} leaves Minestom's own. */
    public @Nullable Integer legacyTabSlots() { return legacyTabSlots; }

    /** This config over {@code base}: every toggle either side sets, each merged onto the other. */
    public FixesConfig fromBase(FixesConfig base) {
        VisualsConfig v = visuals == null ? base.visuals : visuals.fromBase(base.visuals);
        Builder b = new Builder().visuals(v)
                .legacyTabSlots(legacyTabSlots != null ? legacyTabSlots : base.legacyTabSlots);
        for (String name : TOGGLES) {
            b.toggle(name, merge(toggles.get(name), base.toggles.get(name)));
        }
        return b.build();
    }

    private static @Nullable FixToggleConfig merge(@Nullable FixToggleConfig over, @Nullable FixToggleConfig base) {
        return over == null ? base : base == null ? over : over.fromBase(base);
    }

    /** The toggle names the {@code fixes/<toggle>/enabled} path addresses, from {@link FixCatalog}. */
    public static final List<String> TOGGLES =
            FixCatalog.fixes().stream().map(FixCatalog.Fix::name).filter(n -> !"legacyTabSlots".equals(n)).toList();

    /**
     * This config as it applies to a client speaking {@code protocol}: a toggle whose {@link FixCatalog} range does
     * not cover it reads unset, and so does {@code legacyTabSlots} outside its own. One seam, so every reader is
     * gated at once - a fix cannot forget to ask.
     */
    public FixesConfig scopedTo(int protocol) {
        Builder b = null;
        for (Map.Entry<String, FixToggleConfig> set : toggles.entrySet()) {
            if (FixCatalog.applies(set.getKey()).covers(protocol)) continue;
            if (b == null) b = toBuilder();
            b.toggle(set.getKey(), null);
        }
        if (legacyTabSlots != null && !FixCatalog.applies("legacyTabSlots").covers(protocol)) {
            if (b == null) b = toBuilder();
            b.legacyTabSlots(null);
        }
        return b == null ? this : b.build();
    }

    public @Nullable FixToggleConfig toggle(String name) {
        requireKnown(name);
        return toggles.get(name);
    }

    /** {@code base} (or an empty config) with one toggle replaced. */
    public static FixesConfig withToggle(@Nullable FixesConfig base, String name, FixToggleConfig toggle) {
        requireKnown(name);
        return (base != null ? base.toBuilder() : builder()).toggle(name, toggle).build();
    }

    private static void requireKnown(String name) {
        if (!TOGGLES.contains(name)) {
            throw new IllegalArgumentException("unknown fix toggle '" + name + "' (known: " + TOGGLES + ")");
        }
    }

    /** {@code base} (or an empty config) with one visual fix's config replaced ({@code visuals/<name>/<knob>}). */
    public static FixesConfig withVisual(@Nullable FixesConfig base, String name, Object entry) {
        VisualsConfig visuals = base != null && base.visuals != null ? base.visuals : VisualsConfig.builder().build();
        Builder b = base != null ? base.toBuilder() : builder();
        return b.visuals(visuals.with(name, entry)).build();
    }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(); }
    public static Builder builder(@Nullable FixesConfig base) { return base != null ? new Builder(base) : new Builder(); }

    public static final class Builder {

        private @Nullable VisualsConfig visuals;
        private final Map<String, FixToggleConfig> toggles = new LinkedHashMap<>();
        private @Nullable Integer legacyTabSlots;

        Builder() {}

        Builder(FixesConfig c) {
            visuals = c.visuals;
            toggles.putAll(c.toggles);
            legacyTabSlots = c.legacyTabSlots;
        }

        /** Sets one toggle by its {@link FixCatalog} name; {@code null} clears it. */
        public Builder toggle(String name, @Nullable FixToggleConfig v) {
            if (v == null) toggles.remove(name);
            else toggles.put(name, v);
            return this;
        }

        public Builder visuals(@Nullable VisualsConfig v) { this.visuals = v; return this; }
        public Builder legacySelfPlacement(@Nullable FixToggleConfig v) { return toggle("legacySelfPlacement", v); }
        public Builder equipmentFix(@Nullable FixToggleConfig v) { return toggle("equipmentFix", v); }
        public Builder legacyTabCompleteFix(@Nullable FixToggleConfig v) { return toggle("legacyTabCompleteFix", v); }
        public Builder legacyConsume(@Nullable FixToggleConfig v) { return toggle("legacyConsume", v); }
        public Builder legacyFireDouse(@Nullable FixToggleConfig v) { return toggle("legacyFireDouse", v); }
        public Builder inventorySync(@Nullable FixToggleConfig v) { return toggle("inventorySync", v); }
        public Builder legacyInventorySlot(@Nullable FixToggleConfig v) { return toggle("legacyInventorySlot", v); }
        public Builder effectResync(@Nullable FixToggleConfig v) { return toggle("effectResync", v); }
        public Builder legacyUseResync(@Nullable FixToggleConfig v) { return toggle("legacyUseResync", v); }
        public Builder legacyPlacementHalf(@Nullable FixToggleConfig v) { return toggle("legacyPlacementHalf", v); }
        public Builder legacyHealthRounding(@Nullable FixToggleConfig v) { return toggle("legacyHealthRounding", v); }
        public Builder legacyTabSlots(@Nullable Integer v) { this.legacyTabSlots = v; return this; }

        public FixesConfig build() { return new FixesConfig(this); }
    }
}
