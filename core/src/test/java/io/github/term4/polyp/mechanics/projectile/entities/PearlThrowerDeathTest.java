package io.github.term4.polyp.mechanics.projectile.entities;

import io.github.term4.polyp.entity.Corpses;
import io.github.term4.polyp.mechanics.projectile.ProjectileSnapshot;
import io.github.term4.polyp.mechanics.projectile.types.Pearl;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 1.8 EntityEnderPearl: a dead thrower kills the pearl before its next move, a game's own death included. */
class PearlThrowerDeathTest extends HeadlessServerTest {

    @Test
    void aPearlDiesWithItsThrower() {
        Pos start = new Pos(5.5, 65, 5.5);
        FakePlayer shooter = FakePlayer.connect(instance, start, "PearlDier");
        PearlEntity pearl = pearl(shooter, 0);
        try {
            pearl.tick(50L);
            assertFalse(pearl.isRemoved(), "flying while the thrower lives");
            Corpses.died(shooter.player); // a game's death: the fatal hit cancelled, a respawn wait instead
            for (int tick = 2; tick <= 4 && !pearl.isRemoved(); tick++) pearl.tick(tick * 50L);
            assertTrue(pearl.isRemoved(), "gone before it lands");
            assertEquals(start, shooter.player.getPosition(), "nobody was teleported");
        } finally {
            if (!pearl.isRemoved()) pearl.remove();
            shooter.player.remove();
        }
    }

    @Test
    void offItOutlivesItsThrower() {
        FakePlayer shooter = FakePlayer.connect(instance, new Pos(7.5, 65, 5.5), "PearlKeeper");
        PearlEntity pearl = pearl(shooter, -1);
        try {
            Corpses.died(shooter.player);
            for (int tick = 1; tick <= 3; tick++) pearl.tick(tick * 50L);
            assertFalse(pearl.isRemoved(), "still flying");
        } finally {
            if (!pearl.isRemoved()) pearl.remove();
            shooter.player.remove();
        }
    }

    private PearlEntity pearl(FakePlayer shooter, int despawnTicks) {
        PearlEntity pearl = new PearlEntity(shooter.player, EntityType.ENDER_PEARL,
                ProjectileSnapshot.of(shooter.player, Pearl.INSTANCE), ProjectileTypeConfig.builder().build());
        pearl.setShooterDeathDespawnTicks(despawnTicks);
        pearl.setInstance(instance, new Pos(20.5, 90, 5.5)).join();
        pearl.setVelocity(new Vec(0, 4, 0)); // up and away from anything to land on
        return pearl;
    }
}
