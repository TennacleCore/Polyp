package io.github.term4.polyp.mechanics.explosion;

import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** MineMen clears the blocks a tick after the push lands; vanilla clears them in the same tick. */
class BlockBreakDelayTest extends HeadlessServerTest {

    // a private world per blast: a deferred clear left armed on the shared instance fires under someone else's test
    private static Block padAfterBlast(ExplosionConfig config, boolean tick) {
        Instance inst = flatInstance(MechanicsProfile.builder().build());
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) inst.setBlock(100 + dx, 65, 100 + dz, Block.OAK_PLANKS);
        new ExplosionSystem(polyp, config).explode(inst, new Pos(100.5, 66.5, 100.5), 4.0f, null);
        if (tick) inst.scheduler().processTick();
        return inst.getBlock(100, 65, 100);
    }

    @Test
    void minemenClearsATickLate() {
        var mmc = io.github.term4.polyp.presets.mmc18.Explosion.config();
        assertEquals(Block.OAK_PLANKS, padAfterBlast(mmc, false), "the pad still stands when the push goes out");
        assertEquals(Block.AIR, padAfterBlast(mmc, true), "and goes on the next tick");
    }

    @Test
    void vanillaClearsWithThePush() {
        assertEquals(Block.AIR, padAfterBlast(Vanilla18.explosion(), false));
    }
}
