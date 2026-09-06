package io.github.term4.polyp.world;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxRegistry;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlaceSoundPositionTest extends HeadlessServerTest {

    private MechanicsProfile previous;

    @BeforeEach
    void keepScope() { previous = Polyp.getInstance().profiles().global(); }

    // restore, never null: other classes resolve their configs off whatever global is already installed
    @AfterEach
    void restoreScope() { Polyp.getInstance().profiles().setGlobal(previous); }

    /** Vanilla's ItemBlock/BlockItem sounds the block CENTER; client-side-sound mods dedup against it. */
    @Test
    void breakSoundsAtCenter() {
        AtomicReference<Point> at = new AtomicReference<>();
        Polyp.getInstance().profiles().setGlobal(MechanicsProfile.builder()
                .set(MechanicsKeys.FX, FxRegistry.empty().register(Fx.BLOCK_BREAK, ctx -> at.set(ctx.position())))
                .build());
        FakePlayer breaker = FakePlayer.connect(instance, new Pos(0.5, 41, 0.5), "Breaker");
        try {
            var refused = new PlayerBlockBreakEvent(breaker.player, instance, Block.STONE, Block.AIR, new BlockVec(-4, 40, 10), BlockFace.TOP);
            EventDispatcher.call(refused);
            refused.setCancelled(true); // a guard that runs after the emitter's node
            MinecraftServer.getSchedulerManager().processTickEnd();
            assertNull(at.get(), "a refused dig makes no sound");

            EventDispatcher.call(new PlayerBlockBreakEvent(breaker.player, instance, Block.STONE, Block.AIR, new BlockVec(-4, 40, 10), BlockFace.TOP));
            MinecraftServer.getSchedulerManager().processTickEnd();
            assertNotNull(at.get(), "the break emitter ran");
            assertEquals(-3.5, at.get().x(), 1e-9);
            assertEquals(40.5, at.get().y(), 1e-9);
            assertEquals(10.5, at.get().z(), 1e-9);
        } finally {
            breaker.player.remove();
        }
    }

    @Test
    void placeSoundsAtTheBlockCenter() {
        AtomicReference<Point> at = new AtomicReference<>();
        Polyp.getInstance().profiles().setGlobal(MechanicsProfile.builder()
                .set(MechanicsKeys.FX, FxRegistry.empty().register(Fx.BLOCK_PLACE, ctx -> at.set(ctx.position())))
                .build());
        FakePlayer placer = FakePlayer.connect(instance, new Pos(0.5, 41, 0.5), "Placer");
        try {
            EventDispatcher.call(new PlayerBlockPlaceEvent(placer.player, instance, Block.STONE,
                    BlockFace.TOP, new Pos(-4, 64, 10).asBlockVec(), new Pos(0.5, 1, 0.5), PlayerHand.MAIN));
            instance.setBlock(-4, 64, 10, Block.STONE); // the listener sounds what landed, read at end of tick
            MinecraftServer.getSchedulerManager().processTickEnd();

            assertNotNull(at.get(), "the place emitter ran");
            assertEquals(-3.5, at.get().x(), 1e-9);
            assertEquals(64.5, at.get().y(), 1e-9);
            assertEquals(10.5, at.get().z(), 1e-9);
        } finally {
            instance.setBlock(-4, 64, 10, Block.AIR);
            placer.player.remove();
        }
    }

    /** A rule that refuses after the event (a bed with its head blocked lands as air) and a later guard both stay silent. */
    @Test
    void refusedPlacementsAreSilent() {
        AtomicReference<Point> at = new AtomicReference<>();
        Polyp.getInstance().profiles().setGlobal(MechanicsProfile.builder()
                .set(MechanicsKeys.FX, FxRegistry.empty().register(Fx.BLOCK_PLACE, ctx -> at.set(ctx.position())))
                .build());
        FakePlayer placer = FakePlayer.connect(instance, new Pos(0.5, 41, 0.5), "Refused");
        try {
            EventDispatcher.call(new PlayerBlockPlaceEvent(placer.player, instance, Block.RED_BED,
                    BlockFace.TOP, new Pos(-6, 64, 10).asBlockVec(), new Pos(0.5, 1, 0.5), PlayerHand.MAIN));
            MinecraftServer.getSchedulerManager().processTickEnd();
            assertNull(at.get(), "nothing landed, so nothing sounds");

            var guarded = new PlayerBlockPlaceEvent(placer.player, instance, Block.STONE,
                    BlockFace.TOP, new Pos(-6, 40, 10).asBlockVec(), new Pos(0.5, 1, 0.5), PlayerHand.MAIN);
            EventDispatcher.call(guarded);
            guarded.setCancelled(true); // a guard that runs after the emitter's node
            MinecraftServer.getSchedulerManager().processTickEnd();
            assertNull(at.get(), "a refused placement makes no sound");
        } finally {
            placer.player.remove();
        }
    }
}
