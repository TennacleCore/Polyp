package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.platform.PacketShapes;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import net.minestom.server.tag.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * Whole-point health for the 1.8 heart bar (it ceils: 14.01 draws as 15 half-hearts); a hit that stays inside the
 * same displayed heart goes out a hair under the point so the client still flinches. Off by default: Hypixel and
 * MineMen send fractional health (captured). The viewer's own health only.
 */
public final class LegacyHealthRoundingFix {

    private static final Tag<Float> LAST_SENT = Tag.Transient("polyp:legacy-health-rounding-last");
    private static final int HEALTH_INDEX = MetadataDef.LivingEntity.HEALTH.index();

    private LegacyHealthRoundingFix() {}

    /** The same instance unless {@code apply} (legacy client + toggle) and the packet carries the viewer's health. */
    public static SendablePacket rewrite(Player viewer, boolean apply, SendablePacket packet) {
        if (!apply) return packet;
        ServerPacket server = PacketShapes.unwrapStateless(packet);
        if (server instanceof UpdateHealthPacket uh) {
            return new UpdateHealthPacket(next(viewer, uh.health()), uh.food(), uh.foodSaturation());
        }
        if (server instanceof EntityMetaDataPacket meta && meta.entityId() == viewer.getEntityId()) {
            Metadata.Entry<?> entry = meta.entries().get(HEALTH_INDEX);
            if (entry != null && entry.value() instanceof Float health) {
                Map<Integer, Metadata.Entry<?>> entries = new HashMap<>(meta.entries());
                entries.put(HEALTH_INDEX, Metadata.Float(next(viewer, health)));
                return new EntityMetaDataPacket(meta.entityId(), entries);
            }
        }
        return packet;
    }

    private static float next(Player viewer, float health) {
        Float previous = viewer.getTag(LAST_SENT);
        viewer.setTag(LAST_SENT, health);
        return round(previous != null ? previous : Float.NaN, health);
    }

    /** The whole-point health for {@code current}; {@code previous} is the last real value sent, or NaN for none. */
    public static float round(float previous, float current) {
        if (current <= 0) return 0;
        float rounded = Math.max(1, Math.round(current));
        boolean sameHeartDamage = !Float.isNaN(previous) && current < previous && Math.round(previous) == rounded;
        return sameHeartDamage ? Math.nextAfter(rounded, 0) : rounded;
    }
}
