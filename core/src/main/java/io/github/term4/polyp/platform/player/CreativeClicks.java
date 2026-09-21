package io.github.term4.polyp.platform.player;

import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The creative-only clicks Minestom leaves as TODOs, as vanilla's {@code Container} runs them: the middle-click
 * clone and the clone drag. Both eras predict them the same way, so the server has to land them the same way.
 */
public final class CreativeClicks {

    private CreativeClicks() {}

    /** Mode 3: an empty cursor takes a full stack of the clicked item. */
    public static boolean clone(@NotNull Player player, @NotNull AbstractInventory clickedInventory, int clickedSlot) {
        if (player.getGameMode() != GameMode.CREATIVE) return false;
        if (!player.getInventory().getCursorItem().isAir()) return false;
        if (clickedSlot < 0 || clickedSlot >= clickedInventory.getSize()) return false;
        ItemStack clicked = clickedInventory.getItemStack(clickedSlot);
        if (clicked.isAir()) return false;
        player.getInventory().setCursorItem(clicked.withAmount(clicked.maxStackSize()));
        return true;
    }

    /**
     * Quick-craft type 2, ended: every dragged slot that is empty or holds the cursor's item fills to a full stack.
     * What landed comes off the cursor, which empties at zero - a clone drag spends the stack it cloned from.
     * Slots at or past {@code window}'s size are the player's, offset by that size. {@code null} = nothing to do.
     */
    public static @Nullable ItemStack cloneDrag(@NotNull Player player, @NotNull AbstractInventory window,
                                                @NotNull List<Integer> slots, @NotNull ItemStack cursor) {
        if (player.getGameMode() != GameMode.CREATIVE || cursor.isAir()) return null;
        int max = cursor.maxStackSize();
        int remaining = cursor.amount();
        for (int slot : slots) {
            boolean inWindow = slot < window.getSize();
            AbstractInventory inv = inWindow ? window : player.getInventory();
            int s = inWindow ? slot : slot - window.getSize();
            if (s < 0 || s >= inv.getSize()) continue;
            ItemStack in = inv.getItemStack(s);
            if (!in.isAir() && !cursor.isSimilar(in)) continue;
            int carry = in.isAir() ? 0 : in.amount();
            if (carry >= max) continue;
            inv.setItemStack(s, cursor.withAmount(max));
            remaining -= max - carry;
        }
        return remaining <= 0 ? ItemStack.AIR : cursor.withAmount(remaining);
    }
}
