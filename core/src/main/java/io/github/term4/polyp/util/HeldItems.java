package io.github.term4.polyp.util;

import io.github.term4.polyp.platform.player.PolypPlayer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.item.ItemStack;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;

/** Held-item helpers shared by the use-item paths. */
public final class HeldItems {

    private HeldItems() {}

    /** Shrinks the held stack by one outside creative (the vanilla use-consume). */
    public static void consumeOne(Player player, PlayerHand hand) {
        if (player.getGameMode() == GameMode.CREATIVE) return;
        ItemStack held = player.getItemInHand(hand);
        player.setItemInHand(hand, held.withAmount(held.amount() - 1));
    }

    /**
     * Sends the hand's stack again: every client spends a throwable itself (1.8 {@code ItemEgg.onItemRightClick},
     * 26.1 {@code ItemStack.consume}) before the server answers, so a use the server refused leaves it one short.
     */
    public static void resend(Player player, PlayerHand hand) {
        int slot = hand == PlayerHand.OFF ? PlayerInventoryUtils.OFFHAND_SLOT : player.getHeldSlot();
        if (player instanceof PolypPlayer op) op.inventorySync().resend(slot);
        else player.getInventory().update();
    }
}
