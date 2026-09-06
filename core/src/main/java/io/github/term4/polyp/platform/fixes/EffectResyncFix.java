package io.github.term4.polyp.platform.fixes;

import io.github.term4.polyp.platform.PacketShapes;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.EntityEffectPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import net.minestom.server.potion.TimedPotion;
import org.jetbrains.annotations.NotNull;

/**
 * The effects a new viewer is owed. Minestom sends an effect packet to whoever is watching when the effect lands
 * and to nobody after: a viewer who arrives later gets metadata, equipment and attributes, never the effects.
 * Vanilla re-sends them as tracking starts, and a client reads real behavior off them - a swing's length is the
 * swinger's own haste or fatigue - so an entity a viewer met mid-effect swings at the wrong speed for them.
 * Rides the send override, so it gates on the install config and cannot vary per scope.
 */
public final class EffectResyncFix {

    private static volatile boolean enabled;

    private EffectResyncFix() {}

    public static void install() {
        enabled = true;
    }

    /** Follows a spawn the viewer was just sent with the effects that entity already carries. */
    public static void afterSpawn(@NotNull Player viewer, @NotNull SendablePacket packet) {
        if (!enabled) return;
        ServerPacket server = PacketShapes.unwrapStateless(packet);
        if (!(server instanceof SpawnEntityPacket spawn)) return;
        Instance instance = viewer.getInstance();
        if (instance == null) return;
        Entity subject = instance.getEntityById(spawn.entityId());
        if (!(subject instanceof LivingEntity living)) return;
        for (TimedPotion timed : living.getActiveEffects()) {
            viewer.sendPacket(new EntityEffectPacket(subject.getEntityId(), timed.potion()));
        }
    }
}
