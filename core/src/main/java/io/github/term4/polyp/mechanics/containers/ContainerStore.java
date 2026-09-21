package io.github.term4.polyp.mechanics.containers;

import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** A world's containers: contents by key (only while non-empty), kinds declared by position, open windows. */
final class ContainerStore {

    private static final Tag<ContainerStore> TAG = Tag.Transient("polyp:containers");

    /** An open window: the cells it fronts and the key behind each equal run of its slots. */
    record Window(Inventory inventory, MechanicsWorld world, Block block, List<BlockVec> cells, List<String> keys) {
        int half() { return inventory.getSize() / keys.size(); }
    }

    final Map<String, ItemStack[]> holdings = new ConcurrentHashMap<>();
    /** Keys whose fill has run: an emptied container stays empty. */
    final Set<String> filled = ConcurrentHashMap.newKeySet();
    final Map<BlockVec, ContainerTypeConfig> declared = new ConcurrentHashMap<>();
    final Map<String, Window> windows = new ConcurrentHashMap<>();

    static @NotNull ContainerStore of(@NotNull MechanicsWorld world) {
        ContainerStore store = world.getTag(TAG);
        if (store == null) {
            synchronized (TAG) {
                store = world.getTag(TAG);
                if (store == null) {
                    store = new ContainerStore();
                    world.setTag(TAG, store);
                }
            }
        }
        return store;
    }

    static @Nullable ContainerStore existing(@NotNull MechanicsWorld world) {
        return world.getTag(TAG);
    }

    /** Writes one slot; the entry exists only while something is in it. */
    void put(String key, int slot, int size, ItemStack stack) {
        if (stack.isAir()) {
            ItemStack[] slots = holdings.get(key);
            if (slots == null) return;
            slots[slot] = null;
            for (ItemStack s : slots) if (s != null) return;
            holdings.remove(key);
            return;
        }
        ItemStack[] slots = holdings.computeIfAbsent(key, k -> new ItemStack[size]);
        if (slots.length < size) { // a restore may hand back fewer slots than the block declares
            slots = java.util.Arrays.copyOf(slots, size);
            holdings.put(key, slots);
        }
        slots[slot] = stack;
    }
}
