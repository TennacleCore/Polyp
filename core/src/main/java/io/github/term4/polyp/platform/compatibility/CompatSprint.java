package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.platform.compatibility.CompatConfig.SprintGate;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.GameMode;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.network.packet.server.play.EntityEffectPacket;
import net.minestom.server.network.packet.server.play.RemoveEntityEffectPacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Drives {@code CompatConfig.suppressSprint} and {@code suppressSwim}: one of the client's two wire-drivable sprint
 * gates is held shut, always for the first and while the water is deep enough for the swim pose for the second;
 * released the moment neither wants it. {@code FOOD} rides the {@code OptimizedPlayer} outgoing clamp (any server
 * food update stays clamped while held); {@code BLINDNESS} refreshes a hidden effect every tick
 * ({@code blindnessGateTicks} long - the default stays above the 20-tick fog saturation knee so the fog factor
 * can't sawtooth). All state lives on {@link CompatState}, so disconnect/relog cleanup is free. Installed once;
 * inert unless configured.
 */
public final class CompatSprint {

    private CompatSprint() {}

    public static void install(Polyp polyp) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:compat-sprint", EventFilter.PLAYER);
        node.addListener(PlayerTickEvent.class, e -> {
            if (e.getPlayer() instanceof OptimizedPlayer op) tick(op);
        });
        polyp.install(node);
    }

    static void tick(@NotNull OptimizedPlayer player) {
        CompatState compat = player.compat();
        boolean exempt = exempt(player);
        SprintGate swim = exempt ? null : swimWanted(player, compat);
        SprintGate always = exempt ? null : compat.suppressSprint();
        SprintGate want = always != null ? always : swim;
        compat.setSwimGated(swim != null);
        SprintGate active = compat.sprintGate();
        if (want != active) {
            compat.setSprintGate(want); // before the sends: the food clamp keys off it
            if (active == SprintGate.BLINDNESS && !player.hasEffect(PotionEffect.BLINDNESS)) {
                player.sendPacket(new RemoveEntityEffectPacket(player.getEntityId(), PotionEffect.BLINDNESS));
            }
            if (active == SprintGate.FOOD || want == SprintGate.FOOD) resendFood(player);
        }
        // a real blindness effect already gates sprint; riding it also keeps its client duration honest
        if (want == SprintGate.BLINDNESS && !player.hasEffect(PotionEffect.BLINDNESS)) {
            player.sendPacket(new EntityEffectPacket(player.getEntityId(),
                    new Potion(PotionEffect.BLINDNESS, 0, compat.blindnessGateTicks(), Potion.AMBIENT_FLAG)));
        }
        // the gate takes a round trip; a pose slipped in before it landed is forced back (isPoseDisabled covers later ones)
        if (swim != null && player.getPose() == EntityPose.SWIMMING) player.setPose(EntityPose.STANDING);
    }

    // mayfly bypasses the client's food gate anyway, and fogging a builder helps no one
    private static boolean exempt(OptimizedPlayer player) {
        if (player.isDead() || player.getInstance() == null) return true;
        GameMode gm = player.getGameMode();
        return gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR;
    }

    private static @Nullable SprintGate swimWanted(OptimizedPlayer player, CompatState compat) {
        SprintGate configured = compat.suppressSwim();
        if (configured == null) return null;
        return ClientEye.swimDepth(player, MechanicsWorld.viewed(player)) ? configured : null;
    }

    /** Through {@code sendPacket}, so the clamp applies exactly when a gate is held. */
    private static void resendFood(OptimizedPlayer player) {
        player.sendPacket(new UpdateHealthPacket(player.getHealth(), player.getFood(), player.getFoodSaturation()));
    }
}
