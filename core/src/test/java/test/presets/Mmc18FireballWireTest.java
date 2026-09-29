package test.presets;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.ProjectileSystem;
import io.github.term4.polyp.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.polyp.mechanics.projectile.types.Fireball;
import io.github.term4.polyp.mechanics.projectile.types.Pearl;
import io.github.term4.polyp.presets.mmc18.Projectiles;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * mmc18 fireball wire, from the captured sessions (mmcfbdflct1 + 3 older, 139 fireballs): MineMen tracks a
 * fireball on the vanilla 10-tick cadence - absolute teleports only, and never an {@code entity_velocity}
 * (the 1.8 tracker sends velocity only when {@code velocityChanged}, which a fireball never sets).
 */
class Mmc18FireballWireTest extends HeadlessServerTest {

    @Test
    void fireballTeleportsOnTheTrackerCadenceAndSendsNoVelocity() {
        ProjectileConfig config = Projectiles.config();
        var viewer = FakePlayer.connect(instance, new Pos(4.5, 210, 4.5), "FbWire");
        LivingEntity shooter = looseZombie();
        shooter.setInstance(instance, new Pos(8.5, 210, 8.5, 0.0f, 0.0f)).join();
        try {
            var snap = ProjectileSnapshot.of(shooter, Fireball.INSTANCE).withConfig(config);
            ProjectileEntity fb = new ProjectileSystem(Polyp.getInstance(), config).launch(snap);
            assertNotNull(fb);
            awaitSpawn(fb);
            shooter.remove();
            viewer.sent.clear();

            // 1.8 increments updateCounter after the check, so counter 0 sends: the first correction lands the
            // tick after spawn. A fireball that hits a wall in ~6 ticks gets no other chance to be corrected.
            fb.tick(50L);
            assertEquals(1, viewer.packetsFor(fb.getEntityId()).stream()
                    .filter(p -> p instanceof EntityTeleportPacket).count(), "first teleport on the tick after spawn");

            for (int tick = 2; tick <= 40 && !fb.isRemoved(); tick++) fb.tick(tick * 50L);

            var mine = viewer.packetsFor(fb.getEntityId());
            long velocity = mine.stream().filter(p -> p instanceof EntityVelocityPacket).count();
            long teleports = mine.stream().filter(p -> p instanceof EntityTeleportPacket).count();
            assertTrue(velocity == 0, "a fireball never broadcasts velocity in flight, got " + velocity);
            assertEquals(4, teleports, "then the 10-tick cadence: ticks 1, 11, 21, 31");
            if (!fb.isRemoved()) fb.remove();
        } finally {
            viewer.player.remove();
        }
    }

    /** mmcfbupimpact: blasts at 1, 2, 3 ticks after the spawn packet for 0.04, 0.56, 1.61 blocks, so a MineMen
     *  fireball's first step waits for the tick after its spawn; a vanilla item's does not. */
    @Test
    void theFirstStepWaitsATick() {
        ProjectileConfig config = Projectiles.config();
        ProjectileSystem system = new ProjectileSystem(Polyp.getInstance(), config);
        ProjectileEntity fb = pointBlank(system, config, Fireball.INSTANCE, 230);
        system.firstStep(fb);
        assertFalse(fb.isRemoved(), "no flight in the use tick");
        assertFalse(fb.getEntityMeta().isOnFire(), "unlit until it flies");
        fb.tick(50L);
        assertTrue(fb.isRemoved(), "the first step, a tick later, meets the wall");

        ProjectileEntity pearl = pointBlank(system, config, Pearl.INSTANCE, 240);
        system.firstStep(pearl);
        assertTrue(pearl.isRemoved(), "a vanilla item impacts in the use tick");
    }

    // a shooter facing +z, its projectile spawning 0.05 short of a stone cell
    private static ProjectileEntity pointBlank(ProjectileSystem system, ProjectileConfig config,
                                               io.github.term4.polyp.mechanics.projectile.types.ProjectileType type, int y) {
        LivingEntity shooter = looseZombie();
        var snap = ProjectileSnapshot.of(shooter, type).withConfig(config);
        double forward = system.resolveFlight(snap).spawnOffsetForward();
        shooter.setInstance(instance, new Pos(8.5, y, 8.95 - forward, 0.0f, 0.0f)).join();
        double eye = y + shooter.getEyeHeight();
        for (int dy = -1; dy <= 1; dy++) instance.setBlock(8, (int) Math.floor(eye) + dy, 9, Block.STONE);
        ProjectileEntity proj = system.launch(snap);
        assertNotNull(proj);
        awaitSpawn(proj);
        assertEquals(8.95, proj.getPosition().z(), 0.02, "spawned at the eye, a hair short of the cell");
        shooter.remove();
        return proj;
    }
}
