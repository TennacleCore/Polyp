package io.github.term4.polyp.mechanics.attack;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link AttackLog}'s geometry queries: the nearest-point reach the guard cancels on, and the sight clip. */
class ReachGuardTest extends HeadlessServerTest {

    @Test
    void reachIsNearestPointAndRotationIndependent() {
        FakePlayer attacker = FakePlayer.connect(instance, new Pos(600.5, 65, 600.5, 0f, 0f), "ReachA");
        FakePlayer target = FakePlayer.connect(instance, new Pos(604.5, 65, 600.5), "ReachB");
        try {
            double facing = AttackLog.optimalReach(attacker.player, target.player, polyp.clientInfo());
            attacker.player.refreshPosition(new Pos(600.5, 65, 600.5, 180f, 0f));
            double away = AttackLog.optimalReach(attacker.player, target.player, polyp.clientInfo());
            assertEquals(facing, away, 1e-9, "the lower bound never reads the attacker's yaw");
            assertTrue(facing > 3.0 && facing < 4.0, "box to eye, not centre to centre: " + facing);
        } finally {
            attacker.player.remove();
            target.player.remove();
        }
    }

    @Test
    void sightIsClippedByTerrain() {
        FakePlayer attacker = FakePlayer.connect(instance, new Pos(610.5, 65, 600.5, 0f, 0f), "SightA");
        FakePlayer target = FakePlayer.connect(instance, new Pos(613.5, 65, 600.5), "SightB");
        try {
            assertFalse(AttackLog.obstructed(attacker.player, target.player), "open ground");
            instance.setBlock(612, 65, 600, Block.STONE);
            instance.setBlock(612, 66, 600, Block.STONE);
            assertTrue(AttackLog.obstructed(attacker.player, target.player), "a wall between them");
        } finally {
            instance.setBlock(612, 65, 600, Block.AIR);
            instance.setBlock(612, 66, 600, Block.AIR);
            attacker.player.remove();
            target.player.remove();
        }
    }
}
