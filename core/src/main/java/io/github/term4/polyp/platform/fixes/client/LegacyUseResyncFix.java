package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.platform.fixes.FixToggleConfig;
import io.github.term4.polyp.platform.fixes.FixesSystem;
import io.github.term4.polyp.platform.player.PolypPlayer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.Material;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jetbrains.annotations.NotNull;

/**
 * A client before 1.21 runs the bow's release itself (1.8 {@code ItemBow.onPlayerStoppedUsing}, through 1.20.6's
 * {@code ProjectileWeaponItem.useAmmo}): past a tenth of power it spends an arrow before the server says whether it
 * fired. A shot the server did not take (under its own minimum, refused, arrowless) leaves the client an arrow short,
 * and with none left it cannot draw again, so its arrows are re-sent after every bow release, the offhand's too from
 * 1.9. Never the bow: a hand rewrite ends a 1.8 use, and a quick re-draw has begun by the time the resend lands.
 */
public final class LegacyUseResyncFix {

    private LegacyUseResyncFix() {}

    public static void install(EventNode<@NotNull Event> node, FixesSystem fixes) {
        node.addListener(PlayerCancelItemUseEvent.class, e -> {
            if (e.getItemStack().material() != Material.BOW) return;
            Player player = e.getPlayer();
            if (!(player instanceof PolypPlayer op)) return;
            FixToggleConfig cfg = fixes.forClient(player).legacyUseResync();
            if (cfg == null || !cfg.enabled(player)) return;
            PlayerInventory inventory = player.getInventory();
            for (int slot = 0; slot < PlayerInventory.INNER_INVENTORY_SIZE; slot++) {
                if (arrow(inventory.getItemStack(slot).material())) op.inventorySync().resend(slot);
            }
            // a 1.9 client draws from its offhand first
            if (!op.compat().legacyClient() && arrow(inventory.getItemStack(PlayerInventoryUtils.OFFHAND_SLOT).material())) {
                op.inventorySync().resend(PlayerInventoryUtils.OFFHAND_SLOT);
            }
        });
    }

    private static boolean arrow(Material material) {
        return material == Material.ARROW || material == Material.TIPPED_ARROW || material == Material.SPECTRAL_ARROW;
    }
}
