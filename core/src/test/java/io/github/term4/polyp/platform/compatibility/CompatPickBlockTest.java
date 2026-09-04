package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerPickBlockEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Vanilla's {@code tryPickItem}: snap to a hotbar match, swap up an inventory match, create one in creative. */
class CompatPickBlockTest extends HeadlessServerTest {

    private static Player builder(String name, GameMode mode, byte held) {
        Player player = FakePlayer.connect(instance, new Pos(0.5, 65, 900.5), name).player;
        player.setGameMode(mode);
        player.setHeldItemSlot(held);
        return player;
    }

    private static void middleClick(Player player, Block block) {
        EventDispatcher.call(new PlayerPickBlockEvent(player, player.getInstance(), block,
                new BlockVec(0, 64, 0), false));
    }

    @Test
    void aHotbarMatchOnlyMovesTheSelectedSlot() {
        Player player = builder("PickHotbar", GameMode.SURVIVAL, (byte) 0);
        try {
            player.getInventory().setItemStack(0, ItemStack.of(Material.DIAMOND_SWORD));
            player.getInventory().setItemStack(4, ItemStack.of(Material.STONE, 64));

            middleClick(player, Block.STONE);

            assertEquals(4, player.getHeldSlot(), "snap to it");
            assertEquals(Material.STONE, player.getInventory().getItemStack(4).material());
            assertEquals(Material.DIAMOND_SWORD, player.getInventory().getItemStack(0).material(),
                    "nothing is written anywhere");
        } finally {
            player.remove();
        }
    }

    @Test
    void anInventoryMatchIsSwappedIntoTheHotbar() {
        Player player = builder("PickDeep", GameMode.SURVIVAL, (byte) 0);
        try {
            player.getInventory().setItemStack(0, ItemStack.of(Material.DIAMOND_SWORD));
            player.getInventory().setItemStack(20, ItemStack.of(Material.STONE, 64));

            middleClick(player, Block.STONE);

            assertEquals(Material.STONE, player.getInventory().getItemStack(player.getHeldSlot()).material());
            assertTrue(player.getInventory().getItemStack(20).isAir(), "it moved, it was not copied");
            assertEquals(Material.DIAMOND_SWORD, player.getInventory().getItemStack(0).material(),
                    "the first EMPTY hotbar slot takes it, so the sword is untouched");
        } finally {
            player.remove();
        }
    }

    @Test
    void survivalCreatesNothingAndCreativeDoes() {
        Player survivor = builder("PickNone", GameMode.SURVIVAL, (byte) 0);
        try {
            middleClick(survivor, Block.STONE);
            assertTrue(survivor.getInventory().getItemStack(0).isAir(), "no infinite materials, no block");
        } finally {
            survivor.remove();
        }

        Player creative = builder("PickMake", GameMode.CREATIVE, (byte) 0);
        try {
            middleClick(creative, Block.STONE);
            assertEquals(Material.STONE, creative.getInventory().getItemStack(creative.getHeldSlot()).material());
        } finally {
            creative.remove();
        }
    }

    @Test
    void aFullHotbarPushesTheDisplacedStackIntoTheInventory() {
        Player player = builder("PickFull", GameMode.CREATIVE, (byte) 3);
        try {
            for (byte slot = 0; slot < 9; slot++) {
                player.getInventory().setItemStack(slot, ItemStack.of(Material.DIRT, 1));
            }
            middleClick(player, Block.STONE);

            assertEquals(3, player.getHeldSlot(), "nothing empty and nothing enchanted: the selected slot");
            assertEquals(Material.STONE, player.getInventory().getItemStack(3).material());
            assertEquals(Material.DIRT, player.getInventory().getItemStack(9).material(),
                    "what it displaced went to the first free slot");
        } finally {
            player.remove();
        }
    }
}
