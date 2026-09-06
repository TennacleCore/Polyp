package io.github.term4.polyp.mechanics.projectile;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.projectile.entities.arrow.ArrowEntity;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.metadata.projectile.AbstractArrowMeta;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crit particle flag and the crit damage roll are separate knobs: hypixel bridge drops both, scrims keeps
 * the trail with no bonus, minemen keeps vanilla. Capture-derived - see NOTES in the games repo.
 */
class ArrowCritKnobsTest extends HeadlessServerTest {

    @BeforeAll
    static void installFixes() {
        io.github.term4.polyp.platform.fixes.FixesSystem.install(Polyp.getInstance());
    }

    private static ProjectileConfig with(ProjectileTypeConfig arrow) {
        return ProjectileConfig.builder().typeConfigs(arrow).build().fromBase(Vanilla18.projectiles());
    }

    private boolean flagOf(ProjectileConfig cfg) {
        LivingEntity shooter = looseZombie();
        shooter.setInstance(instance, new Pos(0, 64, 0)).join();
        try {
            var snap = ProjectileSnapshot.of(shooter, Arrow.INSTANCE).withConfig(cfg);
            var arrow = new ProjectileSystem(Polyp.getInstance(), cfg).launch(snap);
            assertNotNull(arrow);
            awaitSpawn(arrow);
            ((ArrowEntity) arrow).setCritical(true); // what the bow does at full draw
            boolean flag = ((AbstractArrowMeta) arrow.getEntityMeta()).isCritical();
            arrow.remove();
            return flag;
        } finally {
            shooter.remove();
        }
    }

    @Test
    void particlesFollowTheDrawWhenUnset() {
        assertTrue(flagOf(with(ProjectileTypeConfig.builder(Arrow.KEY).build())));
    }

    @Test
    void particlesOffOnACrit() {
        assertFalse(flagOf(with(ProjectileTypeConfig.builder(Arrow.KEY).critParticles(false).build())));
    }

    /** The per-hit roll gate, as {@link io.github.term4.polyp.mechanics.projectile.entities.arrow.ArrowEntity}
     *  reads it: null = follow the arrow's own critical state, FALSE = never roll, TRUE = always. */
    private Boolean resolvedCritDamage(ProjectileTypeConfig arrow) {
        LivingEntity shooter = zombie(new Pos(0, 64, 0));
        try {
            var snap = ProjectileSnapshot.of(shooter, Arrow.INSTANCE).withConfig(with(arrow));
            return ProjectileConfigResolver.resolveHit(with(arrow).typeConfig(Arrow.KEY),
                    ProjectileConfigResolver.ProjectileContext.of(snap, services)).critDamage();
        } finally {
            shooter.remove();
        }
    }

    @Test
    void critDamageIsTriState() {
        assertNull(resolvedCritDamage(ProjectileTypeConfig.builder(Arrow.KEY).build()),
                "unset follows the arrow's own crit, so vanilla still rolls");
        assertEquals(Boolean.FALSE, resolvedCritDamage(
                ProjectileTypeConfig.builder(Arrow.KEY).critDamage(false).critParticles(true).build()),
                "roll off while the trail stays on - the scrims shape");
    }

    @Test
    void particlesWithoutTheRoll() {
        ProjectileConfig cfg = with(ProjectileTypeConfig.builder(Arrow.KEY)
                .critParticles(true).critDamage(false).build());
        assertTrue(flagOf(cfg), "the trail is cosmetic and independent of the bonus");
    }

    private static MechanicsProfile withDeflectTrail(ProjectileConfig projectiles) {
        return MechanicsProfile.builder()
                .set(MechanicsKeys.PROJECTILES, projectiles)
                .set(MechanicsKeys.FIXES, io.github.term4.polyp.platform.fixes.FixesConfig.builder()
                        .visuals(io.github.term4.polyp.platform.fixes.visuals.VisualsConfig.builder()
                                .legacyArrowVisibility(io.github.term4.polyp.platform.fixes.visuals.legacy_1_8
                                        .LegacyArrowVisibilityConfig.builder().enabled(true).deflectParticles(true).build())
                                .build())
                        .build())
                .build();
    }

    private static int crits(FakePlayer viewer) {
        return (int) viewer.sent(ParticlePacket.class).stream().filter(e -> e.particle() == Particle.CRIT).count();
    }

    /**
     * The trail covers a 1.8 client hiding an arrow off ANOTHER player. Through the shooter's own body - a
     * pass-through the moment they walk onto their own shot - it only reads as a false crit.
     */
    @Test
    void noTrailThroughTheShooter() {
        for (int cx = 37; cx <= 39; cx++) for (int cz = 37; cz <= 39; cz++) instance.loadChunk(cx, cz).join();
        ProjectileConfig cfg = with(ProjectileTypeConfig.builder(Arrow.KEY)
                .selfHit(ProjectileTypeConfig.HitResponse.PASS_THROUGH)
                .entityHit(ProjectileTypeConfig.HitResponse.PASS_THROUGH)
                .speed(0.6).gravity(0.0).build());
        MechanicsProfile before = Polyp.getInstance().profiles().global();
        Polyp.getInstance().profiles().setGlobal(withDeflectTrail(cfg));
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(600.5, 65, 600.5, 0f, 0f), "TrailSelf");
        LivingEntity victim = zombie(new Pos(600.5, 65, 640.5));
        try {
            var snap = ProjectileSnapshot.of(shooter.player, Arrow.INSTANCE).withConfig(cfg);
            var arrow = new ProjectileSystem(Polyp.getInstance(), cfg).launch(snap);
            assertNotNull(arrow);
            awaitSpawn(arrow);
            for (int tick = 1; tick <= 8; tick++) arrow.tick(tick * 50L);
            shooter.player.teleport(arrow.getPosition().add(0, -1.4, 0.4)).join(); // walk onto your own shot
            int self = crits(shooter);
            for (int tick = 9; tick <= 16; tick++) arrow.tick(tick * 50L);
            assertEquals(self, crits(shooter), "the shooter's own pass-through paints nothing");

            shooter.player.teleport(new Pos(600.5, 65, 620.5)).join(); // out of the line, still watching
            victim.teleport(arrow.getPosition().add(0, -1.4, 0.4)).join();
            for (int tick = 17; tick <= 26; tick++) arrow.tick(tick * 50L);
            assertTrue(crits(shooter) > self, "an enemy pass-through still trails");
            arrow.remove();
        } finally {
            victim.remove();
            shooter.player.remove();
            Polyp.getInstance().profiles().setGlobal(before);
        }
    }
}
