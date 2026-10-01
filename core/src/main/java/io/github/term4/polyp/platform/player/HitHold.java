package io.github.term4.polyp.platform.player;

import net.minestom.server.entity.Player;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket;
import net.minestom.server.network.packet.server.play.DamageEventPacket;
import net.minestom.server.network.packet.server.play.DeathCombatEventPacket;
import net.minestom.server.network.packet.server.play.EntityAnimationPacket;
import net.minestom.server.network.packet.server.play.EntityStatusPacket;
import net.minestom.server.network.packet.server.play.EntityVelocityPacket;
import net.minestom.server.network.packet.server.play.PlayerPositionAndLookPacket;
import net.minestom.server.network.packet.server.play.RespawnPacket;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Hypixel's melee hit (captured 2026-10-01): viewers see the hurt flash and crits at once, and every other packet the
 * hit makes leaves when the victim's next movement packet arrives, from the read thread, so the victim takes the
 * knockback one round trip after sending it.
 */
public final class HitHold {

    private static final ThreadLocal<HitHold> OPEN = new ThreadLocal<>();
    // a 1.9+ client sends nothing while it stands still
    private static final int CAP_TICKS = 2;

    private record Held(@Nullable PolypPlayer to, @Nullable SendablePacket packet, @Nullable Runnable send) {}

    private final PolypPlayer victim;
    private final List<Held> opening = new ArrayList<>(); // the hit in progress, tick thread only
    private final List<Held> armed = new ArrayList<>();
    private boolean spoiled;
    private boolean dropOwnVelocity;

    HitHold(PolypPlayer victim) {
        this.victim = victim;
    }

    /** Runs {@code hit} with its packets held for {@code victim}'s next movement packet. */
    public static void hold(@NotNull PolypPlayer victim, @NotNull Runnable hit) {
        HitHold hold = victim.hitHold();
        HitHold outer = OPEN.get();
        OPEN.set(hold);
        try {
            hit.run();
        } finally {
            OPEN.set(outer);
            hold.seal();
        }
    }

    /** Whether a hit on {@code victim} is being held right now. */
    public static boolean holding(@NotNull Player victim) {
        HitHold hold = OPEN.get();
        return hold != null && hold.victim == victim && !hold.spoiled;
    }

    /** Sends {@code send} with the held hit when {@code victim}'s is open; {@code false}: nothing is held, send it now. */
    public static boolean defer(@NotNull Player victim, @NotNull Runnable send) {
        if (!holding(victim)) return false;
        OPEN.get().opening.add(new Held(null, null, send));
        return true;
    }

    /** The victim's velocity goes out another way (the exact 1.8 short): its own velocity packet goes nowhere. */
    public static void dropOwnVelocity(@NotNull Player victim) {
        if (holding(victim)) OPEN.get().dropOwnVelocity = true;
    }

    /** {@code true} when {@code packet} for {@code to} joined the open hold instead of leaving now. */
    static boolean capture(@NotNull PolypPlayer to, @NotNull SendablePacket packet) {
        HitHold hold = OPEN.get();
        if (hold == null || hold.spoiled) return false;
        ServerPacket p = SendablePacket.extractServerPacket(ConnectionState.PLAY, packet);
        if (to == hold.victim) {
            if (p instanceof PlayerPositionAndLookPacket || p instanceof RespawnPacket
                    || p instanceof ChangeGameStatePacket || p instanceof DeathCombatEventPacket) {
                // the hit became a death or a teleport: what it held goes first, then everything as it comes
                hold.spoiled = true;
                deliver(new ArrayList<>(hold.opening));
                hold.opening.clear();
                return false;
            }
            if (hold.dropOwnVelocity && p instanceof EntityVelocityPacket v && v.entityId() == to.getEntityId()) return true;
        } else if (flash(hold.victim, p)) {
            return false;
        }
        hold.opening.add(new Held(to, packet, null));
        return true;
    }

    // what viewers see the moment the hit lands
    private static boolean flash(Player victim, ServerPacket p) {
        int id = victim.getEntityId();
        return p instanceof DamageEventPacket d && d.targetEntityId() == id
                || p instanceof EntityAnimationPacket a && a.entityId() == id
                || p instanceof EntityStatusPacket s && s.entityId() == id && s.status() == 2;
    }

    /** A movement packet from the victim arrived (the read thread): a sealed hit leaves now. */
    void moved() {
        release();
    }

    private void seal() {
        List<Held> batch = new ArrayList<>(opening);
        boolean now = spoiled || victim.isDead() || victim.isRemoved();
        opening.clear();
        spoiled = false;
        dropOwnVelocity = false;
        if (batch.isEmpty()) return;
        if (now) {
            deliver(batch);
            return;
        }
        synchronized (this) {
            armed.addAll(batch);
        }
        victim.scheduler().buildTask(this::release).delay(TaskSchedule.tick(CAP_TICKS)).schedule();
    }

    private void release() {
        List<Held> batch;
        synchronized (this) {
            if (armed.isEmpty()) return;
            batch = new ArrayList<>(armed);
            armed.clear();
        }
        deliver(batch);
    }

    private static void deliver(List<Held> batch) {
        for (Held held : batch) {
            if (held.send() != null) held.send().run();
            else held.to().getPlayerConnection().sendPacket(held.packet());
        }
    }
}
