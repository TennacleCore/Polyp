package io.github.term4.polyp.mechanics.damage.types.burning;

import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.presets.Preset;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** BedWars fire takes on contact; every other Hypixel mode waits out the warmup, which lands on the second felt hit. */
class BedwarsFireTest extends HeadlessServerTest {

    /** Contact ticks standing in fire before the victim ignites, under {@code preset}. */
    private int ticksToIgnite(Preset preset, Pos pos) {
        MechanicsProfile before = polyp.profiles().global();
        polyp.profiles().setGlobal(preset.profile());
        instance.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), Block.FIRE);
        LivingEntity mob = zombie(pos);
        mob.setHealth(20f);
        try {
            for (int t = 1; t <= 100; t++) {
                BurningTicker.INSTANCE.tick(mob, services.damage());
                if (mob.getFireTicks() > 0) return t;
            }
            return -1;
        } finally {
            mob.remove();
            instance.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), Block.AIR);
            polyp.profiles().setGlobal(before);
        }
    }

    @Test
    void bedwarsFireTakesOnContact() {
        assertEquals(1, ticksToIgnite(Preset.HYPIXEL_BEDWARS, new Pos(40.5, 65, 700.5)),
                "the first tick standing in it");
        assertTrue(ticksToIgnite(Preset.HYPIXEL, new Pos(40.5, 65, 704.5)) > 1,
                "every other Hypixel mode keeps the warmup");
    }
}
