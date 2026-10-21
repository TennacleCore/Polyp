package io.github.term4.polyp.tracking;

import io.github.term4.polyp.platform.player.PolypPlayer;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.client.play.ClientEntityActionPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSprintTest extends HeadlessServerTest {

    @Test
    void stopAfterAClearedSprint() {
        Player p = FakePlayer.connect(instance, new Pos(0.5, 65, 0.5), "Csprint").player;
        try {
            send(p, ClientEntityActionPacket.Action.START_SPRINTING);
            assertTrue(SprintTracker.isClientSprinting(polyp.sprintTracker(), p));
            p.setSprinting(false); // a landed hit
            assertTrue(SprintTracker.isClientSprinting(polyp.sprintTracker(), p), "the client has not heard");
            send(p, ClientEntityActionPacket.Action.STOP_SPRINTING);
            assertFalse(SprintTracker.isClientSprinting(polyp.sprintTracker(), p), "a stop the server already had");
            send(p, ClientEntityActionPacket.Action.START_SPRINTING);
            assertTrue(SprintTracker.isClientSprinting(polyp.sprintTracker(), p));
        } finally {
            p.remove();
        }
    }

    private static void send(Player p, ClientEntityActionPacket.Action action) {
        p.addPacketToQueue(new ClientEntityActionPacket(p.getEntityId(), action, 0));
        ((PolypPlayer) p).tick(System.nanoTime());
    }
}
