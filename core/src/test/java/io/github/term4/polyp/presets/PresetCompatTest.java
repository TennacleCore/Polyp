package io.github.term4.polyp.presets;

import io.github.term4.polyp.testsupport.HeadlessServerTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Each preset carries the cross-version layer of the network it recreates, not just its mechanics. */
class PresetCompatTest extends HeadlessServerTest { // profile() boots Fx, which needs the server

    @Test
    void hypixelRefusesSelfOverlappingPlacementWhereThe18PresetsAllowIt() {
        assertEquals(Boolean.FALSE, Preset.HYPIXEL.compat().legacySelfPlace.constantOrNull());
        assertEquals(Boolean.FALSE, Preset.HYPIXEL_BEDWARS.compat().legacySelfPlace.constantOrNull());
        for (Preset legacy : new Preset[]{Preset.VANILLA18, Preset.MMC18, Preset.SCRIMS18}) {
            assertNull(legacy.compat().legacySelfPlace, legacy + " keeps the 1.8 mechanic");
        }
    }

    /** Everything else the 1.8 layer sets still rides along - hypixel is a delta, not a replacement. */
    @Test
    void hypixelKeepsTheRestOfTheLegacyLayer() {
        assertEquals(Preset.VANILLA18.compat().legacyHitbox.constantOrNull(), Preset.HYPIXEL.compat().legacyHitbox.constantOrNull());
        assertEquals(Preset.VANILLA18.compat().blockPlaceReach.constantOrNull(), Preset.HYPIXEL.compat().blockPlaceReach.constantOrNull());
        assertEquals(Preset.VANILLA18.compat().attackHitboxMargin.constantOrNull(), Preset.HYPIXEL.compat().attackHitboxMargin.constantOrNull());
        assertNotNull(Preset.HYPIXEL.compat().disabledPoses.constantOrNull());
    }

    @Test
    void modernPresetRunsNoLegacyLayer() {
        assertEquals(Boolean.FALSE, Preset.VANILLA.compat().legacyHitbox.constantOrNull(), "nothing to reconcile on 26.1");
    }

    /** motY's gravity accrues on the player's own movement everywhere but Hypixel, which steps on the server tick. */
    @Test
    void onlyHypixelStepsMotYOnTheServerTick() {
        assertEquals(Boolean.FALSE, motYPerPacket(Preset.HYPIXEL));
        assertEquals(Boolean.FALSE, motYPerPacket(Preset.HYPIXEL_BEDWARS));
        for (Preset legacy : new Preset[]{Preset.VANILLA18, Preset.MMC18, Preset.SCRIMS18}) {
            assertEquals(Boolean.TRUE, motYPerPacket(legacy), legacy + " runs 1.8's per-packet living update");
        }
    }

    private static Boolean motYPerPacket(Preset preset) {
        var rule = preset.profile().get(io.github.term4.polyp.MechanicsKeys.VELOCITY);
        assertNotNull(rule, preset + " sets a velocity rule");
        var config = rule.reconstructionConfig();
        assertNotNull(config, preset + " reconstructs velocity");
        return config.motYOnMovePacket == null ? Boolean.FALSE : config.motYOnMovePacket.constantOrNull(); // unset = the default
    }

    /** The reach gate is the server's own, not a player's: each network sets how far past vanilla it accepts. */
    @Test
    void eachPresetKeepsItsOwnReachGate() {
        assertEquals(io.github.term4.polyp.mechanics.attack.AttackConfig.VANILLA_REACH_PADDING, padding(Preset.VANILLA18));
        assertEquals(io.github.term4.polyp.mechanics.attack.AttackConfig.VANILLA_REACH_PADDING, padding(Preset.HYPIXEL),
                "hypixel refuses what 1.8 refuses");
        assertEquals(io.github.term4.polyp.mechanics.attack.AttackConfig.MINEMEN_REACH_PADDING, padding(Preset.MMC18));
        assertEquals(io.github.term4.polyp.mechanics.attack.AttackConfig.SCRIMS_REACH_PADDING, padding(Preset.SCRIMS18));
    }

    private static double padding(Preset preset) {
        var attack = preset.profile().get(io.github.term4.polyp.MechanicsKeys.ATTACK);
        assertNotNull(attack, preset + " sets an attack config");
        Double value = attack.reachPadding.constantOrNull();
        assertNotNull(value, preset + " pins its padding");
        return value;
    }

    /** Every preset hands back a complete layer - callers set it, they never assemble one. */
    @Test
    void everyPresetCarriesAWholeLayer() {
        for (Preset preset : Preset.values()) {
            assertNotNull(preset.compat(), preset + " has a compat layer");
            assertNotNull(preset.profile(), preset + " has a mechanics profile");
        }
    }
}
