package io.github.term4.polyp.mechanics.damage;

import io.github.term4.polyp.mechanics.damage.types.generic.GenericDamage;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.network.packet.server.play.DamageEventPacket;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A replacement hit inside the i-frame window moves the health and nothing else, as 1.8's attackEntityFrom with
 * its flag false: no animation broadcast, no sound, while the victim's own client flashes off the health drop.
 */
class ReplacementHitFeedbackTest extends HeadlessServerTest {

    @Test
    void aReplacementMovesHealthOnly() {
        FakePlayer victim = FakePlayer.connect(instance, new Pos(530.5, 65, 530.5), "RepVictim");
        FakePlayer attacker = FakePlayer.connect(instance, new Pos(531.5, 65, 530.5), "RepAttacker");
        try {
            victim.player.addViewer(attacker.player);
            victim.player.setHealth(20f);
            services.damage().apply(DamageSnapshot.of(victim.player, GenericDamage.INSTANCE)
                    .withSource(attacker.player).withAmount(4f));
            assertEquals(16f, victim.player.getHealth(), "the fresh hit landed");

            victim.sent.clear();
            attacker.sent.clear();
            services.damage().apply(DamageSnapshot.of(victim.player, GenericDamage.INSTANCE)
                    .withSource(attacker.player).withAmount(6f));
            assertEquals(14f, victim.player.getHealth(), "the two above the window's four landed");
            assertFalse(victim.sent(UpdateHealthPacket.class).isEmpty(), "the drop reaches the victim, so their client flashes");
            assertTrue(victim.sent(DamageEventPacket.class).isEmpty(), "no animation to the victim");
            assertTrue(attacker.sent(DamageEventPacket.class).isEmpty(), "none to the viewer");
            assertTrue(attacker.sent(SoundEffectPacket.class).isEmpty(), "and no sound");
        } finally {
            attacker.player.remove();
            victim.player.remove();
        }
    }

    @Test
    void aLethalReplacementJustDies() {
        LivingEntity victim = new LivingEntity(EntityType.ZOMBIE);
        victim.setInstance(instance, new Pos(534.5, 65, 534.5)).join();
        FakePlayer viewer = FakePlayer.connect(instance, new Pos(535.5, 65, 534.5), "RepViewer");
        try {
            victim.addViewer(viewer.player);
            services.damage().apply(DamageSnapshot.of(victim, GenericDamage.INSTANCE).withAmount(4f));
            viewer.sent.clear();
            services.damage().apply(DamageSnapshot.of(victim, GenericDamage.INSTANCE).withAmount(30f));
            assertTrue(victim.isDead(), "the replacement took the rest");
            assertTrue(viewer.sent(DamageEventPacket.class).isEmpty(), "no hurt animation before the death");
            assertTrue(viewer.sent(SoundEffectPacket.class).isEmpty(), "and no hurt sound");
        } finally {
            viewer.player.remove();
            victim.remove();
        }
    }
}
