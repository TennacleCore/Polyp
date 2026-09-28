package io.github.term4.polyp.vri;

import io.github.term4.polyp.vri.BlockDrops.DropContext;
import io.github.term4.polyp.vri.BlockLoot.Any;
import io.github.term4.polyp.vri.BlockLoot.Cond;
import io.github.term4.polyp.vri.BlockLoot.Entry;
import io.github.term4.polyp.vri.BlockLoot.Fixed;
import io.github.term4.polyp.vri.BlockLoot.Item;
import io.github.term4.polyp.vri.BlockLoot.Pool;
import io.github.term4.polyp.vri.BlockLoot.Shears;
import io.github.term4.polyp.vri.BlockLoot.Silk;
import io.github.term4.polyp.vri.BlockLoot.Table;
import io.github.term4.polyp.vri.BlockLoot.Uniform;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where 1.8 dropped something else than today's tables (MCP-919 {@code Block} subclasses): lapis 4 to 8, not 4 to
 * 9; iron and gold ore the ore, not the raw metal; leaves no sticks; a dead bush nothing bare-handed; two-tall
 * grass and ferns no seeds; snow layers {@code layers + 1} snowballs whatever the tool. Ice's water left below is
 * not carried over.
 */
final class LegacyLoot {

    private static final Map<Key, Table> TABLES = build();

    private LegacyLoot() {}

    static @Nullable List<ItemStack> drops(@NotNull DropContext ctx) {
        Table table = TABLES.get(ctx.block().key());
        return table == null ? null : BlockLoot.drops(table, ctx);
    }

    private static Map<Key, Table> build() {
        Map<Key, Table> out = new HashMap<>();
        out.put(key("lapis_ore"), silkOr("lapis_ore", new Item(key("lapis_lazuli"), List.of(), new Uniform(4, 8), new BlockLoot.OreDrops(), null)));
        out.put(key("iron_ore"), self("iron_ore"));
        out.put(key("gold_ore"), self("gold_ore"));
        out.put(key("dead_bush"), shearsOnly("dead_bush"));
        for (String grass : List.of("tall_grass", "large_fern")) {
            String cut = grass.equals("tall_grass") ? "short_grass" : "fern";
            out.put(key(grass), new Table(List.of(new Pool(List.of(new BlockLoot.State(Map.of("half", "lower"))),
                    List.of(new Item(key(cut), List.of(new Shears()), new Fixed(2), null, null))))));
        }
        for (String leaves : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak")) {
            Table modern = BlockLoot.of(key(leaves + "_leaves"));
            if (modern != null) out.put(key(leaves + "_leaves"), without(modern, key("stick")));
        }
        Map<String, BlockLoot.Count> layers = new HashMap<>();
        for (int i = 1; i <= 8; i++) layers.put(String.valueOf(i), new Fixed(i + 1));
        out.put(key("snow"), new Table(List.of(new Pool(List.of(), List.of(
                new Item(key("snowball"), List.of(), new BlockLoot.ByState("layers", layers, new Fixed(2)), null, null))))));
        return Map.copyOf(out);
    }

    private static Key key(String name) {
        return Key.key("minecraft:" + name);
    }

    private static Table self(String name) {
        return new Table(List.of(new Pool(List.of(), List.of(new Item(key(name), List.of(), new Fixed(1), null, null)))));
    }

    private static Table silkOr(String silk, Item otherwise) {
        return new Table(List.of(new Pool(List.of(), List.of(new Any(List.of(
                new Item(key(silk), List.of(new Silk()), new Fixed(1), null, null), otherwise), List.of())))));
    }

    private static Table shearsOnly(String name) {
        return new Table(List.of(new Pool(List.of(), List.of(new Item(key(name), List.of(new Shears()), new Fixed(1), null, null)))));
    }

    // the modern table less every pool that yields item
    private static Table without(Table table, Key item) {
        List<Pool> pools = new ArrayList<>();
        for (Pool pool : table.pools()) {
            if (pool.of().stream().noneMatch(entry -> yields(entry, item))) pools.add(pool);
        }
        return new Table(List.copyOf(pools));
    }

    private static boolean yields(Entry entry, Key item) {
        return switch (entry) {
            case Item i -> i.item().equals(item);
            case Any any -> any.of().stream().anyMatch(child -> yields(child, item));
        };
    }

    @SuppressWarnings("unused")
    private static List<Cond> none() {
        return List.of();
    }
}
