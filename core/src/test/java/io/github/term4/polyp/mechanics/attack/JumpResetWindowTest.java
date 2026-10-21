package io.github.term4.polyp.mechanics.attack;

import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.platform.player.PolypPlayer;
import io.github.term4.polyp.presets.hypixel.Hypixel;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A 1.8 jump reset is the victim's own client jumping on the first tick after the knockback lands (MCP-919
 * EntityLivingBase.onLivingUpdate): nothing the server sends after the knockback may overwrite it. One velocity, no
 * snap back, the victim's sprint left alone (the jump's 0.2 push needs it).
 */
class JumpResetWindowTest extends HeadlessServerTest {

    @BeforeAll
    static void installAttack() {
        AttackSystem.install(polyp);
    }

    @Test
    void vanillaLeavesTheJumpAlone() {
        check(Vanilla18.profile(), "V");
    }

    @Test
    void hypixelLeavesTheJumpAlone() {
        check(Hypixel.profile(), "H");
    }

    private static void check(MechanicsProfile profile, String tag) {
        Instance inst = flatInstance(profile);
        FakePlayer attacker = FakePlayer.connect(inst, new Pos(8.5, 64, 8.5, 0f, 0f), "Ja" + tag);
        FakePlayer victim = FakePlayer.connect(inst, new Pos(8.5, 64, 10.5, 180f, 0f), "Jv" + tag);
        try {
            polyp.clientInfo().setProtocol(victim.player, 47);
            victim.player.setHealth(20f);
            attacker.player.setSprinting(true);
            victim.player.setSprinting(true);
            attacker.sent.clear();
            victim.sent.clear();
            polyp.module(AttackSystem.class).apply(new AttackSnapshot(attacker.player, victim.player, null));
            // the reset: a sprint-jump's rise, packet by packet, as the 1.8 client sends it
            double y = 64;
            double[] rise = {0.42, 0.3332, 0.2481, 0.1645, 0.0825, 0.0019};
            for (double dy : rise) {
                y += dy;
                victim.player.addPacketToQueue(new ClientPlayerPositionPacket(new Pos(8.5, y, 10.6), (byte) 0));
                ((PolypPlayer) victim.player).tick(System.nanoTime());
            }
            int own = victim.player.getEntityId();
            long velocities = victim.sent(EntityVelocityPacket.class).stream().filter(v -> v.entityId() == own).count();
            assertEquals(1, velocities, "one knockback, never a second over the jump");
            assertTrue(victim.sent(PlayerPositionAndLookPacket.class).isEmpty(), "no snap back over the jump");
            boolean sprintCleared = victim.sent(EntityMetaDataPacket.class).stream()
                    .filter(m -> m.entityId() == own && m.entries().containsKey(0))
                    .anyMatch(m -> (((Byte) m.entries().get(0).value()) & 0x08) == 0);
            assertTrue(!sprintCleared, "the victim's own sprint never cleared by the hit");
        } finally {
            attacker.player.remove();
            victim.player.remove();
        }
    }
}
