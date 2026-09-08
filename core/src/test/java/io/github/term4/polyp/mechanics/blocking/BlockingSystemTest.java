package io.github.term4.polyp.mechanics.blocking;

import io.github.term4.polyp.mechanics.blocking.catalog.VanillaBlocking;
import io.github.term4.polyp.presets.vanilla18.Blocking;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What blocks is the scope config plus the per-item opt-out - NOT the {@code blocks_attacks} component. A 1.8 profile
 * marks every sword blockable and nothing stamps them, so requiring the component meant swords never actually blocked.
 */
class BlockingSystemTest extends HeadlessServerTest {

    private static BlockingSystem blocking;
    private static FakePlayer player;

    @BeforeAll
    static void installBlocking() {
        blocking = BlockingSystem.install(polyp, Blocking.config());
        player = FakePlayer.connect(instance, new Pos(70.5, 64, 70.5), "Blocker");
    }

    /**
     * The block a landed hit carries. A second swing inside the i-frame window is reduced all the same, but only
     * its excess over the stored high-water lands - and a hit that lands nothing fires no applied event, so
     * nothing downstream can mistake it for a block that mattered.
     */
    @Test
    void aBlockIsCarriedByTheHitItLands() {
        FakePlayer victim = FakePlayer.connect(instance, new Pos(74.5, 64, 74.5), "BlockCue");
        net.minestom.server.entity.LivingEntity attacker = zombie(new Pos(75.5, 64, 74.5));
        java.util.List<io.github.term4.polyp.api.event.damage.DamageAppliedEvent> landed = new java.util.ArrayList<>();
        var node = net.minestom.server.event.EventNode.all("test:block-cue");
        node.addListener(io.github.term4.polyp.api.event.damage.DamageAppliedEvent.class, e -> {
            if (e.target() == victim.player) landed.add(e);
        });
        net.minestom.server.MinecraftServer.getGlobalEventHandler().addChild(node);
        try {
            ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD);
            victim.player.setItemInHand(PlayerHand.MAIN, sword);
            victim.player.refreshItemUse(PlayerHand.MAIN, 72000); // sword up, the 1.8 way
            victim.player.setHealth(20f);
            assertTrue(blocking.isBlocking(victim.player), "blocking before the first swing");

            services.damage().apply(io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage.INSTANCE
                    .snapshot(attacker, victim.player, false, sword, services));
            assertEquals(1, landed.size(), "the first swing landed");
            assertTrue(landed.getFirst().blocked(), "and the block took something off it");

            // the same swing again, inside the window: reduced to the same figure, so nothing is left to land
            landed.clear();
            services.damage().apply(io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage.INSTANCE
                    .snapshot(attacker, victim.player, false, sword, services));
            assertEquals(java.util.List.of(), landed, "no damage dealt, so nothing was halved");
        } finally {
            net.minestom.server.MinecraftServer.getGlobalEventHandler().removeChild(node);
            victim.player.clearItemUse();
            victim.player.remove();
            attacker.remove();
        }
    }

    /** A raise starts from the config alone: Minestom grants a use time only to a component item, so we grant it. */
    @Test
    void plainSwordBlocksWhenTheProfileMarksItBlockable() {
        ItemStack sword = ItemStack.of(Material.DIAMOND_SWORD); // no blocks_attacks - what an app's kit hands out
        player.player.clearItemUse();

        var use = new PlayerUseItemEvent(player.player, PlayerHand.MAIN, sword, 0);
        EventDispatcher.call(use);

        assertTrue(use.getItemUseTime() > 0, "the config alone starts the block");
        player.player.setItemInHand(PlayerHand.MAIN, sword);
        player.player.refreshItemUse(PlayerHand.MAIN, use.getItemUseTime());
        assertTrue(blocking.isBlocking(player.player), "and the server counts it as blocking");
        player.player.clearItemUse();
    }

    @Test
    void optedOutSwordNeverBlocks() {
        ItemStack sword = VanillaBlocking.nonBlocking(ItemStack.of(Material.DIAMOND_SWORD));
        player.player.clearItemUse();

        var use = new PlayerUseItemEvent(player.player, PlayerHand.MAIN, sword, 0);
        EventDispatcher.call(use);

        assertEquals(0, use.getItemUseTime(), "an opted-out sword never raises");
        player.player.setItemInHand(PlayerHand.MAIN, sword);
        player.player.refreshItemUse(PlayerHand.MAIN, 72000); // even if something else started a use
        assertFalse(blocking.isBlocking(player.player), "and never counts as blocking");
        player.player.clearItemUse();
    }

    /** A non-blockable material is left alone - the system must not hijack every right-click. */
    @Test
    void unconfiguredMaterialIsUntouched() {
        player.player.clearItemUse();
        var use = new PlayerUseItemEvent(player.player, PlayerHand.MAIN, ItemStack.of(Material.STONE), 0);
        EventDispatcher.call(use);
        assertEquals(0, use.getItemUseTime(), "stone is not blockable, so no use time is granted");
    }
}
