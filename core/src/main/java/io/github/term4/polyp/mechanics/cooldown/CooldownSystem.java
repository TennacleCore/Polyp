package io.github.term4.polyp.mechanics.cooldown;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsModule;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.util.tick.TickScaler;
import io.github.term4.polyp.util.tick.TickSystem;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Player;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.SetCooldownPacket;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;

/**
 * Server-authoritative item-use cooldowns. Vanilla's are client-predicted only (the {@code use_cooldown} component) -
 * the server happily honors a use packet from a client that ignores its own overlay. This system is the authority:
 * consumers gate on {@link #isOnCooldown} or {@link #tryUse} and {@link #arm} once the use actually lands,
 * which also sends the client overlay ({@code set_cooldown}). Legacy 1.8 clients have no cooldown wire - enforcement still applies, the overlay just
 * doesn't render.
 */
public final class CooldownSystem implements MechanicsModule {

    public static final Key KEY = Key.key("polyp:cooldowns");

    /** Per-player active cooldowns: cooldown group -> expiry on the player's combat clock. */
    private static final Tag<Map<String, Long>> ACTIVE = Tag.Transient("polyp:cooldowns");

    private final Polyp polyp;
    private final @Nullable CooldownConfig config;

    private CooldownSystem(Polyp polyp, @Nullable CooldownConfig config) {
        this.polyp = polyp;
        this.config = config;
    }

    /** Installs with no install-level config: cooldowns come from the profile scope ({@code MechanicsKeys.COOLDOWNS}). */
    public static CooldownSystem install(Polyp polyp) {
        return install(polyp, null);
    }

    public static CooldownSystem install(Polyp polyp, @Nullable CooldownConfig config) {
        CooldownSystem system = new CooldownSystem(polyp, config);
        if (CLOCK_RESET.compareAndSet(false, true)) {
            // no future-guard on the expiry: against a new clock a stale stamp never expires
            TickSystem.onClockChange(e -> { if (e instanceof Player p) p.removeTag(ACTIVE); });
        }
        return polyp.installModule(system);
    }

    private static final AtomicBoolean CLOCK_RESET = new AtomicBoolean();

    public boolean isOnCooldown(Player player, Material material) {
        Map<String, Long> active = player.getTag(ACTIVE);
        if (active == null) return false;
        Long until = active.get(material.key().asString());
        return until != null && TickSystem.tick(player) < until;
    }

    /**
     * Gates a use: {@code false} while on cooldown, else arms the cooldown (server clock, TPS-scaled) + sends the
     * client overlay. No config for the material = always allowed.
     */
    public boolean tryUse(Player player, Material material) {
        Integer ticks = ticks(player, material);
        if (ticks == null) return true;
        if (isOnCooldown(player, material)) return false;
        arm(player, material, ticks);
        return true;
    }

    /** Arms the cooldown without gating on it - for a use that only knows it went through after the fact. */
    public void arm(Player player, Material material) {
        Integer ticks = ticks(player, material);
        if (ticks != null) arm(player, material, ticks);
    }

    private void arm(Player player, Material material, int ticks) {
        String group = material.key().asString();
        Map<String, Long> active = player.getTag(ACTIVE);
        if (active == null) player.setTag(ACTIVE, active = new HashMap<>());
        active.put(group, TickSystem.tick(player)
                + TickScaler.duration(ticks, polyp.profiles().resolve(player, MechanicsKeys.TICK_SCALING), KEY));
        // the overlay counts on the CLIENT's clock, so it gets the unscaled vanilla ticks (same real time)
        player.sendPacket(new SetCooldownPacket(group, ticks));
    }

    private @Nullable Integer ticks(Player player, Material material) {
        CooldownConfig cfg = polyp.profiles().resolve(player, MechanicsKeys.COOLDOWNS);
        if (cfg == null) cfg = config;
        Integer ticks = cfg != null ? cfg.ticks(material) : null;
        return ticks == null || ticks <= 0 ? null : ticks;
    }
}
