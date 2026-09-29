package io.github.term4.polyp.platform.player;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vanilla relays a swing only once the last is half done: one every four ticks, plus a second click in the same tick. */
class SwingRelayGateTest extends HeadlessServerTest {

    @Test
    void everyFourthTickRelays() {
        instance.loadChunk(0, 56).join();
        FakePlayer swinger = FakePlayer.connect(instance, new Pos(0.5, 64, 896.5), "SwingA");
        FakePlayer viewer = FakePlayer.connect(instance, new Pos(2.5, 64, 896.5), "SwingV");
        try {
            OptimizedPlayer op = (OptimizedPlayer) swinger.player;
            long deadline = System.currentTimeMillis() + 2000;
            while (!op.getViewers().contains(viewer.player) && System.currentTimeMillis() < deadline) Thread.onSpinWait();
            assertTrue(op.getViewers().contains(viewer.player), "viewed");

            viewer.sent.clear();
            op.swingMainHand(true);
            op.swingMainHand(true);
            assertEquals(2, swings(viewer, op), "a second click inside the same tick restarts the arm");
            for (int tick = 1; tick <= 3; tick++) {
                op.tick(System.nanoTime());
                op.swingMainHand(true);
            }
            assertEquals(2, swings(viewer, op), "not half done before tick 4");
            op.tick(System.nanoTime());
            op.swingMainHand(true);
            assertEquals(3, swings(viewer, op));

            // haste I shortens the swing to 5 ticks: half done at 2, so the third tick relays
            for (int tick = 1; tick <= 6; tick++) op.tick(System.nanoTime());
            op.addEffect(new Potion(PotionEffect.HASTE, 0, 200));
            viewer.sent.clear();
            op.swingMainHand(true);
            for (int tick = 1; tick <= 2; tick++) {
                op.tick(System.nanoTime());
                op.swingMainHand(true);
            }
            assertEquals(1, swings(viewer, op));
            op.tick(System.nanoTime());
            op.swingMainHand(true);
            assertEquals(2, swings(viewer, op), "haste: the third tick");
        } finally {
            swinger.player.remove();
            viewer.player.remove();
        }
    }

    private static long swings(FakePlayer viewer, OptimizedPlayer of) {
        return viewer.sent(EntityAnimationPacket.class).stream()
                .filter(p -> p.entityId() == of.getEntityId() && p.animation() == EntityAnimationPacket.Animation.SWING_MAIN_ARM)
                .count();
    }
}
