package io.github.term4.polyp.mechanics.knockback;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 1.8 EntityHuman.attack: a melee hit's velocity goes to the victim alone; a thrown hit's reaches everyone watching. */
class MeleeVelocityViewersTest extends HeadlessServerTest {

    /** The victim's velocity packets after one hit: {to the victim, to the attacker}. */
    private static long[] hit(KnockbackConfig config, boolean melee, String tag) {
        Instance inst = flatInstance(MechanicsProfile.builder().set(MechanicsKeys.KNOCKBACK, config).build());
        FakePlayer attacker = FakePlayer.connect(inst, new Pos(8.5, 64, 8.5, 0f, 0f), "Kb" + tag);
        FakePlayer victim = FakePlayer.connect(inst, new Pos(8.5, 64, 10.5), "Kv" + tag);
        try {
            int id = victim.player.getEntityId();
            attacker.sent.clear();
            victim.sent.clear();
            services.knockback().apply(new KnockbackSnapshot(victim.player, melee, attacker.player, null, null, null));
            return new long[]{
                    victim.sent(EntityVelocityPacket.class).stream().filter(v -> v.entityId() == id).count(),
                    attacker.sent(EntityVelocityPacket.class).stream().filter(v -> v.entityId() == id).count()};
        } finally {
            attacker.player.remove();
            victim.player.remove();
        }
    }

    @Test
    void aMeleeHitReachesTheVictimAlone() {
        long[] got = hit(Vanilla18.knockback(), true, "Melee");
        assertEquals(1, got[0], "the victim");
        assertEquals(0, got[1], "the attacker");
    }

    @Test
    void aThrownHitReachesTheViewers() {
        long[] got = hit(Vanilla18.knockback(), false, "Thrown");
        assertEquals(1, got[0], "the victim");
        assertEquals(1, got[1], "the attacker");
    }

    @Test
    void unsetMeleeReachesTheViewers() {
        long[] got = hit(KnockbackConfig.builder(Vanilla18.knockback()).meleeVelocityToViewers(true).build(), true, "Unset");
        assertEquals(1, got[1], "the attacker");
    }
}
