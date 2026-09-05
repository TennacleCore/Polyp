package io.github.term4.polyp.mechanics.damage;

import io.github.term4.polyp.api.event.damage.FatalDamageEvent;
import io.github.term4.polyp.mechanics.damage.types.generic.GenericDamage;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.EventListener;
import net.minestom.server.network.packet.server.play.DamageEventPacket;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A game that cancels the killing blow still owes the hit its feedback, and only what vanilla gives: a fresh
 * blow flashes and sounds, an overdamage one inside the window does neither (attackEntityFrom's flag is false).
 */
class CancelledKillFeedbackTest extends HeadlessServerTest {

    @Test
    void anOverdamageKillStaysQuiet() {
        FakePlayer victim = FakePlayer.connect(instance, new Pos(520.5, 65, 520.5), "OdVictim");
        FakePlayer attacker = FakePlayer.connect(instance, new Pos(521.5, 65, 520.5), "OdAttacker");
        EventListener<FatalDamageEvent> respawns = EventListener.of(FatalDamageEvent.class, FatalDamageEvent::cancel);
        MinecraftServer.getGlobalEventHandler().addListener(respawns);
        try {
            victim.player.addViewer(attacker.player);
            victim.player.setHealth(6f);
            victim.sent.clear();
            attacker.sent.clear();
            services.damage().apply(DamageSnapshot.of(victim.player, GenericDamage.INSTANCE)
                    .withSource(attacker.player).withAmount(4f));
            assertEquals(1, attacker.sent(SoundEffectPacket.class).size(), "the fresh hit sounds for the viewer");
            assertEquals(1, victim.sent(DamageEventPacket.class).size(), "and flashes");

            // the replacement: 8 over the window's 4 lands 4 more, past the 2 left - a kill the game cancels
            victim.sent.clear();
            attacker.sent.clear();
            services.damage().apply(DamageSnapshot.of(victim.player, GenericDamage.INSTANCE)
                    .withSource(attacker.player).withAmount(8f));
            assertTrue(victim.player.getHealth() > 0f, "the game kept them up");
            assertEquals(0, attacker.sent(SoundEffectPacket.class).size(), "no hurt sound for the overdamage kill");
            assertEquals(0, victim.sent(DamageEventPacket.class).size(), "and no flash");
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(respawns);
            attacker.player.remove();
            victim.player.remove();
        }
    }
}
