package io.github.term4.polyp.mechanics.itemdamage;

import io.github.term4.polyp.codegen.GenerateBuilder;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.config.SubjectContext;
import net.minestom.server.entity.Entity;
import net.kyori.adventure.key.Key;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * What destroys a dropped item, per scope ({@code itemDamage} profile member). Vanilla (both eras) gives the
 * entity {@link #health} 5 and subtracts each hit; fire also IGNITES it, and the burn keeps charging after it
 * leaves the flame.
 *
 * <p>Everything is overridable and removable: {@link Builder#health(Material, Integer) per-item health},
 * {@link Builder#damage per-source price}, and immunity in both directions -
 * {@link Builder#immune add} or {@link Builder#vulnerable strip} one, including the vanilla defaults.
 */
@GenerateBuilder
public final class ItemDamageConfig {

    /** What an item-damage knob resolves against: the dropped item entity. */
    public record ItemDamageContext(@Nullable Entity subject) implements SubjectContext {}

    /** Unset = active. */
    public final @Nullable FieldValue<ItemDamageContext, Boolean> enabled;
    /** Starting health of a dropped item (vanilla 5, both eras). */
    public final @Nullable FieldValue<ItemDamageContext, Integer> health;
    /** Below the world floor an item is removed outright, never damaged (vanilla {@code outOfWorld}); unset = on. */
    public final @Nullable FieldValue<ItemDamageContext, Boolean> voidDestroys;
    /** Honour the stack's own {@code damage_resistant} component (26.1's rule: netherite shrugs off fire); unset = on. */
    public final @Nullable FieldValue<ItemDamageContext, Boolean> itemResistance;
    /** Fire-ticks an item standing in flame is lit for (vanilla {@code setOnFire(8)} = 160). */
    public final @Nullable FieldValue<ItemDamageContext, Integer> fireIgniteTicks;
    /** Fire-ticks lava lights it for (vanilla {@code setOnFire(15)} = 300). */
    public final @Nullable FieldValue<ItemDamageContext, Integer> lavaIgniteTicks;
    /** Fire-ticks between lingering burn charges (vanilla {@code fireTicks % 20}). */
    public final @Nullable FieldValue<ItemDamageContext, Integer> burnInterval;
    /** Ticks a ground item lasts; vanilla 6000. {@code 0} = forever. */
    public final @Nullable FieldValue<ItemDamageContext, Integer> despawnTicks;
    /** Per-item health, overriding {@link #health()}. */
    public final Map<Material, Integer> healthPerItem;
    /** Per-source damage, keyed by the {@link ItemDamageSystem} source keys; unset = the source's own amount. */
    public final Map<Key, Float> damage;
    /** Per-item added immunity: material -&gt; the sources it shrugs off. */
    public final Map<Material, Set<Key>> immune;
    /** Per-item REMOVED immunity; beats {@link #immune}, the resistance component and the vanilla defaults. */
    public final Map<Material, Set<Key>> vulnerable;

    private ItemDamageConfig(Builder b) {
        this.enabled = b.enabled;
        this.health = b.health;
        this.voidDestroys = b.voidDestroys;
        this.itemResistance = b.itemResistance;
        this.fireIgniteTicks = b.fireIgniteTicks;
        this.lavaIgniteTicks = b.lavaIgniteTicks;
        this.burnInterval = b.burnInterval;
        this.despawnTicks = b.despawnTicks;
        this.healthPerItem = Map.copyOf(b.healthPerItem);
        this.damage = Map.copyOf(b.damage);
        this.immune = copy(b.immune);
        this.vulnerable = copy(b.vulnerable);
    }

    private static Map<Material, Set<Key>> copy(Map<Material, Set<Key>> in) {
        Map<Material, Set<Key>> out = new LinkedHashMap<>();
        in.forEach((m, keys) -> out.put(m, Set.copyOf(keys)));
        return Map.copyOf(out);
    }

    /** Starting health for {@code material}, or null = {@link #health()}. */
    public @Nullable Integer health(Material material) { return healthPerItem.get(material); }

    /** Damage for {@code source}, or null = use the amount the source itself supplies. */
    public @Nullable Float damage(Key source) { return damage.get(source); }

    /** Whether the scope adds immunity to {@code source} for {@code material}. */
    public boolean immune(Material material, Key source) {
        Set<Key> keys = immune.get(material);
        return keys != null && keys.contains(source);
    }

    /** Whether the scope STRIPS immunity to {@code source} for {@code material} (beats every other rule). */
    public boolean vulnerable(Material material, Key source) {
        Set<Key> keys = vulnerable.get(material);
        return keys != null && keys.contains(source);
    }

    /** Merges this config over {@code base}; the maps overlay entry-wise. */
    public ItemDamageConfig fromBase(ItemDamageConfig base) {
        Builder b = new Builder();
        b.mergeKnobs(this, base);
        b.healthPerItem.putAll(base.healthPerItem);
        b.healthPerItem.putAll(healthPerItem);
        b.damage.putAll(base.damage);
        b.damage.putAll(damage);
        merge(b.immune, base.immune); merge(b.immune, immune);
        merge(b.vulnerable, base.vulnerable); merge(b.vulnerable, vulnerable);
        return b.build();
    }

    private static void merge(Map<Material, Set<Key>> into, Map<Material, Set<Key>> from) {
        from.forEach((m, keys) -> into.computeIfAbsent(m, k -> new LinkedHashSet<>()).addAll(keys));
    }

    public static Builder builder() { return new Builder(); }
    public Builder toBuilder() { return new Builder(this); }

    public static final class Builder extends ItemDamageConfigBuilderBase<Builder> {

        @Override protected Builder self() { return this; }

        private final Map<Material, Integer> healthPerItem = new LinkedHashMap<>();
        private final Map<Key, Float> damage = new LinkedHashMap<>();
        private final Map<Material, Set<Key>> immune = new LinkedHashMap<>();
        private final Map<Material, Set<Key>> vulnerable = new LinkedHashMap<>();

        Builder() {}
        Builder(ItemDamageConfig c) {
            super(c);
            healthPerItem.putAll(c.healthPerItem);
            damage.putAll(c.damage);
            merge(immune, c.immune);
            merge(vulnerable, c.vulnerable);
        }

        /** Health for one item; {@code null} drops the override back to the global {@link #health(Integer)}. */
        public Builder health(Material material, @Nullable Integer v) {
            if (v == null) healthPerItem.remove(material); else healthPerItem.put(material, v);
            return this;
        }

        /** Prices one source; {@code null} clears it back to the source's own amount. */
        public Builder damage(Key source, @Nullable Float amount) {
            if (amount == null) damage.remove(source); else damage.put(source, amount);
            return this;
        }

        /** {@code material} shrugs off these sources. */
        public Builder immune(Material material, Key... sources) {
            immune.computeIfAbsent(material, k -> new LinkedHashSet<>()).addAll(Set.of(sources));
            Set<Key> stripped = vulnerable.get(material);
            if (stripped != null) stripped.removeAll(Set.of(sources));
            return this;
        }

        /** {@code material} takes these sources even if vanilla, its component, or {@link #immune} exempts it. */
        public Builder vulnerable(Material material, Key... sources) {
            vulnerable.computeIfAbsent(material, k -> new LinkedHashSet<>()).addAll(Set.of(sources));
            Set<Key> granted = immune.get(material);
            if (granted != null) granted.removeAll(Set.of(sources));
            return this;
        }

        public ItemDamageConfig build() { return new ItemDamageConfig(this); }
    }
}
