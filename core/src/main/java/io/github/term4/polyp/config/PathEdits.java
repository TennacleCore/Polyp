package io.github.term4.polyp.config;

import io.github.term4.polyp.ConfigKey;
import io.github.term4.polyp.fx.FxHandler;
import io.github.term4.polyp.fx.FxRegistry;
import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.mechanics.consumable.ConsumableConfig;
import io.github.term4.polyp.mechanics.consumable.ConsumableTypeConfig;
import io.github.term4.polyp.mechanics.damage.DamageConfig;
import io.github.term4.polyp.mechanics.damage.types.DamageTypeConfig;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;
import io.github.term4.polyp.mechanics.projectile.types.ProjectileTypeConfig;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.BiFunction;

/**
 * Applies a data-addressed edit to a {@link MechanicsProfile.Builder}:
 * <pre>member[/typeKey]/knob = value    e.g. projectiles/minecraft:arrow/critDamage = false</pre>
 * The knob half is the generated {@code <Config>BuilderBase.KNOBS} table, so every constant-valued
 * {@code FieldValue} knob is addressable without declaration; the member half is the small registry below.
 * Writes go through {@link MechanicsProfile.Builder#mutate} + {@code fromBase}, so an edit layers over the
 * inherited config - it cannot wipe base tuning. Everything invalid throws {@link IllegalArgumentException}
 * with the reason; nothing is a silent no-op.
 */
public final class PathEdits {

    private PathEdits() {}

    /** A typed-container member: how to read an entry and how to rebuild the container around a replaced one. */
    private record TypedFamily(Class<?> defaultEntryClass,
                               BiFunction<Object, Key, @Nullable Object> entry,
                               BiFunction<@Nullable Object, Object, Object> withEntry) {}

    /** A member whose value has no knob table (FX): it owns the whole edit. */
    @FunctionalInterface
    private interface Editor {
        Object edit(@Nullable Object base, String slot, String rawValue, String path);
    }

    private record Member(ConfigKey<?> key, Class<?> configClass, @Nullable TypedFamily typed, @Nullable Editor editor) {
        Member(ConfigKey<?> key, Class<?> configClass, @Nullable TypedFamily typed) { this(key, configClass, typed, null); }
    }

    private static final Map<String, Member> MEMBERS = new LinkedHashMap<>();

    static {
        Vocabulary.ensure();

        MEMBERS.put("projectiles", new Member(MechanicsKeys.PROJECTILES, ProjectileConfig.class, new TypedFamily(
                ProjectileTypeConfig.class,
                (c, k) -> ((ProjectileConfig) c).typeConfig(k),
                (base, e) -> {
                    ProjectileConfig sparse = ProjectileConfig.builder().typeConfigs((ProjectileTypeConfig) e).build();
                    return base == null ? sparse : sparse.fromBase((ProjectileConfig) base);
                })));
        MEMBERS.put("consumables", new Member(MechanicsKeys.CONSUMABLES, ConsumableConfig.class, new TypedFamily(
                ConsumableTypeConfig.class,
                (c, k) -> ((ConsumableConfig) c).typeConfig(k),
                (base, e) -> {
                    ConsumableConfig sparse = ConsumableConfig.builder().typeConfigs((ConsumableTypeConfig) e).build();
                    return base == null ? sparse : sparse.fromBase((ConsumableConfig) base);
                })));
        MEMBERS.put("damage", new Member(MechanicsKeys.DAMAGE, DamageConfig.class, new TypedFamily(
                DamageTypeConfig.class,
                (c, k) -> ((DamageConfig) c).typeConfig(k),
                (base, e) -> {
                    DamageConfig sparse = DamageConfig.builder().typeConfig((DamageTypeConfig) e).build();
                    return base == null ? sparse : sparse.fromBase((DamageConfig) base);
                })));
        MEMBERS.put("attack", new Member(MechanicsKeys.ATTACK, io.github.term4.polyp.mechanics.attack.AttackConfig.class, null));
        MEMBERS.put("explosion", new Member(MechanicsKeys.EXPLOSION, io.github.term4.polyp.mechanics.explosion.ExplosionConfig.class, null));
        MEMBERS.put("tnt", new Member(MechanicsKeys.TNT, io.github.term4.polyp.mechanics.explosion.TntConfig.class, null));
        MEMBERS.put("death", new Member(MechanicsKeys.DEATH, io.github.term4.polyp.mechanics.damage.DeathConfig.class, null));
        MEMBERS.put("knockback", new Member(MechanicsKeys.KNOCKBACK, io.github.term4.polyp.mechanics.knockback.KnockbackConfig.class, null));
        MEMBERS.put("attributes", new Member(MechanicsKeys.ATTRIBUTES, io.github.term4.polyp.mechanics.attribute.AttributeConfig.class, null));
        MEMBERS.put("hunger", new Member(MechanicsKeys.HUNGER, io.github.term4.polyp.mechanics.hunger.HungerConfig.class, null));
        MEMBERS.put("vri", new Member(MechanicsKeys.VRI, io.github.term4.polyp.vri.VriConfig.class, null));
        MEMBERS.put("player", new Member(MechanicsKeys.PLAYER, io.github.term4.polyp.platform.player.PlayerConfig.class, null));
        MEMBERS.put("item-damage", new Member(MechanicsKeys.ITEM_DAMAGE, io.github.term4.polyp.mechanics.itemdamage.ItemDamageConfig.class, null));
        // fx/<key> = <factory call>, e.g. to(at-listener(watchers), sound(entity.player.teleport, player, 1, 1))
        MEMBERS.put("fx", new Member(MechanicsKeys.FX, FxRegistry.class, null, (base, slot, raw, path) -> {
            Key fxKey = parseKey(slot, path);
            FxHandler handler = FieldFns.parse(FxHandler.class, raw, path);
            return (base != null ? (FxRegistry) base : FxRegistry.empty()).register(fxKey, handler);
        }));
    }

