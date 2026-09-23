package io.github.term4.polyp.mechanics.damage;

import io.github.term4.polyp.item.Enchants;
import io.github.term4.polyp.mechanics.containers.Spill;
import io.github.term4.polyp.mechanics.damage.DeathConfig.DeathContext;
import io.github.term4.polyp.platform.inventory.PlaceBack;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.enchant.EffectComponent;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** A killed player's inventory: dropped, kept or gone, per {@link DeathConfig#spill}. */
final class DeathDrops {

    /** 26.1's order: the inventory, the offhand, then the armor from the feet up; 1.8's is the same less the offhand. */
    private static final int[] SLOTS = order();

    private DeathDrops() {}

    static void spill(Player dead, @Nullable DeathConfig death, DeathContext ctx) {
        if (dead.getGameMode() == GameMode.SPECTATOR) return;
        PlayerInventory inventory = dead.getInventory();
        // the client's screen went with the death, so its cursor goes the inventory's way
        ItemStack carried = inventory.getCursorItem();
        inventory.setCursorItem(ItemStack.AIR);
        if (dead.getOpenInventory() != null) dead.closeInventory();
        Spill spill = death != null ? death.spill(ctx) : null;
        if (spill == Spill.KEEP) {
            inventory.setCursorItem(PlaceBack.into(dead, carried));
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        for (int slot : SLOTS) {
            ItemStack stack = inventory.getItemStack(slot);
            if (stack.isAir()) continue;
            inventory.setItemStack(slot, ItemStack.AIR);
            drops.add(stack);
        }
        drops.add(carried);
        if (spill == Spill.VANISH) return;
        if (death == null || !Boolean.FALSE.equals(death.vanishingCurse(ctx))) {
            drops.removeIf(stack -> Enchants.has(stack, EffectComponent.PREVENT_EQUIPMENT_DROP));
        }
        Spill.Throw how = death != null ? death.dropThrow(ctx) : null;
        Spill.throwFrom(dead, drops, how != null ? how : Spill.Throw.PLAYER);
    }

    private static int[] order() {
        int inner = PlayerInventory.INNER_INVENTORY_SIZE;
        int[] slots = new int[inner + 5];
        for (int slot = 0; slot < inner; slot++) slots[slot] = slot;
        slots[inner] = PlayerInventoryUtils.OFFHAND_SLOT;
        slots[inner + 1] = PlayerInventoryUtils.BOOTS_SLOT;
        slots[inner + 2] = PlayerInventoryUtils.LEGGINGS_SLOT;
        slots[inner + 3] = PlayerInventoryUtils.CHESTPLATE_SLOT;
        slots[inner + 4] = PlayerInventoryUtils.HELMET_SLOT;
        return slots;
    }
}
