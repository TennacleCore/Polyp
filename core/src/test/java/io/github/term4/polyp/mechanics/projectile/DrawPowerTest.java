package io.github.term4.polyp.mechanics.projectile;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.config.PathEdits;
import io.github.term4.polyp.mechanics.projectile.shootables.DrawPower;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bow's charge-up is a per-shot knob, so a scope (or a timed powerup) can change it. */
class DrawPowerTest extends HeadlessServerTest {

    @Test
    void vanillaCurveIsUnchanged() {
        assertEquals(1f, DrawPower.VANILLA.at(1f), 1e-6, "full draw at one second");
        assertEquals((0.25f + 1f) / 3f, DrawPower.VANILLA.at(0.5f), 1e-6);
        assertEquals(1f, DrawPower.VANILLA.at(5f), 1e-6, "capped");
    }

    @Test
    void oneFactoryCoversTheFamily() {
        assertEquals(1f, DrawPower.fullDrawAt(0f).at(0f), 1e-6, "full-draw-at(0) IS instant");
        assertEquals(DrawPower.VANILLA.at(0.4f), DrawPower.fullDrawAt(1f).at(0.4f), 1e-6,
                "full-draw-at(1) IS the vanilla curve");
    }

    @Test
    void instantAndStretchedCurves() {
        assertEquals(1f, DrawPower.INSTANT.at(0f), 1e-6, "no charge-up at all");
        // half the time to full draw: what a "fast bow" powerup wants
        assertEquals(1f, DrawPower.fullDrawAt(0.5f).at(0.5f), 1e-6);
        assertTrue(DrawPower.fullDrawAt(0.5f).at(0.25f) > DrawPower.VANILLA.at(0.25f));
    }

    @Test
    void aPathSwapsTheCurveOverThePreset() {
        MechanicsProfile base = MechanicsProfile.builder()
                .set(MechanicsKeys.PROJECTILES, Vanilla18.projectiles()).build();
        MechanicsProfile.Builder b = MechanicsProfile.builder();
        PathEdits.apply(b, base, "projectiles/minecraft:arrow/drawPower", "full-draw-at(0)");

        var arrow = b.build().get(MechanicsKeys.PROJECTILES).typeConfig(Arrow.KEY);
        assertNotNull(arrow);
        assertEquals(1f, arrow.drawPower.constantOrNull().at(0f), 1e-6, "no charge-up");
        assertEquals(3.0, arrow.speed.constantOrNull(), "the preset's arrow tuning rides along");
    }
}
