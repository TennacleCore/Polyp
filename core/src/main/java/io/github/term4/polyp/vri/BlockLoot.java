package io.github.term4.polyp.vri;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.term4.polyp.vri.BlockDrops.DropContext;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The vanilla block loot tables, 26.1's {@code data/minecraft/loot_table/blocks} boiled down to what a player's
 * break can test: silk touch, shears, fortune, the block's state and chance. A block with no table drops nothing,
 * as vanilla's {@code noLootTable} blocks do. Not carried over: what only the pot, the amethyst cluster and the
 * multiface growths (glow lichen, sculk vein, resin clump) test, and nether wart's conditional bonus; those drop
 * nothing until written in.
 */
public final class BlockLoot {

    /** What a break of the block yields: each pool once. */
    public record Table(@NotNull List<Pool> pools) {}

    public record Pool(@NotNull List<Cond> when, @NotNull List<Entry> of) {}

    public sealed interface Entry permits Item, Any {
        @NotNull List<Cond> when();
    }

    public record Item(@NotNull Key item, @NotNull List<Cond> when, @NotNull Count count, @Nullable Bonus bonus,
                       int @Nullable [] cap) implements Entry {}

    /** The first child whose conditions hold. */
    public record Any(@NotNull List<Entry> of, @NotNull List<Cond> when) implements Entry {}

    public sealed interface Cond permits Silk, Shears, Chance, TableBonus, State, Not, AnyOf {}

    public record Silk() implements Cond {}

    public record Shears() implements Cond {}

    public record Chance(double chance) implements Cond {}

    /** A chance by fortune level, the last for every level past it. */
    public record TableBonus(double[] chances) implements Cond {}

    public record State(@NotNull Map<String, String> properties) implements Cond {}

    public record Not(@NotNull Cond cond) implements Cond {}

    public record AnyOf(@NotNull List<Cond> conds) implements Cond {}

    public sealed interface Count permits Fixed, Uniform, Binomial, ByState {}

    public record Fixed(int n) implements Count {}

    public record Uniform(int min, int max) implements Count {}

    public record Binomial(int n, double p) implements Count {}

    /** A count by the block's {@code property} value, else {@code otherwise}. */
    public record ByState(@NotNull String property, @NotNull Map<String, Count> by, @NotNull Count otherwise) implements Count {}

    /** Fortune's {@code apply_bonus} formulas. */
    public sealed interface Bonus permits OreDrops, UniformBonus, BinomialBonus {}

    public record OreDrops() implements Bonus {}

    public record UniformBonus(int multiplier) implements Bonus {}

    public record BinomialBonus(int extra, double probability) implements Bonus {}

    private static final Map<Key, Table> TABLES = load();

    private BlockLoot() {}

    /** The block's table, or null for a block that drops nothing. */
    public static @Nullable Table of(@NotNull Key block) {
        return TABLES.get(block);
    }

    /** The drops of {@code table} for this break; empty for none. */
    public static @NotNull List<ItemStack> drops(@NotNull Table table, @NotNull DropContext ctx) {
        Random random = ThreadLocalRandom.current();
        List<ItemStack> out = new ArrayList<>();
        for (Pool pool : table.pools()) {
            if (!holds(pool.when(), ctx, random)) continue;
            for (Entry entry : pool.of()) pour(entry, ctx, random, out);
        }
        return out;
    }

    private static void pour(Entry entry, DropContext ctx, Random random, List<ItemStack> out) {
        if (!holds(entry.when(), ctx, random)) return;
        switch (entry) {
            case Item item -> {
                Material material = Material.fromKey(item.item());
                if (material == null) return;
                int n = count(item.count(), ctx, random);
                if (item.bonus() != null && ctx.fortune() > 0) n = bonus(item.bonus(), n, ctx.fortune(), random);
                if (item.cap() != null) n = Math.max(item.cap()[0], Math.min(item.cap()[1], n));
                ItemStack unit = ItemStack.of(material);
                while (n > 0) {
                    int now = Math.min(n, unit.maxStackSize());
                    out.add(unit.withAmount(now));
                    n -= now;
                }
            }
            case Any any -> {
                for (Entry child : any.of()) {
                    if (holds(child.when(), ctx, random)) {
                        pour(child, ctx, random, out);
                        return;
                    }
                }
            }
        }
    }

    private static boolean holds(List<Cond> conds, DropContext ctx, Random random) {
        for (Cond cond : conds) {
            if (!holds(cond, ctx, random)) return false;
        }
        return true;
    }

    private static boolean holds(Cond cond, DropContext ctx, Random random) {
        return switch (cond) {
            case Silk ignored -> ctx.silkTouch();
            case Shears ignored -> ctx.tool().material() == Material.SHEARS;
            case Chance chance -> random.nextDouble() < chance.chance();
            case TableBonus bonus -> random.nextDouble() < bonus.chances()[Math.min(ctx.fortune(), bonus.chances().length - 1)];
            case State state -> {
                for (var property : state.properties().entrySet()) {
                    if (!property.getValue().equals(ctx.block().getProperty(property.getKey()))) yield false;
                }
                yield true;
            }
            case Not not -> !holds(not.cond(), ctx, random);
            case AnyOf any -> {
                for (Cond c : any.conds()) if (holds(c, ctx, random)) yield true;
                yield false;
            }
        };
    }

