package io.github.term4.polyp.mechanics.blocks;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerFinishDiggingEvent;
import net.minestom.server.event.player.PlayerStartDiggingEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.utils.block.BlockBreakCalculation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PlayerInteractManager: a finished dig breaks only past 70% of the dig; short of it, the server finishes it at 100%. */
class DigFinishTest extends HeadlessServerTest {

    private static final BlockVec AT = new BlockVec(0, 64, 0);

    @BeforeAll
    static void install() {
        BlocksSystem.install(polyp);
    }

    private static Instance world(boolean gated) {
        BlocksConfig.Builder cfg = BlocksConfig.builder();
        if (gated) cfg.digFinishShare(0.7).digFinishLate(true);
        Instance inst = flatInstance(MechanicsProfile.builder().set(MechanicsKeys.BLOCKS, cfg.build()).build());
        inst.setBlock(AT, Block.DIRT);
        return inst;
    }

    private static void ticks(Instance inst, int n) {
        for (int i = 0; i < n; i++) EventDispatcher.call(new InstanceTickEvent(inst, 0, 0));
    }

    /** Digs for {@code held} ticks (none: no start at all), finishes, and says whether the break went through. */
    private static boolean dig(Instance inst, Player p, int held) {
        if (held >= 0) EventDispatcher.call(new PlayerStartDiggingEvent(p, inst, Block.DIRT, AT, BlockFace.TOP));
        ticks(inst, Math.max(0, held));
        EventDispatcher.call(new PlayerFinishDiggingEvent(p, inst, Block.DIRT, AT));
        var event = new PlayerBlockBreakEvent(p, inst, Block.DIRT, Block.AIR, AT, BlockFace.TOP);
        EventDispatcher.call(event);
        return !event.isCancelled();
    }

    @Test
    void anEarlyFinishWaitsForTheServer() {
        Instance inst = world(true);
        FakePlayer fp = FakePlayer.connect(inst, new Pos(0.5, 64, 2.5), "DigEarly");
        try {
            int whole = BlockBreakCalculation.breakTicks(Block.DIRT, fp.player);
            assertFalse(dig(inst, fp.player, 2), "3 ticks of " + whole + ": refused");
            assertTrue(inst.getBlock(AT).compare(Block.DIRT), "still there");
            for (int i = 0; i < whole && !inst.getBlock(AT).compare(Block.AIR); i++) MinecraftServer.getSchedulerManager().processTick();
            assertTrue(inst.getBlock(AT).compare(Block.AIR), "the server breaks it at the whole dig");
        } finally {
            fp.player.remove();
        }
    }

    @Test
    void seventyPercentIsEnough() {
        Instance inst = world(true);
        FakePlayer fp = FakePlayer.connect(inst, new Pos(0.5, 64, 2.5), "DigFull");
        try {
            int whole = BlockBreakCalculation.breakTicks(Block.DIRT, fp.player);
            assertTrue(dig(inst, fp.player, (int) Math.ceil(whole * 0.7)), "past 70%");
        } finally {
            fp.player.remove();
        }
    }

    @Test
    void unsetTakesAnyFinish() {
        Instance inst = world(false);
        FakePlayer fp = FakePlayer.connect(inst, new Pos(0.5, 64, 2.5), "DigFree");
        try {
            assertTrue(dig(inst, fp.player, 0), "Minestom's own: any finish");
        } finally {
            fp.player.remove();
        }
    }

    @Test
    void aFinishWithNoStartBreaksNothing() {
        Instance inst = world(true);
        FakePlayer fp = FakePlayer.connect(inst, new Pos(0.5, 64, 2.5), "DigNone");
        try {
            assertFalse(dig(inst, fp.player, -1));
        } finally {
            fp.player.remove();
        }
    }
}
