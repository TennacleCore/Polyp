package io.github.term4.polyp.mechanics.damage.silent;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A legacy client's silent hit swallows its own health echo, and only while the window is open. */
class HurtSuppressionTest extends HeadlessServerTest {

    @Test
    void theWindowSwallowsTheHealthEcho() {
        FakePlayer victim = FakePlayer.connect(instance, new Pos(0.5, 41, 0.5), "SilentA");
        HurtSuppression.setSuppressHealthPackets(victim.player, true);
        victim.sent.clear();
        victim.player.setHealth(7f);
        assertTrue(victim.sent(UpdateHealthPacket.class).isEmpty(), "the client hears nothing about it");
        HurtSuppression.setSuppressHealthPackets(victim.player, false);
        assertEquals(7f, victim.player.getHealth(), 0.001f, "the server still took it");
    }

    @Test
    void outsideTheWindowTheEchoGoesOut() {
        FakePlayer victim = FakePlayer.connect(instance, new Pos(2.5, 41, 2.5), "SilentB");
        victim.sent.clear();
        victim.player.setHealth(5f);
        assertFalse(victim.sent(UpdateHealthPacket.class).isEmpty(), "an ordinary change is sent");
    }
}
