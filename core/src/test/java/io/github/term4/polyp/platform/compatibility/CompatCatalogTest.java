package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.config.ConfigKnob;
import io.github.term4.polyp.tracking.ClientRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every compat knob is catalogued, and a config scoped to a client keeps the mechanics while dropping the rendering. */
class CompatCatalogTest {

    private static final int V1_8 = 47, MODERN = 771;

    @Test
    void everyKnobIsCatalogued() {
        for (String name : CompatConfigBuilderBase.KNOBS.keySet()) {
            assertNotNull(CompatCatalog.of(name), name + " needs a CompatCatalog entry");
        }
        assertEquals(CompatConfigBuilderBase.KNOBS.size(), CompatCatalog.knobs().size(), "no stale entries either");
    }

    @Test
    void aLegacyClientKeepsTheMechanicsAndLosesTheRendering() {
        CompatConfig legacy = Compat18.config().scopedTo(V1_8);
        assertNull(legacy.attackHitboxMargin, "1.8 hits natively; the stamp is junk NBT through Via");
        assertNull(legacy.suppressSwim, "no swim pose to suppress");
        assertNotNull(legacy.removeAttackCooldown, "1.8 combat is the server's, not the client's");
        assertNotNull(legacy.legacyHitbox);
    }

    @Test
    void aModernClientLosesOnlyTheLegacyDrawing() {
        CompatConfig modern = Compat18.config().scopedTo(MODERN);
        assertNull(modern.legacyChestShapes, "the sideways pair is a 1.8 drawing");
        assertNotNull(modern.removeAttackCooldown, "a modern client on a 1.8 preset still loses the cooldown");
        assertNotNull(modern.oldPlacement);
    }

    @Test
    void anUntouchedConfigIsTheSameInstance() {
        CompatConfig off = CompatConfig.builder().build();
        assertSame(off, off.scopedTo(MODERN), "nothing set: nothing to scope");
        assertTrue(CompatCatalog.describe().stream().anyMatch(l -> l.startsWith("removeAttackCooldown  [any]")));
    }

    @Test
    void theStampedRangesAreTheOnesTheCodeGates() {
        assertEquals(ClientRange.MODERN, CompatCatalog.applies("attackHitboxMargin"));
        assertEquals(ClientRange.LEGACY, CompatCatalog.applies("legacySelfPlace"));
        assertEquals(ClientRange.ANY, CompatCatalog.applies("attackReach"));
        assertEquals(ClientRange.ANY, CompatCatalog.applies("somethingNobodyHasWrittenYet"), "an unlisted knob applies everywhere");
        ConfigKnob knob = CompatConfigBuilderBase.KNOBS.get("attackHitboxMargin");
        assertNotNull(knob.get().apply(Compat18.config()), "the unscoped preset still sets it");
    }
}
