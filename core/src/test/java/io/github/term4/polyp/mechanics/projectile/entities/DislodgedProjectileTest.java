package io.github.term4.polyp.mechanics.projectile.entities;

import io.github.term4.polyp.mechanics.damage.types.fall.FallDamage;
import io.github.term4.polyp.mechanics.damage.types.projectile.EnderPearlDamage;
import io.github.term4.polyp.presets.Preset;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three parity gaps the 19 Sep audit found: a dislodged shot leaves on a fraction of the motion it arrived
 * with, and a pearl's arrival is billed and dismounted by the era's own rules.
 */
class DislodgedProjectileTest extends HeadlessServerTest {

    @Test
    void theEighteenPearlBillsFallAndThrowsTheRiderOff() {
        var pearl = pearlOf(Preset.VANILLA18);
        assertEquals(FallDamage.INSTANCE, pearl.teleportDamageType.constantOrNull(), "1.8 bills the arrival as a fall");
        assertEquals(Boolean.TRUE, pearl.teleportDismounts.constantOrNull(), "and ejects the rider first");
    }

    @Test
    void theModernPearlKeepsItsOwnTypeAndItsRide() {
        // the modern preset sets neither, so both fall through to the resolver defaults PearlEntity reads
        var pearl = pearlOf(Preset.VANILLA);
        if (pearl != null) {
            assertNull(pearl.teleportDamageType, "the modern preset leaves the type unset");
            assertNull(pearl.teleportDismounts, "and the ride unset");
        }
        assertEquals("minecraft:ender_pearl", EnderPearlDamage.KEY.asString(), "and the default it lands on is 26.1's own");
        assertEquals(net.minestom.server.entity.damage.DamageType.ENDER_PEARL,
                EnderPearlDamage.INSTANCE.minecraftType(), "wired to the vanilla registry key, so a ruleset can select it");
    }

    /** Vanilla pops a dislodged shot out on a fraction of the motion it arrived with, per axis, not on nothing. */
    @Test
    void aDislodgedArrowLeavesOnWhatItArrivedWith() {
        var cfg = io.github.term4.polyp.mechanics.projectile.ProjectileConfig.builder()
                .typeConfigs(io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig
                        .builder(io.github.term4.polyp.mechanics.projectile.types.Arrow.KEY)
                        .speed(1.2).gravity(0.0).removeOnBlockHit(false).build())
                .build().fromBase(io.github.term4.polyp.presets.vanilla18.Vanilla18.projectiles());
        net.minestom.server.entity.LivingEntity shooter = zombie(new net.minestom.server.coordinate.Pos(740.5, 65, 740.5, -90f, 0f));
        // a column at the flight height: the shot leaves the eye, ~1.6 above the feet
        var wall = new net.minestom.server.coordinate.BlockVec(745, 66, 740);
        for (int y = 65; y <= 68; y++) {
            instance.setBlock(new net.minestom.server.coordinate.BlockVec(745, y, 740),
                    net.minestom.server.instance.block.Block.STONE);
        }
        try {
            var snap = io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot
                    .of(shooter, io.github.term4.polyp.mechanics.projectile.types.Arrow.INSTANCE).withConfig(cfg);
            var shot = new io.github.term4.polyp.mechanics.projectile.ProjectileSystem(
                    io.github.term4.polyp.Polyp.getInstance(), cfg).launch(snap);
            assertNotNull(shot);
            awaitSpawn(shot);
            double impact = 0;
            for (int tick = 1; tick <= 40 && !shot.isStuck(); tick++) {
                impact = shot.velocityBt().length();
                shot.tick(tick * 50L);
            }
            assertTrue(shot.isStuck(), "it reached the wall");
            assertEquals(0.0, shot.velocityBt().length(), 1e-9, "and is frozen while stuck");

            for (int y = 65; y <= 68; y++) {
                instance.setBlock(new net.minestom.server.coordinate.BlockVec(745, y, 740),
                        net.minestom.server.instance.block.Block.AIR); // mined out from under it
            }
            for (int tick = 41; tick <= 45 && shot.isStuck(); tick++) shot.tick(tick * 50L);
            assertTrue(!shot.isStuck(), "the block went, so the arrow did too");

            double out = shot.velocityBt().length();
            assertTrue(out > 0, "it leaves on something, not a dead drop: " + out);
            assertTrue(out <= impact * 0.2 + 1e-9, "and on no more than a fifth of what it arrived with: "
                    + out + " vs " + impact);
            shot.remove();
        } finally {
            for (int y = 65; y <= 68; y++) {
                instance.setBlock(new net.minestom.server.coordinate.BlockVec(745, y, 740),
                        net.minestom.server.instance.block.Block.AIR);
            }
            shooter.remove();
        }
    }

    private static io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig pearlOf(Preset preset) {
        var projectiles = preset.profile().get(io.github.term4.polyp.MechanicsKeys.PROJECTILES);
        assertNotNull(projectiles, preset + " sets a projectile config");
        return projectiles.typeConfig(io.github.term4.polyp.mechanics.projectile.types.Pearl.KEY);
    }
}