    /** The addressable member names (for errors and enumeration). */
    public static java.util.Set<String> members() { return MEMBERS.keySet(); }

    /**
     * Checks that {@code path = rawValue} would apply, without touching anything: same parse, same member
     * and knob lookup, same value decode. Returns the reason it would fail, or {@code null} when it is fine.
     *
     * <p>Polyp cannot know WHEN to run this - a name like {@code heal-apple} is registered while a mode
     * installs, so anything eager would report false errors. The server owns the moment; this owns the check.
     */
    public static @Nullable String validate(@Nullable MechanicsProfile fallback, String path, String rawValue) {
        try {
            apply(MechanicsProfile.builder(), fallback, path, rawValue);
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    /**
     * Applies {@code path = rawValue} onto {@code b}. The edited base is the builder's current member,
     * else {@code fallback}'s (the resolved global profile) - so a ruleset layers over the preset it runs on.
     */
    public static void apply(MechanicsProfile.Builder b, @Nullable MechanicsProfile fallback, String path, String rawValue) {
        apply(b, fallback, path, rawValue, null);
    }

    /**
     * {@link #apply} for the players {@code who} accepts only: the knob becomes {@link FieldValue#targeted},
     * with what it held before as the fallback for everyone else. A later edit to the same knob replaces it,
     * so fold targeted entries after the untargeted ones.
     */
    public static void apply(MechanicsProfile.Builder b, @Nullable MechanicsProfile fallback, String path,
                             String rawValue, @Nullable Predicate<Player> who) {
        String[] parts = path.split("/");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("path must be member/knob or member/typeKey/knob: " + path);
        }
        Member member = MEMBERS.get(parts[0]);
        if (member == null) {
            throw new IllegalArgumentException("unknown member '" + parts[0] + "' in " + path + " (members: " + members() + ")");
        }
        @SuppressWarnings("unchecked")
        ConfigKey<Object> key = (ConfigKey<Object>) member.key();
        b.mutate(key, cur -> {
            Object base = cur != null ? cur : (fallback != null ? fallback.get(key) : null);
            if (member.editor() != null) {
                if (parts.length != 2) throw new IllegalArgumentException(parts[0] + " paths are " + parts[0] + "/<key>: " + path);
                if (who != null) throw new IllegalArgumentException(parts[0] + " entries cannot vary per player: " + path);
                return member.editor().edit(base, parts[1], rawValue, path);
            }
            if (parts.length == 2) {
                return editFlat(member.configClass(), base, parts[1], rawValue, path, who);
            }
            if (member.typed() == null) {
                throw new IllegalArgumentException(parts[0] + " has no type entries - use " + parts[0] + "/" + parts[2] + ": " + path);
            }
            Key typeKey = parseKey(parts[1], path);
            Object baseEntry = base != null ? member.typed().entry().apply(base, typeKey) : null;
            Object entry = editEntry(member.typed().defaultEntryClass(), baseEntry, typeKey, parts[2], rawValue, path, who);
            return member.typed().withEntry().apply(base, entry);
        });
    }

    // -------------------------------------------------------------- entry / flat editing

    private static Object editFlat(Class<?> configClass, @Nullable Object base, String knobName, String raw,
                                   String path, @Nullable Predicate<Player> who) {
        Class<?> cls = base != null ? base.getClass() : configClass;
        ConfigKnob knob = findKnob(cls, knobName, path);
        Object builder = base != null ? copyBuilder(base, path) : newBuilder(cls, null, path);
        knob.set().accept(builder, value(knob, base, raw, path, who));
        return build(builder, path);
    }

    /** {@code base.toBuilder()}: editing the base's own builder keeps every other field by construction. */
    private static Object copyBuilder(Object base, String path) {
        try {
            return base.getClass().getMethod("toBuilder").invoke(base);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(base.getClass().getSimpleName() + " has no toBuilder(): " + path, e);
        }
    }

    /**
     * The value a knob's setter wants: a constant - or, targeted, a FieldValue that answers {@code decoded}
     * for {@code who} and the base's own value for everyone else.
     */
    private static Object value(ConfigKnob knob, @Nullable Object base, String raw, String path,
                                @Nullable Predicate<Player> who) {
        Object decoded = decode(knob, raw, path);
        if (who == null) return FieldValue.constant(decoded);
        @SuppressWarnings("unchecked")
        FieldValue<SubjectContext, Object> inherited = base != null ? (FieldValue<SubjectContext, Object>) knob.get().apply(base) : null;
        return FieldValue.targeted(who, FieldValue.constant(decoded), inherited);
    }

    private static Object editEntry(Class<?> defaultClass, @Nullable Object baseEntry, Key typeKey,
                                    String knobName, String raw, String path, @Nullable Predicate<Player> who) {
        Class<?> cls = baseEntry != null ? baseEntry.getClass() : defaultClass;
        ConfigKnob knob = findKnob(cls, knobName, path);
        Object builder = baseEntry != null ? copyBuilder(baseEntry, path) : newBuilder(cls, typeKey, path);
        try {
            knob.set().accept(builder, value(knob, baseEntry, raw, path, who));
        } catch (ClassCastException e) {
            // the knob is declared on an ancestor whose builder line this entry's builder doesn't extend
            throw new IllegalArgumentException("'" + knobName + "' is declared above " + cls.getSimpleName()
                    + " and cannot be path-set on this entry (" + e.getMessage() + "): " + path);
        }
        return build(builder, path);
    }

    /** The knob from the config class's own generated table, else the nearest ancestor's. */
    private static ConfigKnob findKnob(Class<?> configClass, String name, String path) {
        for (Class<?> c = configClass; c != null && c != Object.class; c = c.getSuperclass()) {
            Map<String, ConfigKnob> knobs = knobsOf(c);
            if (knobs == null) continue;
            ConfigKnob knob = knobs.get(name);
            if (knob == null) continue;
            if (knob.valueType() == null) {
                throw new IllegalArgumentException("'" + name + "' is a code-only knob (generic type): " + path);
            }
            return knob;
        }
        throw new IllegalArgumentException("unknown knob '" + name + "' on " + configClass.getSimpleName() + ": " + path);
    }

    private static final Map<Class<?>, Object> KNOB_TABLES = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    private static @Nullable Map<String, ConfigKnob> knobsOf(Class<?> configClass) {
        Object cached = KNOB_TABLES.computeIfAbsent(configClass, c -> {
            try {
                return Class.forName(c.getName() + "BuilderBase").getField("KNOBS").get(null);
            } catch (ReflectiveOperationException e) {
                return Map.of(); // not a @GenerateBuilder config
            }
        });
        Map<String, ConfigKnob> map = (Map<String, ConfigKnob>) cached;
        return map.isEmpty() ? null : map;
    }

    // -------------------------------------------------------------- builder plumbing (cached reflection)

    private static final Map<Class<?>, Method> BUILDER_FACTORIES = new ConcurrentHashMap<>();

    private static Object newBuilder(Class<?> configClass, @Nullable Key typeKey, String path) {
        Method m = BUILDER_FACTORIES.computeIfAbsent(configClass, c -> {
            // DECLARED only: getMethod also finds a superclass's static builder, whose Builder is the wrong line
            if (typeKey != null) {
                try {
                    return c.getDeclaredMethod("builder", Key.class);
                } catch (NoSuchMethodException ignored) {
                }
            }
            try {
                return c.getDeclaredMethod("builder");
            } catch (NoSuchMethodException e) {
                return null;
            }
        });
        if (m == null) throw new IllegalArgumentException(configClass.getSimpleName() + " has no builder(): " + path);
        try {
            return m.getParameterCount() == 1 ? m.invoke(null, typeKey) : m.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(configClass.getSimpleName() + " builder failed: " + e.getMessage(), e);
        }
    }

    private static Object build(Object builder, String path) {
        try {
            return builder.getClass().getMethod("build").invoke(builder);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("build() failed for " + path + ": " + e.getMessage(), e);
        }
    }


    // -------------------------------------------------------------- values

    private static Key parseKey(String raw, String path) {
        try {
            return Key.key(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException("bad type key '" + raw + "' in " + path);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object decode(ConfigKnob knob, String raw, String path) {
        Class<?> t = knob.valueType();
        try {
            if (t == Boolean.class) {
                if (raw.equalsIgnoreCase("true")) return Boolean.TRUE;
                if (raw.equalsIgnoreCase("false")) return Boolean.FALSE;
                throw new IllegalArgumentException("not a boolean");
            }
            if (t == Integer.class) return Integer.valueOf(raw);
            if (t == Long.class) return Long.valueOf(raw);
            if (t == Double.class) return Double.valueOf(raw);
            if (t == Float.class) return Float.valueOf(raw);
            if (t == String.class) return raw;
            if (t.isEnum()) return Enum.valueOf((Class<? extends Enum>) t, raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + raw + "' is not a " + t.getSimpleName() + " for " + path);
        }
        if (FieldFns.supports(t)) return FieldFns.parse(t, raw, path);
        throw new IllegalArgumentException(knob.name() + " takes a " + t.getSimpleName() + " - not path-settable yet: " + path);
    }
}
