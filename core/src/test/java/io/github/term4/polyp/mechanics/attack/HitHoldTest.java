package io.github.term4.polyp.mechanics.attack;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionStatusPacket;
import net.minestom.server.network.packet.server.play.DamageEventPacket;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.server.network.packet.server.play.SoundEffectPacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hypixel's hit (captured 2026-10-01): viewers see the flash at once, the victim takes it with their next movement packet. */
class HitHoldTest extends HeadlessServerTest {

    @BeforeAll
    static void installAttack() {
        AttackSystem.install(polyp);
    }

    private record Duo(FakePlayer attacker, FakePlayer victim) {
        void remove() {
            attacker.player.remove();
            victim.player.remove();
        }
    }

    private static Duo hit(boolean hold, String tag) {
        AttackConfig attack = Vanilla18.attack().toBuilder().hitWaitsForVictimMove(hold).build();
        Instance inst = flatInstance(MechanicsProfile.builder().set(MechanicsKeys.ATTACK, attack).build());
        FakePlayer attacker = FakePlayer.connect(inst, new Pos(8.5, 64, 8.5, 0f, 0f), "Ha" + tag);
        FakePlayer victim = FakePlayer.connect(inst, new Pos(8.5, 64, 10.5), "Hv" + tag);
        victim.player.setHealth(20f);
        attacker.sent.clear();
        victim.sent.clear();
        polyp.module(AttackSystem.class).apply(new AttackSnapshot(attacker.player, victim.player, null));
        return new Duo(attacker, victim);
    }

    @Test
    void theVictimWaitsToMove() {
        Duo d = hit(true, "Wait");
        try {
            assertEquals(1, d.attacker().sent(DamageEventPacket.class).size(), "the attacker sees the flash at once");
            assertTrue(d.attacker().sent(SoundEffectPacket.class).isEmpty(), "the hurt sound waits");
            assertTrue(d.victim().sent(EntityVelocityPacket.class).isEmpty(), "no knockback yet");
            assertTrue(d.victim().sent(UpdateHealthPacket.class).isEmpty(), "no health yet");
            d.victim().player.addPacketToQueue(new ClientPlayerPositionStatusPacket(true, false));
            assertEquals(1, d.victim().sent(EntityVelocityPacket.class).size(), "the knockback rides the move");
            assertEquals(1, d.victim().sent(DamageEventPacket.class).size(), "the hurt with it");
            assertFalse(d.victim().sent(UpdateHealthPacket.class).isEmpty(), "the health with it");
            assertFalse(d.attacker().sent(SoundEffectPacket.class).isEmpty(), "the attacker hears it now");
        } finally {
            d.remove();
        }
    }

    @Test
    void aStillVictimStillGetsIt() {
        Duo d = hit(true, "Still");
        try {
            assertTrue(d.victim().sent(EntityVelocityPacket.class).isEmpty(), "held");
            for (int i = 0; i < 2; i++) d.victim().player.scheduler().processTick();
            assertEquals(1, d.victim().sent(EntityVelocityPacket.class).size(), "two ticks at most");
        } finally {
            d.remove();
        }
    }

    @Test
    void offTheHitLandsAtOnce() {
        Duo d = hit(false, "Now");
        try {
            assertEquals(1, d.victim().sent(EntityVelocityPacket.class).size());
            assertFalse(d.attacker().sent(SoundEffectPacket.class).isEmpty());
        } finally {
            d.remove();
        }
    }
}
