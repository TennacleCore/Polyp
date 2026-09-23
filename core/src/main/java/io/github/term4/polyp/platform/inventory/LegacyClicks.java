package io.github.term4.polyp.platform.inventory;

import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A 1.8 client's own click logic, ported from MCP-919 ({@code Container.slotClick}, {@code ContainerPlayer},
 * {@code ContainerChest}, {@code InventoryPlayer}): run on what the client shows, it yields what the client shows next.
 */
final class LegacyClicks {

    /** Window 0 (crafting result, grid, armor, main, hotbar; no offhand), or a chest of {@code chestSlots} and the player's 36. */
    record Layout(boolean playerWindow, int chestSlots) {

        static final Layout PLAYER = new Layout(true, 0);

        static Layout chest(int chestSlots) {
            return new Layout(false, chestSlots);
        }

        int size() {
            return playerWindow ? 45 : chestSlots + 36;
        }

        boolean playerSlot(int slot) {
            return playerWindow ? slot >= 5 && slot < 45 : slot >= chestSlots;
        }

        /** The window slot of {@code InventoryPlayer.mainInventory[index]}: 0-8 hotbar, 9-35 main. */
        int inventorySlot(int index) {
            int main = playerWindow ? 9 : chestSlots;
            return index < 9 ? main + 27 + index : main + index - 9;
        }
    }

    private int dragEvent;
    private int dragMode;
    private final Set<Integer> dragSlots = new LinkedHashSet<>();
    private final List<ItemStack> dropped = new ArrayList<>();

    private Layout layout;
    private ItemStack[] slots;
    private ItemStack cursor;
    private boolean creative;

    void resetDrag() {
        dragEvent = 0;
        dragSlots.clear();
    }

    /** What the last click threw out of the window: the cursor clicked outside, or a slot's throw. */
    @NotNull List<ItemStack> dropped() {
        return List.copyOf(dropped);
    }

    /**
     * Applies one click to {@code slots} (window order) and returns the cursor after it, or {@code null} when the
     * outcome is not modelled: the crafting grid resolves recipes on the client.
     */
    @Nullable ItemStack click(@NotNull Layout layout, ItemStack @NotNull [] slots, @NotNull ItemStack cursor,
                              int slot, int button, int mode, boolean creative) {
        // a drag over the grid still has to be followed: the grid check below catches it when it lands
        if (layout.playerWindow() && slot >= 0 && slot < 5 && mode != 5) return null;
        ItemStack[] grid = layout.playerWindow() ? Arrays.copyOf(slots, 5) : null;
        this.layout = layout;
        this.slots = slots;
        this.cursor = cursor;
        this.creative = creative;
        dropped.clear();
        slotClick(slot, button, mode);
        if (grid != null) {
            for (int i = 0; i < 5; i++) if (!grid[i].equals(slots[i])) return null;
        }
        return this.cursor;
    }

    private void slotClick(int slot, int button, int mode) {
        if (mode == 5) {
            drag(slot, button);
        } else if (dragEvent != 0) {
            resetDrag(); // any other click cancels a drag and does nothing else
        } else if ((mode == 0 || mode == 1) && (button == 0 || button == 1)) {
            if (slot == -999) {
                if (cursor.isAir()) return;
                int out = button == 0 ? cursor.amount() : 1;
                dropped.add(cursor.withAmount(out));
                cursor = shrink(cursor, out);
            } else if (valid(slot)) {
                if (mode == 1) shift(slot, button);
                else pickup(slot, button);
            }
        } else if (mode == 2 && button >= 0 && button < 9) {
            if (valid(slot)) swap(slot, button);
        } else if (mode == 3 && creative && cursor.isAir() && valid(slot)) {
            if (!slots[slot].isAir()) cursor = slots[slot].withAmount(slots[slot].maxStackSize());
        } else if (mode == 4 && cursor.isAir() && valid(slot)) {
            ItemStack in = slots[slot];
            if (in.isAir()) return;
            int out = button == 0 ? 1 : in.amount();
            dropped.add(in.withAmount(out));
            slots[slot] = shrink(in, out);
        } else if (mode == 6 && valid(slot)) {
            gather(slot, button);
        }
    }

    private boolean valid(int slot) {
        return slot >= 0 && slot < layout.size();
    }

