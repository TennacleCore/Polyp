package test.presets;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.ProjectileSystem;
import io.github.term4.polyp.mechanics.projectile.entities.ProjectileEntity;
import io.github.term4.polyp.mechanics.projectile.types.Fireball;
import io.github.term4.polyp.mechanics.projectile.types.Pearl;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileType;
import io.github.term4.polyp.presets.hypixel.Projectiles;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A fireball thrown at a pearl the tick after it: 1.8 moves the new fireball after the older pearl. */
class HypixelPearlCatchOrderTest extends HeadlessServerTest {

    @Test
    void aFreshFireballStepsLast() {
        Pos from = new Pos(640.5, 64, 640.5, 0f, -90f);
        FakePlayer shooter = FakePlayer.connect(instance, from, "CatchOrder");
        try {
            shooter.player.teleport(from).join();
            ProjectileConfig config = Projectiles.config();
            ProjectileSystem system = new ProjectileSystem(Polyp.getInstance(), config);
            ProjectileEntity pearl = launch(system, config, shooter, Pearl.INSTANCE);
            system.firstStep(pearl);
            endTick();

            ProjectileEntity fireball = launch(system, config, shooter, Fireball.INSTANCE);
            Pos spawned = fireball.getPosition();
            system.firstStep(fireball);
            assertEquals(spawned, fireball.getPosition(), "no move inside the click");
            pearl.tick(100L); // the pearl's own update in that tick
            endTick();
            assertFalse(pearl.isRemoved(), "a pearl a tick ahead is not caught on the throw");
            assertTrue(fireball.getPosition().y() > spawned.y(), "the fireball's first step, at the tick's end");

            for (int tick = 3; tick <= 120 && !pearl.isRemoved(); tick++) {
                pearl.tick(tick * 50L);
                if (!fireball.isRemoved()) fireball.tick(tick * 50L);
            }
            assertTrue(pearl.isRemoved(), "the fireball catches it later");
            assertTrue(shooter.player.getPosition().y() > 80, "high up: " + shooter.player.getPosition());
            if (!fireball.isRemoved()) fireball.remove();
        } finally {
            shooter.player.remove();
        }
    }

    private static ProjectileEntity launch(ProjectileSystem system, ProjectileConfig config, FakePlayer shooter, ProjectileType type) {
        ProjectileEntity proj = system.launch(ProjectileSnapshot.of(shooter.player, type).withConfig(config));
        assertNotNull(proj);
        awaitSpawn(proj);
        return proj;
    }

    private static void endTick() {
        MinecraftServer.getSchedulerManager().processTickEnd();
    }
}
