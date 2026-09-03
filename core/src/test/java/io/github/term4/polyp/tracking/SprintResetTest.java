package io.github.term4.polyp.tracking;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server's sprint flag tracks the client's entity: a same-dimension move keeps it, a respawn packet clears it. */
class SprintResetTest extends HeadlessServerTest {

    @Test
    void sprintSurvivesASameDimensionMoveAndDiesWithTheClientEntity() {
        Player p = FakePlayer.connect(instance, new Pos(0.5, 65, 0.5), "Sprinter").player;
        try {
            p.setSprinting(true);
            p.setInstance(flatInstance(null), new Pos(0.5, 65, 0.5)).join();
            assertTrue(p.isSprinting(), "no respawn packet: the client keeps its entity, and its sprint");
            p.kill();
            p.respawn();
            assertFalse(p.isSprinting(), "a respawn recreates the client's entity, which starts not sprinting");
        } finally {
            p.remove();
        }
    }
}
