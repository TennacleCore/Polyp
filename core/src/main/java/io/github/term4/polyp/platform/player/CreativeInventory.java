package io.github.term4.polyp.platform.player;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.inventory.InventoryType;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A Minestom {@link Inventory} whose creative clicks land ({@link CreativeClicks}), and whose slot changes each
 * {@link OptimizedPlayer} viewer's {@link io.github.term4.polyp.platform.inventory.InventorySync} sends.
 */
public class CreativeInventory extends Inventory {

    public CreativeInventory(@NotNull InventoryType type, @NotNull Component title) {
        super(type, title);
    }

    @Override
    protected void UNSAFE_itemInsert(int slot, ItemStack item, ItemStack previous, boolean sendPacket) {
        super.UNSAFE_itemInsert(slot, item, previous, false);
        if (!sendPacket) return;
        for (Player viewer : getViewers()) {
            if (!(viewer instanceof OptimizedPlayer)) viewer.sendPacket(new SetSlotPacket(getWindowId(), 0, (short) slot, item));
        }
    }

    @Override
    public void sendSlotRefresh(int slot, ItemStack item) {
        for (Player viewer : getViewers()) {
            if (viewer instanceof OptimizedPlayer op) op.inventorySync().refresh(this, slot, item);
            else viewer.sendPacket(new SetSlotPacket(getWindowId(), 0, (short) slot, item));
        }
    }

    @Override
    public void update(Player player) {
        if (player instanceof OptimizedPlayer op) op.inventorySync().resync(this);
        else super.update(player);
    }

    @Override
    public boolean middleClick(Player player, int slot) {
        boolean inWindow = slot < getSize();
        AbstractInventory clicked = inWindow ? this : player.getInventory();
        if (CreativeClicks.clone(player, clicked, inWindow ? slot : slot - getSize())) return true;
        update(player);
        return false;
    }

    @Override
    public boolean dragging(Player player, List<Integer> slots, int button) {
        if (button != 10) return super.dragging(player, slots, button);
        ItemStack cursor = CreativeClicks.cloneDrag(player, this, slots, player.getInventory().getCursorItem());
        if (cursor == null) {
            update(player);
            return false;
        }
        player.getInventory().setCursorItem(cursor);
        return true;
    }
}
