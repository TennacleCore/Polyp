package io.github.term4.polyp.platform.fixes;

import io.github.term4.polyp.tracking.ClientRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The catalog is the toggle list, and a config scoped to a client drops what that client cannot take. */
class FixCatalogTest {

    private static final int V1_7 = 5, V1_8 = 47, MODERN = 771;

    @Test
    void everyToggleIsCatalogued() {
        FixesConfig cfg = FixesLegacy.config();
        assertEquals(10, FixesConfig.TOGGLES.size(), "a new toggle needs its FixCatalog entry");
        for (String name : FixesConfig.TOGGLES) {
            assertNotNull(FixCatalog.of(name), name);
            cfg.toggle(name); // a name the switch does not know throws
        }
        assertEquals(11, FixCatalog.fixes().size(), "ten toggles and the one value knob");
        assertNotNull(FixCatalog.of("legacyTabSlots"), "the value knob is catalogued too, though it is no toggle");
        assertTrue(FixCatalog.describe().stream().anyMatch(l -> l.startsWith("legacyTabSlots  [any]")));
    }

    @Test
    void modernDropsTheLegacyFixes() {
        FixesConfig modern = FixesLegacy.config().scopedTo(MODERN);
        assertNull(modern.legacyPlacementHalf(), "a 1.8 cursor fix cannot apply to a modern client");
        assertNull(modern.legacyConsume());
        assertNotNull(modern.legacyTabCompleteFix(), "the tab-complete gap is every client's");
        assertNotNull(modern.effectResync());
        assertEquals(20, modern.legacyTabSlots(), "join carries it before the protocol is known");
    }

    @Test
    void legacyKeepsItsOwn() {
        FixesConfig legacy = FixesLegacy.config().scopedTo(V1_8);
        assertNotNull(legacy.legacyPlacementHalf());
        assertNotNull(legacy.legacyUseResync());
        assertSame(legacy, legacy.scopedTo(V1_8), "nothing out of range: the same instance");
        assertNotNull(FixesLegacy.config().scopedTo(V1_7).legacyInventorySlot(), "1.7 is legacy too");
    }

    @Test
    void rangesReadAsVersions() {
        assertEquals("any", ClientRange.ANY.toString());
        assertEquals("1.8 and older", ClientRange.LEGACY.toString());
        assertEquals("1.7", ClientRange.V1_7.toString());
        assertTrue(ClientRange.LEGACY.covers(V1_7) && ClientRange.LEGACY.covers(V1_8));
        assertTrue(!ClientRange.LEGACY.covers(MODERN) && ClientRange.MODERN.covers(MODERN));
    }
}