    private static int count(Count count, DropContext ctx, Random random) {
        return switch (count) {
            case Fixed fixed -> fixed.n();
            case Uniform uniform -> uniform.min() + random.nextInt(uniform.max() - uniform.min() + 1);
            case Binomial binomial -> {
                int n = 0;
                for (int i = 0; i < binomial.n(); i++) if (random.nextDouble() < binomial.p()) n++;
                yield n;
            }
            case ByState by -> {
                String value = ctx.block().getProperty(by.property());
                Count picked = value != null ? by.by().get(value) : null;
                yield count(picked != null ? picked : by.otherwise(), ctx, random);
            }
        };
    }

    // vanilla ApplyBonusCount, as 1.8's BlockOre / BlockGlowstone / BlockCrops rolled it
    private static int bonus(Bonus bonus, int n, int fortune, Random random) {
        return switch (bonus) {
            case OreDrops ignored -> n * (Math.max(0, random.nextInt(fortune + 2) - 1) + 1);
            case UniformBonus uniform -> n + random.nextInt(fortune * uniform.multiplier() + 1);
            case BinomialBonus binomial -> {
                int extra = 0;
                for (int i = 0; i < fortune + binomial.extra(); i++) if (random.nextDouble() < binomial.probability()) extra++;
                yield n + extra;
            }
        };
    }

    private static Map<Key, Table> load() {
        Map<Key, Table> tables = new HashMap<>();
        try (InputStream in = BlockLoot.class.getResourceAsStream("block-loot.json")) {
            if (in == null) throw new IllegalStateException("block-loot.json is missing from the jar");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : root.entrySet()) {
                List<Pool> pools = new ArrayList<>();
                for (JsonElement pool : entry.getValue().getAsJsonArray()) {
                    JsonObject p = pool.getAsJsonObject();
                    List<Entry> of = new ArrayList<>();
                    for (JsonElement e : p.getAsJsonArray("of")) of.add(entry(e.getAsJsonObject()));
                    pools.add(new Pool(conds(p.get("if")), List.copyOf(of)));
                }
                tables.put(Key.key(entry.getKey()), new Table(List.copyOf(pools)));
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("block-loot.json did not read", e);
        }
        return Map.copyOf(tables);
    }

    private static Entry entry(JsonObject e) {
        List<Cond> when = conds(e.get("if"));
        if (e.has("any")) {
            List<Entry> of = new ArrayList<>();
            for (JsonElement child : e.getAsJsonArray("any")) of.add(entry(child.getAsJsonObject()));
            return new Any(List.copyOf(of), when);
        }
        Bonus bonus = null;
        if (e.has("bonus")) {
            JsonObject b = e.getAsJsonObject("bonus");
            if (b.has("ore")) bonus = new OreDrops();
            else if (b.has("uniform")) bonus = new UniformBonus(b.get("uniform").getAsInt());
            else bonus = new BinomialBonus(b.getAsJsonArray("binomial").get(0).getAsInt(), b.getAsJsonArray("binomial").get(1).getAsDouble());
        }
        int[] cap = null;
        if (e.has("cap")) {
            JsonArray c = e.getAsJsonArray("cap");
            cap = new int[]{c.get(0).getAsInt(), c.get(1).getAsInt()};
        }
        return new Item(Key.key(e.get("item").getAsString()), when, e.has("n") ? count(e.get("n")) : new Fixed(1), bonus, cap);
    }

    private static Count count(JsonElement n) {
        if (n.isJsonPrimitive()) return new Fixed(n.getAsInt());
        if (n.isJsonArray()) return new Uniform(n.getAsJsonArray().get(0).getAsInt(), n.getAsJsonArray().get(1).getAsInt());
        JsonObject o = n.getAsJsonObject();
        if (o.has("binomial")) return new Binomial(o.getAsJsonArray("binomial").get(0).getAsInt(), o.getAsJsonArray("binomial").get(1).getAsDouble());
        Map<String, Count> by = new HashMap<>();
        Count otherwise = new Fixed(1);
        for (var member : o.entrySet()) {
            if (member.getKey().equals("state")) continue;
            if (member.getKey().equals("else")) otherwise = count(member.getValue());
            else by.put(member.getKey(), count(member.getValue()));
        }
        return new ByState(o.get("state").getAsString(), Map.copyOf(by), otherwise);
    }

    private static List<Cond> conds(@Nullable JsonElement list) {
        if (list == null) return List.of();
        List<Cond> out = new ArrayList<>();
        for (JsonElement c : list.getAsJsonArray()) out.add(cond(c));
        return List.copyOf(out);
    }

    private static Cond cond(JsonElement c) {
        if (c.isJsonPrimitive()) return c.getAsString().equals("silk") ? new Silk() : new Shears();
        JsonObject o = c.getAsJsonObject();
        if (o.has("chance")) return new Chance(o.get("chance").getAsDouble());
        if (o.has("bonus")) {
            JsonArray chances = o.getAsJsonArray("bonus");
            double[] out = new double[chances.size()];
            for (int i = 0; i < out.length; i++) out[i] = chances.get(i).getAsDouble();
            return new TableBonus(out);
        }
        if (o.has("state")) {
            Map<String, String> properties = new HashMap<>();
            for (var property : o.getAsJsonObject("state").entrySet()) properties.put(property.getKey(), property.getValue().getAsString());
            return new State(Map.copyOf(properties));
        }
        if (o.has("not")) return new Not(cond(o.get("not")));
        return new AnyOf(conds(o.get("any")));
    }
}
