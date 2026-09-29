package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.platform.compatibility.CompatConfig.SprintGate;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.GameMode;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.EntityEffectPacket;
import net.minestom.server.network.packet.server.play.RemoveEntityEffectPacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Swimmable water drives the water gate and the always gate holds everywhere; the outgoing clamp holds FOOD against server re-sends. */
class CompatSprintTest extends HeadlessServerTest {

    private static final Pos DEEP = new Pos(0.5, 64, 832.5);
    private static final Pos DRY = new Pos(8.5, 64, 832.5);

    private static final CompatConfig FOOD = CompatConfig.builder().suppressSwim(SprintGate.FOOD).build();
    private static final CompatConfig BLINDNESS = CompatConfig.builder().suppressSwim(SprintGate.BLINDNESS).build();

    // two cells of water at x 0, the swim pose's depth; one at x 4, a wade
    private static void pool(int z) {
        instance.setBlock(0, 64, z, Block.WATER);
        instance.setBlock(0, 65, z, Block.WATER);
        instance.setBlock(4, 64, z, Block.WATER);
    }

    @Test
    void lifecycle() {
        instance.loadChunk(0, 52).join();
        pool(832);
        FakePlayer fp = FakePlayer.connect(instance, DEEP, "SprintGate");
        OptimizedPlayer op = (OptimizedPlayer) fp.player;
        try {
            op.compat().apply(FOOD, op);

            // entry: the fresh re-send goes out clamped
            fp.sent.clear();
            CompatSprint.tick(op);
            assertEquals(SprintGate.FOOD, op.compat().sprintGate());
            assertEquals(6, lastFood(fp), "activation re-send clamped");

            // any server food update mid-water stays clamped (eat/regen/exhaustion all pass through here)
            fp.sent.clear();
            op.sendPacket(new UpdateHealthPacket(20f, 20, 5f));
            assertEquals(6, lastFood(fp), "outgoing clamp holds the gate shut");

            // a swim pose that slipped in before the gate landed is forced back
            op.setPose(EntityPose.SWIMMING);
            assertEquals(EntityPose.STANDING, op.getPose(), "the water gate intercepts the pose directly");

            // exit: the real bar comes back
            fp.player.teleport(DRY).join();
            fp.sent.clear();
            CompatSprint.tick(op);
            assertNull(op.compat().sprintGate());
            assertEquals(20, lastFood(fp), "restore on leaving water");

            // FOOD -> BLINDNESS mid-water: food restored, hidden effect starts
            fp.player.teleport(DEEP).join();
            op.compat().apply(FOOD, op);
            CompatSprint.tick(op);
            op.compat().apply(BLINDNESS, op);
            fp.sent.clear();
            CompatSprint.tick(op);
            assertEquals(20, lastFood(fp), "mode switch restores the food bar");
            assertEquals(1, fp.sent(EntityEffectPacket.class).size());

            // refreshed every tick, saturated duration
            fp.sent.clear();
            CompatSprint.tick(op);
            EntityEffectPacket effect = fp.sent(EntityEffectPacket.class).getFirst();
            assertEquals(PotionEffect.BLINDNESS, effect.potion().effect());
            assertTrue(effect.potion().duration() >= 20, "saturated: the fog factor must not enter the fade region");

            // configured duration takes over on the next refresh, no transition needed
            op.compat().apply(BLINDNESS.toBuilder().blindnessGateTicks(40).build(), op);
            fp.sent.clear();
            CompatSprint.tick(op);
            assertEquals(40, fp.sent(EntityEffectPacket.class).getFirst().potion().duration());

            // exit removes the hidden effect
            fp.player.teleport(DRY).join();
            fp.sent.clear();
            CompatSprint.tick(op);
            assertEquals(1, fp.sent(RemoveEntityEffectPacket.class).size());

            // a real blindness effect owns the wire: no hidden refresh over it, no remove on exit
            fp.player.teleport(DEEP).join();
            op.addEffect(new Potion(PotionEffect.BLINDNESS, 0, 200));
            fp.sent.clear();
            CompatSprint.tick(op);
            assertEquals(0, fp.sent(EntityEffectPacket.class).size(), "the real effect already gates sprint");
            fp.player.teleport(DRY).join();
            CompatSprint.tick(op);
            assertEquals(0, fp.sent(RemoveEntityEffectPacket.class).size(), "must not strip the real effect");
            op.removeEffect(PotionEffect.BLINDNESS);
        } finally {
            fp.player.remove();
        }
    }

