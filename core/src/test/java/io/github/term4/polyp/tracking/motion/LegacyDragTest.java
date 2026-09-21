package io.github.term4.polyp.tracking.motion;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.entity.PrimedTnt;
import io.github.term4.polyp.mechanics.explosion.TntConfigResolver;
import io.github.term4.polyp.presets.vanilla18.Movement;
import io.github.term4.polyp.presets.vanilla18.Tnt;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Every vanilla version multiplies the float 0.98F and 0.91F into a double, and takes the ground friction product in
 * float; 1.8 alone wrote its non-living gravity and TNT literals as floats too ({@code VelocityConfig.floatLiterals}).
 */
class LegacyDragTest extends HeadlessServerTest {
    private static final double WIDE_04 = VelocityConfig.widen(0.04);
    private static final double WIDE_98 = VelocityConfig.widen(0.98);

    private static void tick(Instance inst) { EventDispatcher.call(new InstanceTickEvent(inst, 0, 0)); }

    private static void move(Player p, double y, boolean onGround) {
        Pos pos = new Pos(8.5, y, 8.5);
        p.refreshPosition(pos, true, false);
        p.refreshOnGround(onGround);
        EventDispatcher.call(new PlayerMoveEvent(p, pos, onGround));
    }

    private static Instance legacy() {
        return flatInstance(MechanicsProfile.builder().set(MechanicsKeys.VELOCITY, Movement.velocity()).build());
    }

    @Test
    void groundedMotYWidenedDrag() {
        for (Instance inst : new Instance[]{legacy(), flatInstance(null)}) {
            FakePlayer fp = FakePlayer.connect(inst, new Pos(8.5, 64, 8.5), "Drag" + inst.hashCode() % 1000);
            try {
                for (int i = 0; i < 3; i++) { move(fp.player, 64.0, true); tick(inst); }
                double motY = MotionTracker.serverMotY(fp.player, 0, true);
                assertEquals(-0.08 * VelocityConfig.DRAG_V, motY, 0.0);
                assertNotEquals(-0.08 * 0.98, motY);
            } finally {
                fp.player.remove();
            }
        }
    }

    @Test
    void groundFrictionFloatProduct() {
        Instance inst = legacy();
        FakePlayer fp = FakePlayer.connect(inst, new Pos(8.5, 64, 8.5), "Fric18");
        try {
            move(fp.player, 64.0, true);
            tick(inst);
            assertEquals((double) (0.6f * 0.91f), MotionTracker.frictionPerTick(fp.player, false), 0.0);
            assertEquals((double) 0.91f, MotionTracker.frictionPerTick(fp.player, true), 0.0);
        } finally {
            fp.player.remove();
        }
    }

    @Test
    void tntGravityFloatUnder18() {
        Instance legacy = legacy();
        PrimedTnt tnt = TntConfigResolver.spawn(services.explosion(), legacy, new BlockVec(4, 70, 14), Tnt.config());
        double y0 = tnt.getVelocity().div(20).y();
        assertEquals(VelocityConfig.widen(0.2), y0, 1e-12, "0.2F launch");
        tnt.tick(0);
        assertEquals((y0 - WIDE_04) * WIDE_98, tnt.getVelocity().div(20).y(), 1e-12);
        tnt.remove();

        Instance modern = flatInstance(null);
        PrimedTnt tnt2 = TntConfigResolver.spawn(services.explosion(), modern, new BlockVec(4, 70, 14), Tnt.config());
        double y1 = tnt2.getVelocity().div(20).y();
        tnt2.tick(0);
        assertEquals((y1 - 0.04) * WIDE_98, tnt2.getVelocity().div(20).y(), 1e-12);
        tnt2.remove();
    }
}
