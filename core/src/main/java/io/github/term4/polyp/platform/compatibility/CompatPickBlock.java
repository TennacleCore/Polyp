package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.Polyp;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.CreativeInventoryActionEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.8's middle-click, decided on the server's items.
 *
 * <p>A 1.8 client sends no pick packet: {@code Minecraft.middleClickMouse} edits its own hotbar and, in creative,
 * reports the slot it wrote. Vanilla's rule ({@code InventoryPlayer.setCurrentItem}) is that a stack already in
 * the HOTBAR only moves the held slot - nothing is written. The client decides that by 1.8 item id and damage,
 * which a translated stack does not always match, so it takes the "not there" branch and overwrites whatever the
 * builder was holding. This runs the same rule again over the server's own materials and refuses that write.
 *
 * <p>Legacy viewers only, and only a single block landing in the held slot - the shape a pick has and a creative
 * drag rarely does.
 */
public final class CompatPickBlock {

    private CompatPickBlock() {}

    public static void install(@NotNull Polyp polyp) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:compat-pick-block", EventFilter.PLAYER);
        node.addListener(CreativeInventoryActionEvent.class, e -> {
            Player player = e.getPlayer();
            if (player.getGameMode() != GameMode.CREATIVE || !polyp.clientInfo().isLegacy(player)) return;
            ItemStack picked = e.getClickedItem();
            if (e.getSlot() != player.getHeldSlot() || picked.amount() != 1 || picked.material().block() == null) {
                return;
            }
            PlayerInventory inventory = player.getInventory();
            if (inventory.getItemStack(e.getSlot()).material() == picked.material()) return;
            for (byte slot = 0; slot < 9; slot++) {
                if (inventory.getItemStack(slot).material() != picked.material()) continue;
                e.setCancelled(true); // the cancel path refreshes the slot the client already overwrote
                player.setHeldItemSlot(slot);
                return;
            }
        });
        polyp.install(node);
    }
}
