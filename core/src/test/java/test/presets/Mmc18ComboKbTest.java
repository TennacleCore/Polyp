package test.presets;

import io.github.term4.polyp.mechanics.knockback.KnockbackSnapshot;
import io.github.term4.polyp.presets.mmc18.Knockback;
import io.github.term4.polyp.presets.mmc18.Mmc18;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import io.github.term4.polyp.tracking.motion.MotionTracker;
import io.github.term4.polyp.tracking.motion.VelocityConfig;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MineMen's combo-duel vertical, captured 2026-09-21 on their old KB: the wire short is
 * {@code trunc((VY + 0.3614) * 8000)} with VY the server's motY at the hit, floored to +-0.05, the ground hit the add alone.
 * Air ticks 0 to 15 all matched once the drag was the float 0.98; the shorts here are those captures.
 */
class Mmc18ComboKbTest extends HeadlessServerTest {
    private static Instance scoped;

    @BeforeAll
    static void scope() {
        scoped = flatInstance(Mmc18.combo());
    }

    private static void tick() { EventDispatcher.call(new InstanceTickEvent(scoped, 0, 0)); }

    private static void move(Player p, double y, boolean onGround) {
        Pos pos = new Pos(8.5, y, 8.5);
        p.refreshPosition(pos, true, false);
        p.refreshOnGround(onGround);
        EventDispatcher.call(new PlayerMoveEvent(p, pos, onGround));
    }

    /** The victim's broadcast vertical in 1.8 shorts. */
    private static int hitShorts(FakePlayer victim, FakePlayer attacker) {
        victim.sent.clear();
        services.knockback().apply(new KnockbackSnapshot(victim.player, true, attacker.player, null, null, Knockback.combo()));
        EntityVelocityPacket v = victim.sent.stream().filter(EntityVelocityPacket.class::isInstance)
                .map(EntityVelocityPacket.class::cast).filter(x -> x.entityId() == victim.player.getEntityId())
                .findFirst().orElseThrow(() -> new AssertionError("no velocity"));
        return (int) Math.round(v.velocity().y() * 8000);
    }

    private static double airTickVy(int tick) {
        double vy = 0;
        for (int i = 0; i < tick; i++) vy = (vy - VelocityConfig.GRAVITY) * VelocityConfig.DRAG_V;
        return vy;
    }

    @Test
    void groundHitAddAlone() {
        FakePlayer victim = FakePlayer.connect(scoped, new Pos(8.5, 200, 8.5), "ComboG");
        FakePlayer attacker = FakePlayer.connect(scoped, new Pos(6.5, 200, 8.5, 90f, 0f), "ComboGA");
        try {
            for (int i = 0; i < 3; i++) { move(victim.player, 200.0, true); tick(); }
            assertEquals(2891, hitShorts(victim, attacker));
        } finally {
            victim.player.remove();
            attacker.player.remove();
        }
    }

    @Test
    void airTickOneFoldsMotY() {
        FakePlayer victim = FakePlayer.connect(scoped, new Pos(8.5, 200, 8.5), "Combo1");
        FakePlayer attacker = FakePlayer.connect(scoped, new Pos(6.5, 200, 8.5, 90f, 0f), "Combo1A");
        try {
            for (int i = 0; i < 3; i++) { move(victim.player, 200.0, true); tick(); }
            move(victim.player, 200.0, false); // stepped off, before the first air travel step
            assertEquals(2263, hitShorts(victim, attacker)); // 2264 with a 0.98 drag
        } finally {
            victim.player.remove();
            attacker.player.remove();
        }
    }

    @Test
    void airTickFiveFloors() {
        FakePlayer victim = FakePlayer.connect(scoped, new Pos(8.5, 200, 8.5), "Combo5");
        FakePlayer attacker = FakePlayer.connect(scoped, new Pos(6.5, 200, 8.5, 90f, 0f), "Combo5A");
        try {
            move(victim.player, 200.0, false);
            MotionTracker.foldDelivered(victim.player, new Vec(0, airTickVy(5), 0));
            assertEquals(-400, hitShorts(victim, attacker)); // -0.0152 raw
        } finally {
            victim.player.remove();
            attacker.player.remove();
        }
    }

    @Test
    void risingVictimUncapped() {
        FakePlayer victim = FakePlayer.connect(scoped, new Pos(8.5, 200, 8.5), "ComboJ");
        FakePlayer attacker = FakePlayer.connect(scoped, new Pos(6.5, 200, 8.5, 90f, 0f), "ComboJA");
        try {
            move(victim.player, 200.0, false);
            MotionTracker.foldDelivered(victim.player, new Vec(0, 0.3332, 0)); // the jump's first air tick
            assertEquals(5556, hitShorts(victim, attacker)); // 0.6946, past the melee model's 0.3614 cap
        } finally {
            victim.player.remove();
            attacker.player.remove();
        }
    }

    @Test
    void noHoldAboveRelease() {
        FakePlayer victim = FakePlayer.connect(scoped, new Pos(8.5, 200, 8.5), "Combo9");
        FakePlayer attacker = FakePlayer.connect(scoped, new Pos(6.5, 200, 8.5, 90f, 0f), "Combo9A");
        try {
            move(victim.player, 200.0, false);
            MotionTracker.foldDelivered(victim.player, new Vec(0, airTickVy(9), 0));
            assertEquals(-2322, hitShorts(victim, attacker)); // the melee model would hold 2891 here
        } finally {
            victim.player.remove();
            attacker.player.remove();
        }
    }
}
