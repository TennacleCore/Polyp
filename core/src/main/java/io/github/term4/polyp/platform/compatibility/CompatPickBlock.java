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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger(CompatPickBlock.class);
    private static final boolean DEBUG = Boolean.getBoolean("polyp.debug.pick");

    private CompatPickBlock() {}

    public static void install(@NotNull Polyp polyp) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:compat-pick-block", EventFilter.PLAYER);
        node.addListener(PlayerPickBlockEvent.class, event -> {
            Material material = blockItem(event.getBlock());
            if (material != null) pick(event.getPlayer(), ItemStack.of(material));
        });
        // a 1.8 pick arrives as the creative write the client already made. Not gated on the client's version:
        // a modern pick never comes this way, and legacy detection only lands after login
        node.addListener(CreativeInventoryActionEvent.class, event -> legacyReport(event));
        polyp.install(node);
    }

    /**
     * What the client did to itself, undone. It writes into the slot it holds (or an empty one it moved to
     * first) because it failed to recognise its own stack; when the same block IS in the hotbar by the server's
     * reckoning, the write is refused and the selected slot moves there instead - which is all vanilla would
     * have done.
     *
     * <p>Only ever a cancel and a slot change, never a write of our own. The client and the server disagree
     * about this inventory the moment a pick misfires, and a correcting write lands on whichever slot the
     * SERVER thinks is which, destroying whatever was there.
     */
    private static void legacyReport(CreativeInventoryActionEvent event) {
        Player player = event.getPlayer();
        ItemStack wrote = event.getClickedItem();
        int slot = event.getSlot();
        // an emptied slot is the client tidying up after itself, and air matches every empty slot there is
        if (slot < 0 || slot > 8 || wrote.isAir() || wrote.material().block() == null) return;
        PlayerInventory inventory = player.getInventory();
        ItemStack displaced = inventory.getItemStack(slot);
        // a pick lands in the held slot, or in one that was empty; a drag onto an occupied slot is not ours
        if (slot != player.getHeldSlot() && !displaced.isAir()) return;
        // by material, not by components: item identity is what survives the 1.8 round trip
        if (displaced.material() == wrote.material()) return; // it landed where it belongs
        int hotbar = find(inventory, wrote.material(), 0, 9);
        debug(player, hotbar >= 0 ? "snap" : "wrote", slot, wrote, hotbar);
        if (hotbar < 0) return; // nothing to snap to: the client's own write is what vanilla would do too
        event.setCancelled(true);
        player.setHeldItemSlot((byte) hotbar);
        inventory.update(); // the client still believes it wrote a block; put it back in step
    }

    private static int find(PlayerInventory inventory, Material material, int from, int to) {
        for (int slot = from; slot < to; slot++) {
            if (inventory.getItemStack(slot).material() == material) return slot;
        }
        return -1;
    }

    /** {@code -Dpolyp.debug.pick=true}: one line per creative write we looked at, and what we made of it. */
    private static void debug(Player player, String what, int slot, ItemStack wrote, int other) {
        if (!DEBUG) return;
        StringBuilder hotbar = new StringBuilder();
        for (int i = 0; i < 9; i++) {
            ItemStack held = player.getInventory().getItemStack(i);
            hotbar.append(i).append('=').append(held.isAir() ? "-" : held.material().key().value())
                    .append('x').append(held.amount()).append(' ');
        }
        LOGGER.info("pick {} slot={} held={} item={}x{} other={} | {}", what, slot, player.getHeldSlot(),
                wrote.material().key().value(), wrote.amount(), other, hotbar.toString().trim());
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
