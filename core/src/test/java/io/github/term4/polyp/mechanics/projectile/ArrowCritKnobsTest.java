package io.github.term4.polyp.mechanics.projectile;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.projectile.entities.arrow.ArrowEntity;
import io.github.term4.polyp.mechanics.projectile.types.Arrow;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.metadata.projectile.AbstractArrowMeta;
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
    void particlesCanBeSuppressedOnACriticalArrow() {
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
    void critDamageIsATriStateSeparateFromTheFlag() {
        assertNull(resolvedCritDamage(ProjectileTypeConfig.builder(Arrow.KEY).build()),
                "unset follows the arrow's own crit, so vanilla still rolls");
        assertEquals(Boolean.FALSE, resolvedCritDamage(
                ProjectileTypeConfig.builder(Arrow.KEY).critDamage(false).critParticles(true).build()),
                "roll off while the trail stays on - the scrims shape");
    }

    @Test
    void particlesCanBeForcedOnWithoutTheRoll() {
        ProjectileConfig cfg = with(ProjectileTypeConfig.builder(Arrow.KEY)
                .critParticles(true).critDamage(false).build());
        assertTrue(flagOf(cfg), "the trail is cosmetic and independent of the bonus");
    }
}
