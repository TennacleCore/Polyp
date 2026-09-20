package io.github.term4.polyp.mechanics.explosion;

import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.api.event.explosion.TntPrimeEvent;
import io.github.term4.polyp.entity.PrimedTnt;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A blast centered inside the wool floor beside a TNT: vanilla's rays meet the wool, MineMen's see the crater. */
class ExposureAfterBreakTest extends HeadlessServerTest {

    private record Push(Vec perTick, double falloff) {}

    private static Push pushOn(ExplosionConfig config) {
        Instance inst = flatInstance(MechanicsProfile.builder().build());
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++) inst.setBlock(100 + dx, 65, 100 + dz, Block.WHITE_WOOL);
        ExplosionSystem explosion = new ExplosionSystem(polyp, config);
        PrimedTnt tnt = explosion.primeTnt(inst, new Pos(100.5, 66, 100.5), null, TntPrimeEvent.Cause.PLACEMENT);
        assertNotNull(tnt);
        try {
            Point feet = tnt.getPosition();
            Point center = feet.add(1.0, -0.8, 0.0); // inside the neighbouring wool block, below the floor
            Vec before = tnt.getVelocity();
            explosion.explode(inst, center, 2.0f, null);
            Vec perTick = tnt.getVelocity().sub(before).div(ServerFlag.SERVER_TICKS_PER_SECOND);
            return new Push(perTick, 1.0 - feet.distance(center) / 4.0);
        } finally {
            tnt.remove();
        }
    }

    @Test
    void craterDoesNotShield() {
        Push vanilla = pushOn(Vanilla18.explosion());
        Push crater = pushOn(Vanilla18.explosion().toBuilder().exposureAfterBreak(true).build());
        assertTrue(vanilla.perTick().length() < 0.6 * vanilla.falloff(), "intact wool shields most rays: " + vanilla.perTick());
        assertEquals(crater.falloff(), crater.perTick().length(), 0.02, "the crater's rays are all clear: full falloff impact");
    }
}
