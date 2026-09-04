package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.Polyp;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.CreativeInventoryActionEvent;
import net.minestom.server.event.player.PlayerPickBlockEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;

/**
 * Middle-click, which is the SERVER's to answer: the client only says which block it aimed at, and vanilla's
 * {@code ServerGamePacketListenerImpl.tryPickItem} decides everything after that. The library dispatches the
 * event and stops there, so without this nothing happens at all.
 *
 * <p>Vanilla's rule, kept exactly: a matching stack already in the hotbar only moves the selected slot; one
 * deeper in the inventory is swapped into the hotbar; nothing anywhere is created only for a player with
 * infinite materials. The slot it lands in walks forward from the selected one, taking the first empty, else
 * the first unenchanted, else the selected one.
 *
 * <p>A 1.8 client has no pick packet: it decides inside its own inventory and only reports the creative slot it
 * wrote. It decides by 1.8 item id and damage, which a stack that came down the Via item chain does not always
 * match, so it misses its own hotbar and overwrites the held slot. The second listener reads that report and
 * applies the same rule the modern path does, which is the outcome the player expected either way.
 */
public final class CompatPickBlock {

    private CompatPickBlock() {}

    public static void install(@NotNull Polyp polyp) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:compat-pick-block", EventFilter.PLAYER);
        node.addListener(PlayerPickBlockEvent.class, event -> {
            Material material = blockItem(event.getBlock());
            if (material != null) pick(event.getPlayer(), ItemStack.of(material));
        });
        // a 1.8 pick arrives as the creative write it already made: one block, into a hotbar slot. Not gated on
        // the client's version - a modern pick never comes this way, and legacy detection lands after login
        node.addListener(CreativeInventoryActionEvent.class, event -> {
            Player player = event.getPlayer();
            ItemStack wrote = event.getClickedItem();
            int slot = event.getSlot();
            if (slot < 0 || slot > 8 || wrote.amount() != 1 || wrote.material().block() == null) return;
            PlayerInventory inventory = player.getInventory();
            // by material, not by components: item identity is what survives the 1.8 round trip
            if (inventory.getItemStack(slot).material() == wrote.material()) return; // it landed where it belongs
            for (byte held = 0; held < 9; held++) {
                if (inventory.getItemStack(held).material() != wrote.material()) continue;
                event.setCancelled(true); // the library refreshes the slot the client wrote behind our back
                player.setHeldItemSlot(held);
                return;
            }
        });
        polyp.install(node);
    }

    // getCloneItemStack, as far as the block registry carries it - block entity data is not modelled here
    private static Material blockItem(Block block) {
        Material material = block.material();
        return material == null || material == Material.AIR ? null : material;
    }

    private static void pick(Player player, ItemStack picked) {
        PlayerInventory inventory = player.getInventory();
        int found = -1;
        for (int slot = 0; slot < 36 && found < 0; slot++) {
            ItemStack held = inventory.getItemStack(slot);
            if (!held.isAir() && held.isSimilar(picked)) found = slot;
        }
        if (found >= 0 && found < 9) {
            player.setHeldItemSlot((byte) found);
        } else if (found >= 0) {
            byte into = suitableHotbarSlot(player, inventory);
            ItemStack displaced = inventory.getItemStack(into);
            inventory.setItemStack(into, inventory.getItemStack(found));
            inventory.setItemStack(found, displaced);
        } else if (player.getGameMode() == GameMode.CREATIVE) {
            byte into = suitableHotbarSlot(player, inventory);
            ItemStack displaced = inventory.getItemStack(into);
            if (!displaced.isAir()) {
                int free = freeSlot(inventory);
                if (free != -1) inventory.setItemStack(free, displaced);
            }
            inventory.setItemStack(into, picked);
        }
    }

    /** Walks forward from the selected slot: the first empty, else the first unenchanted, else the selected. */
    private static byte suitableHotbarSlot(Player player, PlayerInventory inventory) {
        byte selected = player.getHeldSlot();
        for (int step = 0; step < 9; step++) {
            byte slot = (byte) ((selected + step) % 9);
            if (inventory.getItemStack(slot).isAir()) {
                player.setHeldItemSlot(slot);
                return slot;
            }
        }
        for (int step = 0; step < 9; step++) {
            byte slot = (byte) ((selected + step) % 9);
            if (inventory.getItemStack(slot).get(DataComponents.ENCHANTMENTS) == null) {
                player.setHeldItemSlot(slot);
                return slot;
            }
        }
        return selected;
    }

    private static int freeSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItemStack(slot).isAir()) return slot;
        }
        return -1;
    }
}