    private void pickup(int slot, int button) {
        ItemStack in = slots[slot];
        if (in.isAir()) {
            if (cursor.isAir() || !accepts(slot, cursor)) return;
            int put = Math.min(button == 0 ? cursor.amount() : 1, limit(slot));
            if (cursor.amount() >= put) {
                slots[slot] = cursor.withAmount(put);
                cursor = shrink(cursor, put);
            }
        } else if (cursor.isAir()) {
            int take = button == 0 ? in.amount() : (in.amount() + 1) / 2;
            cursor = in.withAmount(take);
            slots[slot] = shrink(in, take);
        } else if (accepts(slot, cursor)) {
            if (in.isSimilar(cursor)) {
                int put = Math.min(button == 0 ? cursor.amount() : 1, limit(slot) - in.amount());
                put = Math.min(put, cursor.maxStackSize() - in.amount());
                if (put <= 0) return;
                cursor = shrink(cursor, put);
                slots[slot] = in.withAmount(in.amount() + put);
            } else if (cursor.amount() <= limit(slot)) {
                slots[slot] = cursor;
                cursor = in;
            }
        } else if (cursor.maxStackSize() > 1 && in.isSimilar(cursor) && in.amount() + cursor.amount() <= cursor.maxStackSize()) {
            // a slot that refuses the cursor still hands a matching stack up to it
            cursor = cursor.withAmount(cursor.amount() + in.amount());
            slots[slot] = ItemStack.AIR;
        }
    }

    /** Shift-click, repeated while the slot keeps the same item and something moved: 1.8's retrySlotClick. */
    private void shift(int slot, int button) {
        while (true) {
            ItemStack original = slots[slot];
            if (original.isAir() || !transfer(slot)) return;
            ItemStack left = slots[slot];
            if (left.isAir() || left.material() != original.material()) return;
        }
    }

    private boolean transfer(int index) {
        if (!layout.playerWindow()) {
            int chest = layout.chestSlots();
            return index < chest ? merge(index, chest, layout.size(), true) : merge(index, 0, chest, false);
        }
        int armor = armorType(slots[index]);
        if (index < 9) return merge(index, 9, 45, false);
        if (armor >= 0 && slots[5 + armor].isAir()) return merge(index, 5 + armor, 6 + armor, false);
        if (index < 36) return merge(index, 36, 45, false);
        return merge(index, 9, 36, false);
    }

    /** 1.8's mergeItemStack: tops up matching stacks, then drops the whole rest into the first empty slot. */
    private boolean merge(int from, int start, int end, boolean reverse) {
        ItemStack stack = slots[from];
        boolean moved = false;
        int step = reverse ? -1 : 1;
        if (stackable(stack)) {
            for (int i = reverse ? end - 1 : start; !stack.isAir() && i >= start && i < end; i += step) {
                ItemStack in = slots[i];
                if (in.isAir() || !in.isSimilar(stack)) continue;
                int max = stack.maxStackSize();
                int total = in.amount() + stack.amount();
                if (total <= max) {
                    slots[i] = in.withAmount(total);
                    stack = ItemStack.AIR;
                    moved = true;
                } else if (in.amount() < max) {
                    stack = stack.withAmount(total - max);
                    slots[i] = in.withAmount(max);
                    moved = true;
                }
            }
        }
        if (!stack.isAir()) {
            for (int i = reverse ? end - 1 : start; i >= start && i < end; i += step) {
                if (!slots[i].isAir()) continue;
                slots[i] = stack;
                stack = ItemStack.AIR;
                moved = true;
                break;
            }
        }
        slots[from] = stack;
        return moved;
    }

    private void swap(int slot, int button) {
        int hotbar = layout.inventorySlot(button);
        ItemStack held = slots[hotbar];
        ItemStack in = slots[slot];
        boolean own = layout.playerSlot(slot);
        boolean fits = held.isAir() || own && accepts(slot, held);
        boolean room = false;
        if (!fits) {
            room = firstEmpty() >= 0;
            fits = room;
        }
        if (!in.isAir() && fits) {
            slots[hotbar] = in;
            if ((!own || !accepts(slot, held)) && !held.isAir()) {
                if (room) {
                    addToInventory(held);
                    slots[slot] = ItemStack.AIR;
                }
            } else {
                slots[slot] = held;
            }
        } else if (in.isAir() && !held.isAir() && accepts(slot, held)) {
            slots[hotbar] = ItemStack.AIR;
            slots[slot] = held;
        }
    }

