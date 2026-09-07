package io.github.term4.polyp.config;

import io.github.term4.polyp.ConfigKey;
import net.minestom.server.item.Material;
import java.util.function.Function;
import java.util.List;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.TreeMap;
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
 * inherited config - it cannot wipe base tuning. A value naming a registered mutation
 * ({@code damage/enabledTypes = without(minecraft:fall)}) edits the inherited constant instead of replacing it
 * ({@link FieldFns#registerMutation}). Everything invalid throws {@link IllegalArgumentException} with the reason;
 * nothing is a silent no-op.
 */
public final class PathEdits {

    private PathEdits() {}

    /** Maps a path segment to a container's entry key: a namespaced id, a material, or a plain name. */
    @FunctionalInterface
    private interface KeyOf { Object apply(String segment, String path); }

    /** Rebuilds a container around one replaced entry. */
    @FunctionalInterface
    private interface WithEntry { Object apply(@Nullable Object container, Object key, Object entry); }

    /**
     * A typed-container member: entries by key ({@code member/key/knob}), and optionally a defaults entry that the
     * two-part form edits when the container itself has no such knob ({@code projectiles/speed}).
     */
    private record TypedFamily(Class<?> entryClass, KeyOf keyOf,
                               BiFunction<Object, Object, @Nullable Object> entry, WithEntry withEntry,
                               @Nullable Function<Object, @Nullable Object> defaults,
                               @Nullable BiFunction<@Nullable Object, Object, Object> withDefaults,
                               Function<@Nullable Object, Collection<String>> keys) {}

    /** A member whose value has no knob table (fx, cooldowns, items): it owns the whole edit. */
    @FunctionalInterface
    private interface Editor {
        Object edit(@Nullable Object base, List<String> rest, String rawValue, String path, @Nullable Predicate<Player> who);
    }

    /**
     * @param configClass the class knobs are looked up on for {@code member/knob}, or decoded from for a scalar
     * @param scalar      whether the one-part form {@code member = value} replaces the whole member (a behaviour or enum)
     * @param targetable  whether a per-player write stores the edited member for those players
     *                    ({@link MechanicsProfile.Builder#target}) - for a member with no knob table to target inside
     */
    private record Member(ConfigKey<?> key, Class<?> configClass, @Nullable TypedFamily typed, @Nullable Editor editor,
                          boolean scalar, @Nullable String form, boolean targetable) {
        Member(ConfigKey<?> key, Class<?> configClass) { this(key, configClass, null, null, false, null, false); }
        Member(ConfigKey<?> key, Class<?> configClass, TypedFamily typed) { this(key, configClass, typed, null, false, null, false); }
        Member(ConfigKey<?> key, Class<?> configClass, @Nullable TypedFamily typed, @Nullable Editor editor,
               boolean scalar, @Nullable String form) {
            this(key, configClass, typed, editor, scalar, form, false);
        }
    }

    private static final Map<String, Member> MEMBERS = new LinkedHashMap<>();

    private static final KeyOf NAMESPACED = PathEdits::parseKey;
    private static final KeyOf MATERIAL = (segment, path) -> {
        Material m = Material.fromKey(parseKey(segment, path));
        if (m == null) throw new IllegalArgumentException("unknown material '" + segment + "' in " + path);
        return m;
    };
    private static final KeyOf NAME = (segment, path) -> segment;

    static {
        Vocabulary.ensure();

        MEMBERS.put("projectiles", new Member(MechanicsKeys.PROJECTILES, ProjectileConfig.class, new TypedFamily(
                ProjectileTypeConfig.class, NAMESPACED,
                (c, k) -> ((ProjectileConfig) c).typeConfig((Key) k),
                (container, k, e) -> {
                    ProjectileConfig sparse = ProjectileConfig.builder().typeConfigs((ProjectileTypeConfig) e).build();
                    return container == null ? sparse : sparse.fromBase((ProjectileConfig) container);
                },
                c -> ((ProjectileConfig) c).defaults(),
                (container, e) -> (container != null ? ((ProjectileConfig) container).toBuilder() : ProjectileConfig.builder())
                        .defaults((ProjectileTypeConfig) e).build(),
                c -> c == null ? List.of() : keyNames(((ProjectileConfig) c).typeConfigs.keySet()))));
        MEMBERS.put("consumables", new Member(MechanicsKeys.CONSUMABLES, ConsumableConfig.class, new TypedFamily(
                ConsumableTypeConfig.class, NAMESPACED,
                (c, k) -> ((ConsumableConfig) c).typeConfig((Key) k),
                (container, k, e) -> {
                    ConsumableConfig sparse = ConsumableConfig.builder().typeConfigs((ConsumableTypeConfig) e).build();
                    return container == null ? sparse : sparse.fromBase((ConsumableConfig) container);
                },
                null, null, c -> c == null ? List.of() : keyNames(((ConsumableConfig) c).typeConfigs.keySet()))));
        MEMBERS.put("damage", new Member(MechanicsKeys.DAMAGE, DamageConfig.class, new TypedFamily(
                DamageTypeConfig.class, NAMESPACED,
                (c, k) -> ((DamageConfig) c).typeConfig((Key) k),
                (container, k, e) -> {
                    DamageConfig sparse = DamageConfig.builder().typeConfig((DamageTypeConfig) e).build();
                    return container == null ? sparse : sparse.fromBase((DamageConfig) container);
                },
                null, null, c -> c == null ? List.of() : keyNames(((DamageConfig) c).typeConfigs.keySet()))));
        MEMBERS.put("blocking", new Member(MechanicsKeys.BLOCKING, io.github.term4.polyp.mechanics.blocking.BlockingConfig.class, new TypedFamily(
                io.github.term4.polyp.mechanics.blocking.BlockingTypeConfig.class, MATERIAL,
                (c, k) -> ((io.github.term4.polyp.mechanics.blocking.BlockingConfig) c).typeConfig((Material) k),
                (container, k, e) -> (container != null
                        ? ((io.github.term4.polyp.mechanics.blocking.BlockingConfig) container).toBuilder()
                        : io.github.term4.polyp.mechanics.blocking.BlockingConfig.builder())
                        .material((Material) k, (io.github.term4.polyp.mechanics.blocking.BlockingTypeConfig) e).build(),
                c -> ((io.github.term4.polyp.mechanics.blocking.BlockingConfig) c).defaults(),
                (container, e) -> (container != null
                        ? ((io.github.term4.polyp.mechanics.blocking.BlockingConfig) container).toBuilder()
                        : io.github.term4.polyp.mechanics.blocking.BlockingConfig.builder())
                        .defaults((io.github.term4.polyp.mechanics.blocking.BlockingTypeConfig) e).build(),
                c -> c == null ? List.of()
                        : keyNames(((io.github.term4.polyp.mechanics.blocking.BlockingConfig) c).materials.keySet()))));
        // fixes/<toggle>/enabled and visuals/<fix>/<knob>: two path names over the one FIXES member
        MEMBERS.put("fixes", new Member(MechanicsKeys.FIXES, io.github.term4.polyp.platform.fixes.FixesConfig.class, new TypedFamily(
                io.github.term4.polyp.platform.fixes.FixToggleConfig.class, NAME,
                (c, k) -> ((io.github.term4.polyp.platform.fixes.FixesConfig) c).toggle((String) k),
                (container, k, e) -> io.github.term4.polyp.platform.fixes.FixesConfig.withToggle(
                        (io.github.term4.polyp.platform.fixes.FixesConfig) container, (String) k,
                        (io.github.term4.polyp.platform.fixes.FixToggleConfig) e),
                null, null, c -> io.github.term4.polyp.platform.fixes.FixesConfig.TOGGLES)));
        MEMBERS.put("visuals", new Member(MechanicsKeys.FIXES, io.github.term4.polyp.platform.fixes.FixesConfig.class, new TypedFamily(
                io.github.term4.polyp.platform.fixes.visuals.legacy_1_8.LegacyArrowVisibilityConfig.class, NAME,
                (c, k) -> {
                    var visuals = ((io.github.term4.polyp.platform.fixes.FixesConfig) c).visuals();
                    return visuals == null ? null : visuals.entry((String) k);
                },
                (container, k, e) -> io.github.term4.polyp.platform.fixes.FixesConfig.withVisual(
                        (io.github.term4.polyp.platform.fixes.FixesConfig) container, (String) k, e),
                null, null, c -> io.github.term4.polyp.platform.fixes.visuals.VisualsConfig.VISUALS)));

        MEMBERS.put("attack", new Member(MechanicsKeys.ATTACK, io.github.term4.polyp.mechanics.attack.AttackConfig.class));
        MEMBERS.put("explosion", new Member(MechanicsKeys.EXPLOSION, io.github.term4.polyp.mechanics.explosion.ExplosionConfig.class));
        MEMBERS.put("tnt", new Member(MechanicsKeys.TNT, io.github.term4.polyp.mechanics.explosion.TntConfig.class));
        MEMBERS.put("death", new Member(MechanicsKeys.DEATH, io.github.term4.polyp.mechanics.damage.DeathConfig.class));
        MEMBERS.put("knockback", new Member(MechanicsKeys.KNOCKBACK, io.github.term4.polyp.mechanics.knockback.KnockbackConfig.class));
        MEMBERS.put("attributes", new Member(MechanicsKeys.ATTRIBUTES, io.github.term4.polyp.mechanics.attribute.AttributeConfig.class));
        MEMBERS.put("hunger", new Member(MechanicsKeys.HUNGER, io.github.term4.polyp.mechanics.hunger.HungerConfig.class));
        MEMBERS.put("vri", new Member(MechanicsKeys.VRI, io.github.term4.polyp.vri.VriConfig.class));
        MEMBERS.put("player", new Member(MechanicsKeys.PLAYER, io.github.term4.polyp.platform.player.PlayerConfig.class));
        MEMBERS.put("item-damage", new Member(MechanicsKeys.ITEM_DAMAGE, io.github.term4.polyp.mechanics.itemdamage.ItemDamageConfig.class));
        MEMBERS.put("durability", new Member(MechanicsKeys.DURABILITY, io.github.term4.polyp.mechanics.durability.DurabilityConfig.class));
        MEMBERS.put("compat", new Member(MechanicsKeys.COMPAT, io.github.term4.polyp.platform.compatibility.CompatConfig.class));

        // fx/<key> = <factory call>, e.g. to(at-listener(watchers), sound(entity.player.teleport, player, 1, 1))
        MEMBERS.put("fx", new Member(MechanicsKeys.FX, FxRegistry.class, null, (container, rest, raw, path, who) -> {
            one(rest, "fx/<key>", path);
            noTargeting(who, path);
            Key fxKey = parseKey(rest.get(0), path);
            FxHandler handler = FieldFns.parse(FxHandler.class, raw, path);
            return (container != null ? (FxRegistry) container : FxRegistry.empty()).register(fxKey, handler);
        }, false, "fx/<key>"));
        // cooldowns/<material> = ticks
        MEMBERS.put("cooldowns", new Member(MechanicsKeys.COOLDOWNS, io.github.term4.polyp.mechanics.cooldown.CooldownConfig.class, null,
                (container, rest, raw, path, who) -> {
            one(rest, "cooldowns/<material>", path);
            noTargeting(who, path);
            var cfg = (io.github.term4.polyp.mechanics.cooldown.CooldownConfig) container;
            return (cfg != null ? cfg.toBuilder() : io.github.term4.polyp.mechanics.cooldown.CooldownConfig.builder())
                    .cooldown((Material) MATERIAL.apply(rest.get(0), path), integer(raw, path)).build();
        }, false, "cooldowns/<material>"));
        // tick-scaling/referenceTps, tick-scaling/clientTps, tick-scaling/<module key>; per player it stores the
        // edited config for those players whole, so one seat can run dilated where the world stays native
        MEMBERS.put("tick-scaling", new Member(MechanicsKeys.TICK_SCALING, io.github.term4.polyp.util.tick.TickScalingConfig.class, null,
                (container, rest, raw, path, who) -> {
            one(rest, "tick-scaling/<referenceTps|clientTps|module key>", path);
            var cfg = (io.github.term4.polyp.util.tick.TickScalingConfig) container;
            var b = cfg != null ? cfg.toBuilder() : io.github.term4.polyp.util.tick.TickScalingConfig.builder();
            int tps = integer(raw, path);
            switch (rest.get(0)) {
                case "referenceTps" -> b.referenceTps(tps);
                case "clientTps" -> b.clientTps(tps);
                default -> b.referenceTps(parseKey(rest.get(0), path), tps);
            }
            return b.build();
        }, false, "tick-scaling/<referenceTps|clientTps|module key>", true));
        // items/<material>/<stat> = value, both eras
        MEMBERS.put("items", new Member(MechanicsKeys.ITEMS, io.github.term4.polyp.item.ItemRegistry.class, null,
                (container, rest, raw, path, who) -> {
            if (rest.size() != 2) throw new IllegalArgumentException("items paths are items/<material>/<stat>: " + path);
            noTargeting(who, path);
            var registry = (io.github.term4.polyp.item.ItemRegistry) container;
            if (registry == null) throw new IllegalArgumentException("items needs an inherited registry to edit (the era comes from it): " + path);
            Material material = (Material) MATERIAL.apply(rest.get(0), path);
            var stat = io.github.term4.polyp.item.ItemStat.byId(rest.get(1));
            if (stat == null) {
                throw new IllegalArgumentException("unknown item stat '" + rest.get(1) + "' (known: "
                        + io.github.term4.polyp.item.ItemStat.ids() + "): " + path);
            }
            return registry.register(io.github.term4.polyp.item.ItemDef.of(material)
                    .copying(registry.def(material)).both(stat, dbl(raw, path)).build());
        }, false, "items/<material>/<stat>"));
        // item-physics = legacy | modern
        MEMBERS.put("item-physics", new Member(MechanicsKeys.ITEM_PHYSICS,
                io.github.term4.polyp.entity.DroppedItemEntity.Model.class, null, null, true, null));
        // velocity = <rule>, or velocity/<knob> editing the simulated rule's config
        MEMBERS.put("velocity", new Member(MechanicsKeys.VELOCITY, io.github.term4.polyp.tracking.motion.VelocityRule.class, null,
                (container, rest, raw, path, who) -> {
            one(rest, "velocity/<knob> (or velocity = <rule>)", path);
            var rule = (io.github.term4.polyp.tracking.motion.VelocityRule) container;
            var cfg = rule != null ? rule.reconstructionConfig() : io.github.term4.polyp.tracking.motion.VelocityConfig.defaults();
            if (cfg == null) throw new IllegalArgumentException("the inherited velocity rule is custom code; its knobs are not addressable: " + path);
            return io.github.term4.polyp.tracking.motion.VelocityRule.simulated(
                    (io.github.term4.polyp.tracking.motion.VelocityConfig) editFlat(
                            io.github.term4.polyp.tracking.motion.VelocityConfig.class, cfg, rest.get(0), raw, path, who));
        }, true, "velocity/<knob>"));
    }

    /** The addressable member names (for errors and enumeration). */
    public static java.util.Set<String> members() { return MEMBERS.keySet(); }

    /**
     * One addressable step under a path.
     *
     * @param type  what a write there takes, {@code null} when the step is only a way down
     * @param value what it holds now, {@code null} when unset or context-dependent
     * @param more  whether anything is addressable below it
     */
    public record Step(String name, @Nullable String type, @Nullable Object value, boolean more) {}

    /**
     * The steps under {@code path}, one segment at a time - {@code ""} lists the members. {@code member} reads
     * the config a value is shown from, usually a profile registry's resolve for the scope being browsed;
     * everything still lists without one. Unaddressable paths throw the reason a write would.
     */
    public static List<Step> browse(String path, Function<ConfigKey<?>, @Nullable Object> member) {
        if (path.isEmpty()) {
            List<Step> out = new ArrayList<>();
            new TreeMap<>(MEMBERS).forEach((name, m) -> {
                boolean enumScalar = m.scalar() && m.configClass().isEnum();
                out.add(new Step(name, m.scalar() ? typeName(m.configClass()) : null,
                        enumScalar ? member.apply(m.key()) : null,
                        m.editor() != null || m.typed() != null || hasKnobs(m.configClass())));
            });
            return out;
        }
        String[] parts = path.split("/");
        Member m = MEMBERS.get(parts[0]);
        if (m == null) {
            throw new IllegalArgumentException("unknown member '" + parts[0] + "' in '" + path + "' (members: " + members() + ")");
        }
        Object base = member.apply(m.key());
        TypedFamily family = m.typed();
        if (parts.length == 1) {
            if (m.editor() != null) return List.of(new Step(m.form(), null, null, false));
            List<Step> out = new ArrayList<>(knobs(m.configClass(), base));
            if (family == null) return out;
            if (family.defaults() != null) { // the two-part form falls through to the family's defaults entry
                for (Step step : knobs(family.entryClass(), base != null ? family.defaults().apply(base) : null)) {
                    if (out.stream().noneMatch(had -> had.name().equals(step.name()))) out.add(step);
                }
            }
            for (String key : family.keys().apply(base)) out.add(new Step(key, null, null, true));
            return out;
        }
        if (parts.length == 2) {
            Step onMember = knob(m.configClass(), base, parts[1]);
            if (onMember != null) return List.of(onMember);
            if (family == null) {
                throw new IllegalArgumentException("unknown knob '" + parts[1] + "' on " + m.configClass().getSimpleName() + ": " + path);
            }
            if (family.defaults() != null) {
                Step onDefaults = knob(family.entryClass(), base != null ? family.defaults().apply(base) : null, parts[1]);
                if (onDefaults != null) return List.of(onDefaults);
            }
            return knobs(family.entryClass(), entry(family, base, parts[1], path));
        }
        if (parts.length == 3 && family != null) {
            Step step = knob(family.entryClass(), entry(family, base, parts[1], path), parts[2]);
            if (step != null) return List.of(step);
            throw new IllegalArgumentException("unknown knob '" + parts[2] + "' on " + family.entryClass().getSimpleName() + ": " + path);
        }
        throw new IllegalArgumentException("nothing is addressable below " + path);
    }

    private static @Nullable Object entry(TypedFamily family, @Nullable Object base, String segment, String path) {
        return base != null ? family.entry().apply(base, family.keyOf().apply(segment, path)) : null;
    }

    /** The knobs of {@code instance}'s own line - an entry's concrete class carries more than the family's. */
    private static List<Step> knobs(Class<?> declared, @Nullable Object instance) {
        Map<String, ConfigKnob> all = new TreeMap<>();
        for (Class<?> c = instance != null ? instance.getClass() : declared; c != null && c != Object.class; c = c.getSuperclass()) {
            Map<String, ConfigKnob> knobs = knobsOf(c);
            if (knobs != null) knobs.forEach(all::putIfAbsent);
        }
        List<Step> out = new ArrayList<>();
        for (ConfigKnob knob : all.values()) out.add(step(knob, instance));
        return out;
    }

    private static @Nullable Step knob(Class<?> declared, @Nullable Object instance, String name) {
        for (Class<?> c = instance != null ? instance.getClass() : declared; c != null && c != Object.class; c = c.getSuperclass()) {
            Map<String, ConfigKnob> knobs = knobsOf(c);
            ConfigKnob knob = knobs != null ? knobs.get(name) : null;
            if (knob != null) return step(knob, instance);
        }
        return null;
    }

    private static Step step(ConfigKnob knob, @Nullable Object instance) {
        Object held = null;
        if (instance != null && readable(knob.valueType())) {
            Object field = knob.get().apply(instance);
            held = field instanceof FieldValue<?, ?> value ? value.constantOrNull() : field;
        }
        return new Step(knob.name(), knob.valueType() == null ? "code-only" : typeName(knob.valueType()), held, false);
    }

    /** A value worth printing back: a config object's {@code toString} is an address, and its type says more. */
    private static boolean readable(@Nullable Class<?> type) {
        return type != null && (type.isEnum() || type == Boolean.class || type == String.class
                || Number.class.isAssignableFrom(type));
    }

    private static String typeName(Class<?> type) {
        if (!type.isEnum()) return type.getSimpleName();
        StringBuilder names = new StringBuilder(type.getSimpleName()).append(" (");
        Object[] constants = type.getEnumConstants();
        for (int i = 0; i < constants.length; i++) {
            names.append(i == 0 ? "" : "|").append(((Enum<?>) constants[i]).name().toLowerCase(java.util.Locale.ROOT));
        }
        return names.append(')').toString();
    }

    private static boolean hasKnobs(Class<?> configClass) {
        for (Class<?> c = configClass; c != null && c != Object.class; c = c.getSuperclass()) {
            if (knobsOf(c) != null) return true;
        }
        return false;
    }

    private static Collection<String> keyNames(Collection<?> keys) {
        return keys.stream().map(k -> k instanceof Material material ? material.key().asString() : String.valueOf(k)).sorted().toList();
    }

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
            return e.getMessage() != null ? e.getMessage() : e.toString();
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
        Member member = parts.length > 0 ? MEMBERS.get(parts[0]) : null;
        if (member == null) {
            String head = parts.length > 0 ? parts[0] : "";
            throw new IllegalArgumentException("unknown member '" + head + "' in '" + path + "' (members: " + members() + ")");
        }
        @SuppressWarnings("unchecked")
        ConfigKey<Object> key = (ConfigKey<Object>) member.key();
        if (parts.length == 1) {
            if (!member.scalar()) {
                throw new IllegalArgumentException(parts[0] + " is not a whole-member value - paths are member/knob or member/key/knob: " + path);
            }
            noTargeting(who, path);
            b.set(key, decodeScalar(member.configClass(), rawValue, path));
            return;
        }
        List<String> rest = List.of(parts).subList(1, parts.length);
        if (who != null && member.editor() != null && member.targetable()) {
            // the whole member, edited off what everyone else reads, filed for these players only
            Object cur = b.get(key);
            Object base = cur != null ? cur : (fallback != null ? fallback.get(key) : null);
            b.target(key, who, member.editor().edit(base, rest, rawValue, path, null));
            return;
        }
        b.mutate(key, cur -> {
            Object base = cur != null ? cur : (fallback != null ? fallback.get(key) : null);
            if (member.editor() != null) return member.editor().edit(base, rest, rawValue, path, who);
            TypedFamily family = member.typed();
            if (parts.length == 2) {
                // a knob on the container itself, else the family's defaults entry
                if (family != null && family.defaults() != null && !hasKnob(member.configClass(), parts[1])) {
                    Object entry = editEntry(family.entryClass(), base != null ? family.defaults().apply(base) : null,
                            null, parts[1], rawValue, path, who);
                    return family.withDefaults().apply(base, entry);
                }
                return editFlat(member.configClass(), base, parts[1], rawValue, path, who);
            }
            if (parts.length == 3 && family != null) {
                Object entryKey = family.keyOf().apply(parts[1], path);
                Object baseEntry = base != null ? family.entry().apply(base, entryKey) : null;
                Object entry = editEntry(family.entryClass(), baseEntry, entryKey, parts[2], rawValue, path, who);
                return family.withEntry().apply(base, entryKey, entry);
            }
            throw new IllegalArgumentException(family == null
                    ? parts[0] + " has no entries - use " + parts[0] + "/<knob>: " + path
                    : "path must be member/knob or member/key/knob: " + path);
        });
    }

    private static void one(List<String> rest, String form, String path) {
        if (rest.size() != 1) throw new IllegalArgumentException("expected " + form + ": " + path);
    }

    private static void noTargeting(@Nullable Predicate<Player> who, String path) {
        if (who != null) throw new IllegalArgumentException("this member cannot vary per player: " + path);
    }

    private static int integer(String raw, String path) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + raw + "' is not an integer for " + path);
        }
    }

    private static double dbl(String raw, String path) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + raw + "' is not a number for " + path);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object decodeScalar(Class<?> type, String raw, String path) {
        if (type.isEnum()) {
            try {
                return Enum.valueOf((Class<? extends Enum>) type, raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("'" + raw + "' is not a " + type.getSimpleName() + " for " + path);
            }
        }
        return FieldFns.parse(type, raw, path);
    }

    private static boolean hasKnob(Class<?> configClass, String name) {
        for (Class<?> c = configClass; c != null && c != Object.class; c = c.getSuperclass()) {
            Map<String, ConfigKnob> knobs = knobsOf(c);
            if (knobs != null && knobs.containsKey(name)) return true;
        }
        return false;
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
        Object decoded = decode(knob, base, raw, path);
        if (who == null) return FieldValue.constant(decoded);
        if (!SubjectContext.class.isAssignableFrom(knob.contextType())) {
            throw new IllegalArgumentException("'" + knob.name() + "' resolves against " + knob.contextType().getSimpleName()
                    + ", which names no subject - it cannot vary per player: " + path);
        }
        @SuppressWarnings("unchecked")
        FieldValue<SubjectContext, Object> inherited = base != null ? (FieldValue<SubjectContext, Object>) knob.get().apply(base) : null;
        return FieldValue.targeted(who, FieldValue.constant(decoded), inherited);
    }

    private static Object editEntry(Class<?> defaultClass, @Nullable Object baseEntry, @Nullable Object typeKey,
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

    private static Object newBuilder(Class<?> configClass, @Nullable Object typeKey, String path) {
        Method m = BUILDER_FACTORIES.computeIfAbsent(configClass, c -> {
            // DECLARED only: getMethod also finds a superclass's static builder, whose Builder is the wrong line
            if (typeKey instanceof Key) {
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
    private static Object decode(ConfigKnob knob, @Nullable Object base, String raw, String path) {
        Class<?> t = knob.valueType();
        if (FieldFns.mutates(t, raw)) return FieldFns.mutate((Class) t, raw, inheritedConstant(knob, base, path), path);
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

    // what a mutation edits: the base's constant for the knob; a value that varies by subject has no one constant
    private static @Nullable Object inheritedConstant(ConfigKnob knob, @Nullable Object base, String path) {
        if (base == null) return null;
        Object held = knob.get().apply(base);
        if (held == null) return null;
        Object constant = ((FieldValue<?, ?>) held).constantOrNull();
        if (constant == null) {
            throw new IllegalArgumentException("'" + knob.name() + "' inherits a value that varies by subject - a mutation"
                    + " needs one constant to edit; set it whole instead: " + path);
        }
        return constant;
    }
}
