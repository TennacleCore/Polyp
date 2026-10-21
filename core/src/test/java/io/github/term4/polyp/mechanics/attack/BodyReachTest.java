package io.github.term4.polyp.mechanics.attack;

import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.Instance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A hit a non-player body submits passes the same server reach gate as a player's. */
class BodyReachTest extends HeadlessServerTest {

    @BeforeAll
    static void installAttack() {
        AttackSystem.install(polyp);
    }

    @Test
    void aBodyHitPastReachFails() {
        Instance inst = flatInstance(Vanilla18.profile());
        FakePlayer victim = FakePlayer.connect(inst, new Pos(8.5, 64, 8.5), "BodyV");
        LivingEntity body = new LivingEntity(EntityType.PLAYER);
        try {
            victim.player.setHealth(20f);
            body.setInstance(inst, new Pos(8.5, 64, 16.5, 180f, 0f)).join();
            polyp.module(AttackSystem.class).apply(new AttackSnapshot(body, victim.player, null));
            assertEquals(20f, victim.player.getHealth(), "eight blocks off: past 3 + 3");
            body.teleport(new Pos(8.5, 64, 10.5, 180f, 0f)).join();
            polyp.module(AttackSystem.class).apply(new AttackSnapshot(body, victim.player, null));
            assertTrue(victim.player.getHealth() < 20f, "two blocks off lands");
        } finally {
            body.remove();
            victim.player.remove();
        }
    }
}
