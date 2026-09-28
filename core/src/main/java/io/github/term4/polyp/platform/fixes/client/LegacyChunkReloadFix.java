package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.platform.fixes.FixToggleConfig;
import io.github.term4.polyp.platform.fixes.FixesSystem;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerChunkLoadEvent;
import net.minestom.server.instance.EntityTracker;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A client through 1.13 files an entity into the chunk OBJECT it stands in, and a chunk it already holds arrives
 * as a new object ({@code ChunkProviderClient.loadChunk}): the entity keeps rendering off the loaded list, but the
 * crosshair walks the new chunk's lists, so it cannot be hit or used until something spawns it afresh. Minestom
 * re-sends the whole view on a same-world respawn; vanilla 1.8 destroyed tracked entities first and spawned each
 * behind its chunk ({@code EntityTrackerEntry.isPlayerWatchingThisChunk}). Restored here: a chunk sent to such a
 * client is followed by a destroy and a fresh spawn of every entity it views in it.
 */
public final class LegacyChunkReloadFix {
    private LegacyChunkReloadFix() {}

    public static void install(EventNode<@NotNull Event> node, FixesSystem fixes) {
        node.addListener(PlayerChunkLoadEvent.class, e -> {
            Player viewer = e.getPlayer();
            FixToggleConfig cfg = fixes.forClient(viewer).legacyChunkReload();
            if (cfg == null || !cfg.enabled(viewer)) return;
            Instance instance = viewer.getInstance();
            if (instance == null) return;
            respawn(viewer, instance.getEntityTracker().chunkEntities(e.getChunkX(), e.getChunkZ(), EntityTracker.Target.ENTITIES));
        });
    }

    // packet-level only: the viewer sets, and everything keyed on them, stay as they are
    private static void respawn(Player viewer, Collection<Entity> standing) {
        List<Entity> seen = new ArrayList<>();
        for (Entity entity : standing) {
            if (entity != viewer && entity != viewer.getVehicle() && entity.isViewer(viewer)) seen.add(entity);
        }
        if (seen.isEmpty()) return;
        for (Entity entity : seen) {
            entity.updateOldViewer(viewer);
            entity.updateNewViewer(viewer);
        }
        // a fresh spawn seats nobody: each vehicle's passenger list goes out again once every rider is back
        Set<Entity> vehicles = new LinkedHashSet<>();
        for (Entity entity : seen) {
            if (entity.hasPassenger()) vehicles.add(entity);
            Entity vehicle = entity.getVehicle();
            if (vehicle != null && (vehicle == viewer || vehicle.isViewer(viewer))) vehicles.add(vehicle);
        }
        for (Entity vehicle : vehicles) {
            List<Integer> riders = vehicle.getPassengers().stream()
                    .filter(rider -> rider == viewer || rider.isViewer(viewer))
                    .map(Entity::getEntityId).toList();
            if (!riders.isEmpty()) viewer.sendPacket(new SetPassengersPacket(vehicle.getEntityId(), riders));
        }
    }
}
