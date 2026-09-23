package io.github.term4.polyp.vri;

import io.github.term4.polyp.api.event.item.ItemSpawnEvent;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.vri.VriConfig;
import io.github.term4.polyp.entity.DroppedItemEntity;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.inventory.TransactionOption;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns dropped items (Q / drag-out / cursor stack on inventory close) - Minestom fires {@link ItemDropEvent}
 * but spawns nothing. Vanilla 1.8 {@code EntityHuman.a}: eye - 0.3, thrown 0.3 b/t along the look, 40t pickup
 * delay. Container closes route here natively; the close listener adds the own-screen close Minestom misses.
 * No-take GUIs: cancel {@link ItemDropEvent} and reclaim the cursor in their own close listener.
 */
public final class ItemDrop {

    private static final int PICKUP_DELAY_TICKS = 40;
    private static final double THROW_SPEED = 0.3;

    private ItemDrop() {}

    public static void install(EventNode<@NotNull Event> node, Vri vri) {
        node.addListener(InventoryCloseEvent.class, e -> {
            // containers drop the cursor natively (removeViewer); only the player's own screen leaks it
            var inventory = e.getPlayer().getInventory();
            if (e.getInventory() != inventory) return;
            // a profile's PlayerConfig.cursorOnClose owns the close instead
            if (e.getPlayer() instanceof OptimizedPlayer op && op.inventorySync().cursorOnClose() != null) return;
            ItemStack cursor = inventory.getCursorItem();
            if (cursor.isAir()) return;
            inventory.setCursorItem(ItemStack.AIR);
            if (!VriConfig.on(vri.configFor(e.getPlayer()).itemDrop, e.getPlayer())) {
                // tossing is off, but the close may not strand the stack: what the slots cannot take stays on
                // the cursor for the next open, never on the floor
                inventory.setCursorItem(inventory.addItemStack(cursor, TransactionOption.ALL));
                inventory.update();
                return;
            }
            ItemDropEvent drop = new ItemDropEvent(e.getPlayer(), cursor);
            EventDispatcher.call(drop); // spawned by the listener below
            MinecraftServer.getSchedulerManager().scheduleEndOfTick(() -> {
                if (!drop.isCancelled()) return;
                inventory.setCursorItem(cursor); // the reclaim window for no-take GUIs
                MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                    // unclaimed on a closed screen is invisible limbo - back into the slots
                    if (e.getPlayer().getOpenInventory() == null && inventory.getCursorItem().equals(cursor)) {
                        inventory.setCursorItem(inventory.addItemStack(cursor, TransactionOption.ALL));
                        inventory.update();
                    }
                });
            });
        });
        // end-of-tick: a reactor reads the FINAL cancelled state, whatever order the listeners ran in
        node.addListener(ItemDropEvent.class, e ->
                MinecraftServer.getSchedulerManager().scheduleEndOfTick(() -> {
            if (e.isCancelled()) return;
            Player player = e.getPlayer();
            Instance instance = player.getInstance();
            VriConfig cfg = vri.configFor(player);
            if (instance == null || !VriConfig.on(cfg.itemDrop, player)) return;

            double yaw = Math.toRadians(player.getPosition().yaw()), pitch = Math.toRadians(player.getPosition().pitch());
            var rnd = ThreadLocalRandom.current();
            double vx = -Math.sin(yaw) * Math.cos(pitch) * THROW_SPEED;
            double vz = Math.cos(yaw) * Math.cos(pitch) * THROW_SPEED;
            double vy = -Math.sin(pitch) * THROW_SPEED + 0.1;
            double angle = rnd.nextFloat() * Math.PI * 2;
            double scatter = 0.02 * rnd.nextFloat();
            vx += Math.cos(angle) * scatter;
            vy += (rnd.nextFloat() - rnd.nextFloat()) * 0.1f;
            vz += Math.sin(angle) * scatter;

            DroppedItemEntity.spawn(instance,
                    player.getPosition().add(0, player.getEyeHeight() - 0.30000001192092896, 0),
                    new Vec(vx, vy, vz), e.getItemStack(), FieldValue.resolve(cfg.itemPhysics, new VriConfig.VriContext(player)),
                    PICKUP_DELAY_TICKS, ItemSpawnEvent.Cause.PLAYER_DROP, player);
        }));
    }
}
