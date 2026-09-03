package io.github.term4.polyp.world;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.TimedPotion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Each instance owns the bodies of the players in it. A world registered with {@link #keep} stores a body when
 * the player leaves it and puts it back when they return; one registered with {@link #outfit} dresses every
 * arrival the same way (a lobby kit). A game engine leaves a body neutral on its exit; this is who fills it next.
 */
public final class Wardrobe {

    /** A stored body; a persistent world plugs its own {@link Store}. */
    public record Body(ItemStack[] inventory, GameMode gamemode, boolean allowFlying, boolean flying, float health,
                       int food, float saturation, float exp, int level, List<Potion> effects) {}

    public interface Store {
        @Nullable Body load(@NotNull Instance instance, @NotNull UUID player);
        void save(@NotNull Instance instance, @NotNull UUID player, @NotNull Body body);
    }

    private static final Map<Instance, Store> KEPT = new ConcurrentHashMap<>();
    private static final Map<Instance, Consumer<Player>> OUTFITS = new ConcurrentHashMap<>();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private Wardrobe() {}

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        var events = MinecraftServer.getGlobalEventHandler();
        events.addListener(RemoveEntityFromInstanceEvent.class, e -> {
            if (e.getEntity() instanceof Player p) leave(p, e.getInstance());
        });
        events.addListener(PlayerSpawnEvent.class, e -> arrive(e.getPlayer(), e.getSpawnInstance()));
    }

    /** {@code instance} keeps each player's body between visits, in memory. */
    public static void keep(@NotNull Instance instance) {
        keep(instance, new MemoryStore());
    }

    public static void keep(@NotNull Instance instance, @NotNull Store store) {
        KEPT.put(instance, store);
    }

    /** Every arrival at {@code instance} with no stored body gets {@code outfit}. */
    public static void outfit(@NotNull Instance instance, @NotNull Consumer<Player> outfit) {
        OUTFITS.put(instance, outfit);
    }

    public static void forget(@NotNull Instance instance) {
        KEPT.remove(instance);
        OUTFITS.remove(instance);
    }

    /** Dresses {@code player} as an arrival at {@code instance}: the stored body if one is kept, else the outfit.
     *  The spawn listener does this on every instance change; a route that moves nobody between instances calls it. */
    public static void arrive(@NotNull Player player, @NotNull Instance instance) {
        Store store = KEPT.get(instance);
        Body stored = store != null ? store.load(instance, player.getUuid()) : null;
        if (stored != null) {
            put(player, stored);
            return;
        }
        Consumer<Player> outfit = OUTFITS.get(instance);
        if (outfit != null) outfit.accept(player);
    }

    private static void leave(Player player, Instance instance) {
        Store store = KEPT.get(instance);
        if (store != null) store.save(instance, player.getUuid(), take(player));
    }

    static Body take(Player p) {
        var inv = p.getInventory();
        ItemStack[] items = new ItemStack[inv.getSize()];
        for (int i = 0; i < items.length; i++) items[i] = inv.getItemStack(i);
        List<Potion> effects = new ArrayList<>();
        long alive = p.getAliveTicks();
        for (TimedPotion timed : p.getActiveEffects()) {
            Potion potion = timed.potion();
            if (potion.duration() == Potion.INFINITE_DURATION) {
                effects.add(potion);
                continue;
            }
            long left = potion.duration() - (alive - timed.startingTicks());
            if (left > 0) effects.add(new Potion(potion.effect(), potion.amplifier(), (int) left, potion.flags()));
        }
        return new Body(items, p.getGameMode(), p.isAllowFlying(), p.isFlying(), p.getHealth(), p.getFood(),
                p.getFoodSaturation(), p.getExp(), p.getLevel(), effects);
    }

    static void put(Player p, Body b) {
        var inv = p.getInventory();
        inv.clear();
        for (int i = 0; i < b.inventory().length && i < inv.getSize(); i++) inv.setItemStack(i, b.inventory()[i]);
        p.setGameMode(b.gamemode());
        p.setAllowFlying(b.allowFlying());
        p.setFlying(b.flying() && b.allowFlying());
        p.setHealth(Math.max(1f, b.health()));
        p.setFood(b.food());
        p.setFoodSaturation(b.saturation());
        p.setExp(b.exp());
        p.setLevel(b.level());
        p.clearEffects();
        for (Potion effect : b.effects()) p.addEffect(effect);
    }

    private static final class MemoryStore implements Store {
        private final Map<UUID, Body> bodies = new ConcurrentHashMap<>();

        @Override
        public @Nullable Body load(@NotNull Instance instance, @NotNull UUID player) {
            return bodies.get(player);
        }

        @Override
        public void save(@NotNull Instance instance, @NotNull UUID player, @NotNull Body body) {
            bodies.put(player, body);
        }
    }
}
