package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.network.packet.server.play.CameraPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpectatorModeTest extends HeadlessServerTest {

    @Test
    void aLegacyClientWearsBothBodiesAndGetsItsOwnBack() {
        FakePlayer w = FakePlayer.connect(instance, new Pos(0.5, 64, 0.5), "ModeWatcher");
        FakePlayer a = FakePlayer.connect(instance, new Pos(4.5, 64, 4.5), "ModeTarget");
        try {
            polyp.clientInfo().setProtocol(w.player, 47);
            w.player.setGameMode(GameMode.SURVIVAL);
            assertNull(SpectatorMode.mode(w.player));

            assertTrue(SpectatorMode.enter(w.player, SpectatorMode.Mode.GHOST));
            assertEquals(GameMode.ADVENTURE, w.player.getGameMode());
            assertTrue(w.player.isFlying());
            assertTrue(SpectatorHud.hidden(w.player), "a 1.8 ghost has the bars spoofed off");
            assertFalse(SpectatorMode.camera(w.player, a.player), "a ghost has no camera to ride");

            assertTrue(SpectatorMode.enter(w.player, SpectatorMode.Mode.TRUE));
            assertEquals(GameMode.SPECTATOR, w.player.getGameMode());
            assertFalse(SpectatorHud.hidden(w.player), "the spoof is lifted: it would have swallowed the change");
            assertEquals(SpectatorMode.Mode.TRUE, SpectatorMode.mode(w.player));

            w.sent.clear();
            assertTrue(SpectatorMode.camera(w.player, a.player));
            assertSame(a.player, SpectatorMode.camera(w.player));
            assertEquals(a.player.getEntityId(), w.sent(CameraPacket.class).getLast().cameraId(), "the camera rides the target");
            assertEquals(a.player.getPosition(), w.player.getPosition(), "the body follows the ride");

            w.sent.clear();
            assertTrue(SpectatorMode.enter(w.player, SpectatorMode.Mode.GHOST), "back to the ghost body");
            assertEquals(w.player.getEntityId(), w.sent(CameraPacket.class).getFirst().cameraId(), "the camera comes home first");
            assertNull(SpectatorMode.camera(w.player));
            assertEquals(GameMode.ADVENTURE, w.player.getGameMode());
            assertTrue(SpectatorHud.hidden(w.player));

            assertTrue(SpectatorMode.exit(w.player));
            assertEquals(GameMode.SURVIVAL, w.player.getGameMode(), "the body walked in with comes back");
            assertFalse(w.player.isFlying());
            assertFalse(SpectatorHud.hidden(w.player));
            assertNull(SpectatorMode.mode(w.player));
            assertFalse(SpectatorMode.exit(w.player), "nothing to give back twice");
        } finally {
            w.player.remove();
            a.player.remove();
        }
    }

    @Test
    void aSevenClientHasNoSpectatorMode() {
        FakePlayer w = FakePlayer.connect(instance, new Pos(0.5, 64, 0.5), "ModeSeven");
        try {
            polyp.clientInfo().setProtocol(w.player, 5); // 1.7.10
            assertTrue(SpectatorMode.enter(w.player, SpectatorMode.Mode.GHOST));
            assertFalse(SpectatorMode.supports(w.player, SpectatorMode.Mode.TRUE));
            assertFalse(SpectatorMode.enter(w.player, SpectatorMode.Mode.TRUE), "refused, and nothing changes");
            assertEquals(SpectatorMode.Mode.GHOST, SpectatorMode.mode(w.player));
            assertEquals(GameMode.ADVENTURE, w.player.getGameMode());
            polyp.clientInfo().setProtocol(w.player, 774);
            assertTrue(SpectatorMode.supports(w.player, SpectatorMode.Mode.TRUE), "a modern client has it");
        } finally {
            SpectatorMode.exit(w.player);
            w.player.remove();
        }
    }
}
