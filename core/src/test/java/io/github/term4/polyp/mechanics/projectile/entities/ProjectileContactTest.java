package io.github.term4.polyp.mechanics.projectile.entities;

import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Minestom answers an overlap of the two boxes with a hit from any side; neither vanilla does. 1.8 takes only a
 * face crossing on this tick's segment, 26.1 adds the start point being inside - the difference is the pearl a
 * fireball catches before either has flown.
 */
class ProjectileContactTest extends HeadlessServerTest {

    @Test
    void anOverlapOffThePathIsNoHit() {
        // one position, two directions: the boxes overlap either way, and only the aim differs
        Pos beside = new Pos(700.65, 64, 700.0); // grown boxes touch, the centre is outside the target's
        run(false, (shot, target) -> {
            assertFalse(shot.contacts(target, beside, new Vec(0, 0, -0.5)),
                    "an overlap the segment never crosses is not a hit");
            assertTrue(shot.contacts(target, beside, new Vec(-0.5, 0, 0)),
                    "the same overlap, aimed into it, is");
        });
    }

    /** Neither era has a start-inside rule for a flying projectile: a segment that crosses no face is a miss. */
    @Test
    void aStartInsideCrossesNoFace() {
        Pos inside = new Pos(700.0, 64.5, 700.0); // dead centre of the target, going nowhere
        run(false, (shot, target) ->
                assertFalse(shot.contacts(target, inside, new Vec(0, 0, 0)), "no crossing, no hit"));
    }

    /** And the rule is actually applied to the sweep: a flight past an overlapping target must not end on it. */
    @Test
    void aFlightPastAnOverlapCarriesOn() {
        io.github.term4.polyp.mechanics.projectile.ProjectileConfig cfg =
                io.github.term4.polyp.mechanics.projectile.ProjectileConfig.builder()
                        .typeConfigs(io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig
                                .builder(io.github.term4.polyp.mechanics.projectile.types.Arrow.KEY)
                                .speed(0.6).gravity(0.0).boundingBox(0.25, 0.25, 0.25).entityHitGrow(0.3)
                                .entityHitGrowRamp(false).build())
                        .build().fromBase(io.github.term4.polyp.presets.vanilla18.Vanilla18.projectiles());
        LivingEntity shooter = zombie(new Pos(710.5, 65, 710.5, 0f, 0f));
        LivingEntity beside = null;
        try {
            var snap = io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot
                    .of(shooter, io.github.term4.polyp.mechanics.projectile.types.Arrow.INSTANCE).withConfig(cfg);
            var shot = new io.github.term4.polyp.mechanics.projectile.ProjectileSystem(
                    io.github.term4.polyp.Polyp.getInstance(), cfg).launch(snap);
            org.junit.jupiter.api.Assertions.assertNotNull(shot);
            awaitSpawn(shot);
            for (int tick = 1; tick <= 4; tick++) shot.tick(tick * 50L);
            assertFalse(shot.isRemoved(), "still flying");

            // alongside it, boxes touching, centre outside the grown target box - the old overlap rule ate this
            beside = zombie(shot.getPosition().add(0.65, -1.4, 0));
            for (int tick = 5; tick <= 10; tick++) shot.tick(tick * 50L);
            assertFalse(shot.isRemoved(), "an overlap beside the path does not end the flight");

            beside.teleport(shot.getPosition().add(0, -1.4, 1.0)).join(); // dead ahead instead
            for (int tick = 11; tick <= 20; tick++) shot.tick(tick * 50L);
            assertTrue(shot.isRemoved(), "and one on the path still does");
        } finally {
            if (beside != null) beside.remove();
            shooter.remove();
        }
    }

    /**
     * 26.1 grows the margin from nothing to the full value between tick 2 and tick 8
     * ({@code ProjectileUtil.computeMargin}); 1.8 applies it whole from the first tick.
     */
    @Test
    void theModernMarginRampsInOverTheFirstTicks() {
        LivingEntity target = zombie(new Pos(720.0, 64, 720.0));
        ProjectileEntity ramped = new ProjectileEntity(null, EntityType.SNOWBALL) {};
        ProjectileEntity flat = new ProjectileEntity(null, EntityType.SNOWBALL) {};
        try {
            for (ProjectileEntity shot : new ProjectileEntity[]{ramped, flat}) {
                shot.setEntityHitGrow(0.3);
                shot.setInstance(instance, new Pos(720.0, 66, 720.0)).join();
            }
            ramped.setEntityHitGrowRamp(true);
            flat.setEntityHitGrowRamp(false);

            assertEquals(0.0, ramped.entityHitMargin(), 1e-9, "a fresh 26.1 shot has no margin at all");
            assertEquals(0.3, flat.entityHitMargin(), 1e-9, "1.8 has the whole margin from the first tick");

            for (int tick = 1; tick <= 8; tick++) ramped.tick(tick * 50L);
            assertEquals(0.3, ramped.entityHitMargin(), 1e-9, "and 26.1 reaches it by tick 8");
        } finally {
            target.remove();
            ramped.remove();
            flat.remove();
        }
    }

    private static void run(boolean ramp, java.util.function.BiConsumer<ProjectileEntity, LivingEntity> body) {
        LivingEntity target = new LivingEntity(EntityType.ZOMBIE);
        target.setInstance(instance, new Pos(700.0, 64, 700.0)).join();
        ProjectileEntity shot = new ProjectileEntity(null, EntityType.SNOWBALL) {}; // geometry only: no type behaviour involved
        shot.setBoundingBox(0.25, 0.25, 0.25); // a pearl's: the overlap reaches past the grown target box, the centre does not
        shot.setEntityHitGrow(0.3);
        shot.setEntityHitGrowRamp(ramp);
        try {
            body.accept(shot, target);
        } finally {
            target.remove();
            shot.remove();
        }
    }
}
