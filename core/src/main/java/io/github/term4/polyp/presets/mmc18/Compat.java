package io.github.term4.polyp.presets.mmc18;

import io.github.term4.polyp.platform.compatibility.Compat18;
import io.github.term4.polyp.platform.compatibility.CompatConfig;

/** {@link Compat18#config()} plus the measured deltas for this network. */
public final class Compat {

    private Compat() {}

    public static CompatConfig config() {
        return Compat18.config().toBuilder()
                // a placement overlapping the placer is refused, 1.8 client or not: vanilla 1.8 landed a slab in
                // your own legs off ItemBlock's placer exclusion, and no practice server has kept that
                .legacySelfPlace(false)
                .build();
    }
}
