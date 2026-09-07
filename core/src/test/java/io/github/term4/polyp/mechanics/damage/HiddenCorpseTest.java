package io.github.term4.polyp.mechanics.damage;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The corpse hide restores at the respawn under any scope: the hide remembers itself. */
class HiddenCorpseTest extends HeadlessServerTest {

    @Test
    void aHiddenCorpseReturnsUnderAnyScope() {
        MechanicsProfile prev = polyp.profiles().global();
        polyp.profiles().setGlobal(MechanicsProfile.builder()
                .set(MechanicsKeys.DEATH, DeathConfig.builder().hideCorpse(true).deathAnimationTicks(1).build()).build());
        FakePlayer p = FakePlayer.connect(instance, new Pos(0.5, 65, 0.5), "Corpse");
        try {
            p.player.kill();
            assertTrue(p.player.isDead());
            for (int i = 0; i < 5 && p.player.isAutoViewable(); i++) { // the hide rides the entity's own scheduler
                p.player.tick(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
            }
            assertFalse(p.player.isAutoViewable(), "the corpse hides after the animation");

            // the respawn lands under a scope that hides no corpse: the lobby, the next game
            polyp.profiles().setGlobal(MechanicsProfile.builder()
                    .set(MechanicsKeys.DEATH, DeathConfig.builder().hideCorpse(false).build()).build());
            p.player.respawn();
            for (int i = 0; i < 3 && !p.player.isAutoViewable(); i++) { // the restore rides the entity's scheduler too
                p.player.tick(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
            }
            assertTrue(p.player.isAutoViewable(), "the body is back whatever the scope says now");
        } finally {
            polyp.profiles().setGlobal(prev);
            p.player.remove();
        }
    }
}
