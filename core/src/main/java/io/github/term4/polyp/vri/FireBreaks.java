package io.github.term4.polyp.vri;

import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxContext;
import io.github.term4.polyp.world.FireSupport;
import io.github.term4.polyp.vri.VriConfig;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fire parity on block breaks - Minestom runs neither piece: breaking fire directly fizzes
 * ({@code BaseFireBlock.playerWillDestroy}, world event 1009), and fire the break leaves unsupported is removed
 * SILENTLY ({@link FireSupport}). The 1.8 face douse is a client fix ({@code LegacyFireDouseFix}).
 */
final class FireBreaks {

    private FireBreaks() {}

    static void install(EventNode<@NotNull Event> node, Vri vri) {
        // end of tick like the other break consumers: a later node still cancels the break
        node.addListener(PlayerBlockBreakEvent.class, e -> MinecraftServer.getSchedulerManager().scheduleEndOfTick(() -> {
            if (e.isCancelled() || !VriConfig.on(vri.configFor(e.getPlayer()).fireBreaks, e.getPlayer())) return;
            MechanicsWorld world = MechanicsWorld.of(e.getPlayer());
            if (FireSupport.isFire(e.getBlock())) {
                Fx.play(vri.services(), Fx.FIRE_DOUSE, FxContext.at(world, e.getBlockPosition(), e.getPlayer()));
            }
            FireSupport.sweep(world, e.getBlockPosition());
        }));
    }
}
