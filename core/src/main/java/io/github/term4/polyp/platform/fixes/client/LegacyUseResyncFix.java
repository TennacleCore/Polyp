package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.platform.fixes.FixToggleConfig;
import io.github.term4.polyp.platform.fixes.FixesSystem;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A 1.8 client that releases a draw which fires nothing - a bow under {@code MIN_POWER}, a launch a rule refused,
 * an arrowless shot - keeps believing it is drawing, and refuses to draw again until something re-baselines its
 * inventory. Opening and closing it is the player's own workaround; this is that refresh, sent for them.
 *
 * <p>End of tick, so a shot that DID fire has written its arrow first. A 1.8 client cannot start another use in
 * the same tick anyway ({@code rightClickDelayTimer} is four), so the refresh never lands on a live draw.
 */
public final class LegacyUseResyncFix {

    private LegacyUseResyncFix() {}

    public static void install(EventNode<@NotNull Event> node, FixesSystem fixes) {
        node.addListener(PlayerCancelItemUseEvent.class, e -> {
            Player player = e.getPlayer();
            if (!(player instanceof OptimizedPlayer op) || !op.compat().legacyClient()) return;
            FixToggleConfig cfg = fixes.configFor(player).legacyUseResync();
            if (cfg == null || !cfg.enabled(player)) return;
            MinecraftServer.getSchedulerManager().scheduleEndOfTick(() -> {
                if (player.isOnline()) player.getInventory().update(player);
            });
        });
    }
}
