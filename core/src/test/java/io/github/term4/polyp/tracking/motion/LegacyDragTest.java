package io.github.term4.polyp.tracking.motion;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.presets.vanilla18.Movement;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.instance.Instance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Every vanilla version multiplies the float 0.98F and 0.91F into a double and takes the ground friction product in float. */
class LegacyDragTest extends HeadlessServerTest {
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
}
