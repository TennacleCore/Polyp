package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.config.ConfigKnob;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** {@code off()} must decide every Boolean knob - a new CompatConfig knob that skips it fails here. */
class CompatOffCoverageTest {

    @Test
    void offDecidesEveryBooleanKnob() {
        CompatConfig off = Compat18.off();
        for (ConfigKnob knob : CompatConfigBuilderBase.KNOBS.values()) {
            if (knob.valueType() == Boolean.class) {
                assertNotNull(knob.get().apply(off), "Compat18.off() skips " + knob.name());
            }
        }
    }
}
