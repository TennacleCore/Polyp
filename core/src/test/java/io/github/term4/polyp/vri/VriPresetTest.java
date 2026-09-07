package io.github.term4.polyp.vri;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.presets.Preset;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** VRI is a member of the profile like any other: every shipped preset carries it whole, and an install reads nothing else. */
class VriPresetTest extends HeadlessServerTest {

    @Test
    void everyPresetCarriesVri() {
        for (Preset preset : Preset.values()) {
            VriConfig vri = preset.profile().get(MechanicsKeys.VRI);
            assertNotNull(vri, preset + " carries no VRI member");
            assertTrue(VriConfig.on(vri.itemDrop, null), preset + ": drops");
            assertTrue(VriConfig.on(vri.itemPickup, null), preset + ": pickup");
            assertTrue(VriConfig.on(vri.blockBreakProgress, null), preset + ": the crack overlay");
            assertTrue(VriConfig.on(vri.tntIgnite, null), preset + ": ignition");
        }
    }

    @Test
    void anUnsetKnobIsOff() {
        VriConfig none = VriConfig.builder().build(); // what install(polyp) answers where no scope sets the member
        assertTrue(!VriConfig.on(none.itemDrop, null) && !VriConfig.on(none.itemPickup, null));
    }
}
