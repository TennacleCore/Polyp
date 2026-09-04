package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.inventory.CreativeInventoryActionEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.8's middle-click reaches the server as a creative write into the held slot. Vanilla only writes when the
 * block is nowhere in the hotbar; a 1.8 client that cannot recognise a translated stack writes anyway.
 */
class CompatPickBlockTest extends HeadlessServerTest {

    private static Player legacyBuilder(String name, byte held) {
        Player builder = FakePlayer.connect(instance, new Pos(0.5, 65, 900.5), name).player;
        builder.setGameMode(GameMode.CREATIVE);
        builder.setHeldItemSlot(held);
        polyp.client(builder).protocol(47);
        return builder;
    }

    private static boolean write(Player builder, int slot, ItemStack item) {
        var e = new CreativeInventoryActionEvent(builder, slot, item);
        EventDispatcher.call(e);
        return !e.isCancelled();
    }

    @Test
    void aBlockAlreadyInTheHotbarSwitchesInsteadOfOverwriting() {
        Player builder = legacyBuilder("Picker18", (byte) 0);
        try {
            builder.getInventory().setItemStack(0, ItemStack.of(Material.DIAMOND_SWORD));
            builder.getInventory().setItemStack(4, ItemStack.of(Material.STONE, 64));

            assertFalse(write(builder, 0, ItemStack.of(Material.STONE)), "the write is refused");
            assertEquals(4, builder.getHeldSlot(), "the held slot moves to the stone instead");
            assertEquals(Material.DIAMOND_SWORD, builder.getInventory().getItemStack(0).material(),
                    "and what they were holding is still there");
        } finally {
            builder.remove();
        }
    }

    @Test
    void aBlockNowhereInTheHotbarIsWrittenAsVanillaWould() {
        Player builder = legacyBuilder("Picker18Fresh", (byte) 2);
        try {
            assertTrue(write(builder, 2, ItemStack.of(Material.STONE)), "nothing to switch to: the write stands");
            assertEquals(2, builder.getHeldSlot());
        } finally {
            builder.remove();
        }
    }

    @Test
    void aModernClientAndAStackedDragAreLeftAlone() {
        Player modern = FakePlayer.connect(instance, new Pos(0.5, 65, 902.5), "PickerModern").player;
        modern.setGameMode(GameMode.CREATIVE);
        modern.setHeldItemSlot((byte) 0);
        try {
            modern.getInventory().setItemStack(4, ItemStack.of(Material.STONE, 64));
            assertTrue(write(modern, 0, ItemStack.of(Material.STONE)), "modern clients pick for themselves");
            assertEquals(0, modern.getHeldSlot());
        } finally {
            modern.remove();
        }

        Player builder = legacyBuilder("Picker18Drag", (byte) 0);
        try {
            builder.getInventory().setItemStack(4, ItemStack.of(Material.STONE, 64));
            assertTrue(write(builder, 0, ItemStack.of(Material.STONE, 64)), "a whole stack is a drag, not a pick");
            assertEquals(0, builder.getHeldSlot());
        } finally {
            builder.remove();
        }
    }
}