    /** Double click: two passes pulling matching stacks onto the cursor, partial stacks first. */
    private void gather(int slot, int button) {
        if (cursor.isAir() || !slots[slot].isAir()) return;
        int step = button == 0 ? 1 : -1;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = button == 0 ? 0 : layout.size() - 1;
                 i >= 0 && i < layout.size() && cursor.amount() < cursor.maxStackSize(); i += step) {
                ItemStack in = slots[i];
                if (in.isAir() || !addable(in, cursor) || layout.playerWindow() && i == 0) continue;
                if (pass == 0 && in.amount() == in.maxStackSize()) continue;
                int take = Math.min(cursor.maxStackSize() - cursor.amount(), in.amount());
                slots[i] = shrink(in, take);
                cursor = cursor.withAmount(cursor.amount() + take);
            }
        }
    }

    private void drag(int slot, int button) {
        int previous = dragEvent;
        dragEvent = button & 3;
        if ((previous != 1 || dragEvent != 2) && previous != dragEvent) {
            resetDrag();
        } else if (cursor.isAir()) {
            resetDrag();
        } else if (dragEvent == 0) {
            dragMode = button >> 2 & 3;
            if (dragMode == 0 || dragMode == 1 || dragMode == 2 && creative) {
                dragEvent = 1;
                dragSlots.clear();
            } else {
                resetDrag();
            }
        } else if (dragEvent == 1) {
            if (valid(slot) && addable(slots[slot], cursor) && accepts(slot, cursor) && cursor.amount() > dragSlots.size()) {
                dragSlots.add(slot);
            }
        } else if (dragEvent == 2) {
            if (!dragSlots.isEmpty()) {
                int left = cursor.amount();
                for (int s : dragSlots) {
                    ItemStack in = slots[s];
                    if (!addable(in, cursor) || !accepts(s, cursor) || cursor.amount() < dragSlots.size()) continue;
                    int had = in.isAir() ? 0 : in.amount();
                    int size = had + switch (dragMode) {
                        case 0 -> cursor.amount() / dragSlots.size();
                        case 1 -> 1;
                        default -> cursor.maxStackSize();
                    };
                    size = Math.min(Math.min(size, cursor.maxStackSize()), limit(s));
                    left -= size - had;
                    slots[s] = cursor.withAmount(size);
                }
                cursor = shrink(cursor, cursor.amount() - left);
            }
            resetDrag();
        } else {
            resetDrag();
        }
    }

    // ------------------------------------------------------------------ InventoryPlayer

    private int firstEmpty() {
        for (int i = 0; i < 36; i++) if (slots[layout.inventorySlot(i)].isAir()) return i;
        return -1;
    }

    /** addItemStackToInventory: a damaged item takes the first empty slot, anything else tops up matching stacks first. */
    private void addToInventory(ItemStack item) {
        if (damaged(item)) {
            int empty = firstEmpty();
            if (empty >= 0) slots[layout.inventorySlot(empty)] = item;
            return;
        }
        int left = item.amount();
        while (left > 0) {
            int before = left;
            int target = stackWith(item);
            if (target < 0) target = firstEmpty();
            if (target < 0) return;
            int slot = layout.inventorySlot(target);
            ItemStack in = slots[slot];
            int had = in.isAir() ? 0 : in.amount();
            int put = Math.min(left, Math.min(item.maxStackSize() - had, 64 - had));
            if (put <= 0) return;
            slots[slot] = item.withAmount(had + put);
            left -= put;
            if (left >= before) return;
        }
    }

    private int stackWith(ItemStack item) {
        for (int i = 0; i < 36; i++) {
            ItemStack in = slots[layout.inventorySlot(i)];
            if (!in.isAir() && in.isSimilar(item) && stackable(in) && in.amount() < in.maxStackSize() && in.amount() < 64) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ slots and stacks

    private boolean accepts(int slot, ItemStack item) {
        if (!layout.playerWindow()) return true;
        if (slot == 0) return false;
        if (slot >= 5 && slot < 9) {
            int type = slot - 5;
            return armorType(item) == type || type == 0 && wornOnHead(item);
        }
        return true;
    }

    private int limit(int slot) {
        return layout.playerWindow() && slot >= 5 && slot < 9 ? 1 : 64;
    }

    /** canAddItemToSlot with stack size mattering: an empty slot, or a matching stack not over its max. */
    private static boolean addable(ItemStack in, ItemStack stack) {
        return in.isAir() || in.isSimilar(stack) && in.amount() <= stack.maxStackSize();
    }

    private static boolean stackable(ItemStack item) {
        return item.maxStackSize() > 1 && !damaged(item);
    }

    private static boolean damaged(ItemStack item) {
        Integer damage = item.get(DataComponents.DAMAGE);
        return damage != null && damage > 0;
    }

    /** 1.8's ItemArmor.armorType: 0 helmet to 3 boots; a pumpkin or skull is worn but is no ItemArmor. */
    private static int armorType(ItemStack item) {
        String key = item.material().key().value();
        if (key.endsWith("_helmet")) return 0;
        if (key.endsWith("_chestplate")) return 1;
        if (key.endsWith("_leggings")) return 2;
        if (key.endsWith("_boots")) return 3;
        return -1;
    }

    private static boolean wornOnHead(ItemStack item) {
        Material material = item.material();
        String key = material.key().value();
        return material == Material.PUMPKIN || material == Material.CARVED_PUMPKIN
                || key.endsWith("_skull") || key.endsWith("_head");
    }

    private static ItemStack shrink(ItemStack item, int by) {
        int left = item.amount() - by;
        return left <= 0 ? ItemStack.AIR : item.withAmount(left);
    }
}