    @Test
    void exemptions() {
        instance.loadChunk(0, 53).join();
        pool(848);
        FakePlayer fp = FakePlayer.connect(instance, new Pos(0.5, 64, 848.5), "SprintExempt");
        OptimizedPlayer op = (OptimizedPlayer) fp.player;
        try {
            op.compat().apply(FOOD, op);

            op.compat().setLegacyClient(true);
            CompatSprint.tick(op);
            assertNull(op.compat().sprintGate(), "1.8 clients can't swim-pose");
            op.compat().setLegacyClient(false);

            op.compat().setNativeFeatures(Set.of(AnimatiumFeature.DISABLE_SWIM_POSE));
            CompatSprint.tick(op);
            assertNull(op.compat().sprintGate(), "Animatium disables natively");
            op.compat().setNativeFeatures(Set.of());

            op.setGameMode(GameMode.CREATIVE);
            CompatSprint.tick(op);
            assertNull(op.compat().sprintGate(), "mayfly bypasses the food gate anyway");
            op.setGameMode(GameMode.SURVIVAL);

            // pose entered before activation (same-tick water entry) is reset by the tick pass
            op.setPose(EntityPose.SWIMMING);
            assertEquals(EntityPose.SWIMMING, op.getPose());
            CompatSprint.tick(op);
            assertEquals(SprintGate.FOOD, op.compat().sprintGate());
            assertEquals(EntityPose.STANDING, op.getPose());
        } finally {
            fp.player.remove();
        }
    }

    /** One cell of water is a wade: the eye can never go under there, so the bar stays whole (the user, 2026-09-28). */
    @Test
    void aWadeIsNotGated() {
        instance.loadChunk(0, 54).join();
        pool(864);
        Block floor = instance.getBlock(4, 63, 864);
        FakePlayer fp = FakePlayer.connect(instance, new Pos(4.5, 64, 864.5), "SprintWade");
        OptimizedPlayer op = (OptimizedPlayer) fp.player;
        try {
            op.compat().apply(FOOD, op);
            fp.sent.clear();
            CompatSprint.tick(op);
            assertNull(op.compat().sprintGate(), "a wade through one cell");
            assertTrue(fp.sent(UpdateHealthPacket.class).isEmpty(), "nothing to re-send");

            // a second cell below is a dive waiting to happen
            instance.setBlock(4, 63, 864, Block.WATER);
            CompatSprint.tick(op);
            assertEquals(SprintGate.FOOD, op.compat().sprintGate());
            instance.setBlock(4, 63, 864, floor);
            CompatSprint.tick(op);
            assertNull(op.compat().sprintGate());

            fp.player.teleport(new Pos(0.5, 64, 864.5)).join();
            CompatSprint.tick(op);
            assertEquals(SprintGate.FOOD, op.compat().sprintGate(), "a cell above the feet");
        } finally {
            fp.player.remove();
        }
    }

    /** The always gate needs no water and no version: every client has both levers. */
    @Test
    void theAlwaysGateHoldsEverywhere() {
        instance.loadChunk(0, 55).join();
        FakePlayer fp = FakePlayer.connect(instance, new Pos(8.5, 64, 880.5), "SprintAlways");
        OptimizedPlayer op = (OptimizedPlayer) fp.player;
        try {
            op.compat().apply(CompatConfig.builder().suppressSprint(SprintGate.FOOD).build(), op);
            fp.sent.clear();
            CompatSprint.tick(op);
            assertEquals(SprintGate.FOOD, op.compat().sprintGate(), "on dry land");
            assertEquals(6, lastFood(fp));

            op.compat().setLegacyClient(true);
            CompatSprint.tick(op);
            assertEquals(SprintGate.FOOD, op.compat().sprintGate(), "1.8 has the food gate too");
            op.compat().setLegacyClient(false);

            op.compat().setNativeFeatures(Set.of(AnimatiumFeature.DISABLE_SWIM_POSE));
            CompatSprint.tick(op);
            assertEquals(SprintGate.FOOD, op.compat().sprintGate(), "the swim pose is not the sprint");
            op.compat().setNativeFeatures(Set.of());

            // no pose implied: a crawl through a gap is not a swim
            op.setPose(EntityPose.SWIMMING);
            assertEquals(EntityPose.SWIMMING, op.getPose());
            op.setPose(EntityPose.STANDING);

            op.setGameMode(GameMode.CREATIVE);
            fp.sent.clear();
            CompatSprint.tick(op);
            assertNull(op.compat().sprintGate(), "mayfly bypasses the food gate anyway");
            assertEquals(20, lastFood(fp));
            op.setGameMode(GameMode.SURVIVAL);

            // both set: the always gate holds the wire, the water gate still owns the pose
            instance.setBlock(8, 64, 880, Block.WATER);
            instance.setBlock(8, 65, 880, Block.WATER);
            op.compat().apply(CompatConfig.builder().suppressSprint(SprintGate.BLINDNESS).suppressSwim(SprintGate.FOOD).build(), op);
            fp.sent.clear();
            CompatSprint.tick(op);
            assertEquals(SprintGate.BLINDNESS, op.compat().sprintGate());
            assertEquals(1, fp.sent(EntityEffectPacket.class).size());
            assertTrue(fp.sent(UpdateHealthPacket.class).isEmpty(), "food untouched");
            op.setPose(EntityPose.SWIMMING);
            assertEquals(EntityPose.STANDING, op.getPose(), "the water gate intercepts the pose");
        } finally {
            fp.player.remove();
        }
    }

    private static int lastFood(FakePlayer fp) {
        var updates = fp.sent(UpdateHealthPacket.class);
        assertTrue(!updates.isEmpty(), "expected an UpdateHealthPacket");
        return updates.getLast().food();
    }
}
