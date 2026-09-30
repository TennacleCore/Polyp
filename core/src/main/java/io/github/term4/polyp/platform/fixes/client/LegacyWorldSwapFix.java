package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.platform.fixes.FixToggleConfig;
import io.github.term4.polyp.platform.fixes.FixesSystem;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.AddEntityToInstanceEvent;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.SharedInstance;
import net.minestom.server.network.packet.server.play.EntityEffectPacket;
import net.minestom.server.network.packet.server.play.HeldItemChangePacket;
import net.minestom.server.network.packet.server.play.PlayerAbilitiesPacket;
import net.minestom.server.network.packet.server.play.RespawnPacket;
import net.minestom.server.network.packet.server.play.SetExperiencePacket;
import net.minestom.server.network.packet.server.play.UpdateHealthPacket;
import net.minestom.server.network.packet.server.play.data.PlayerSpawnInfo;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.tag.Tag;
import net.minestom.server.world.DimensionType;
import org.jetbrains.annotations.NotNull;

/**
 * Minestom sends no respawn for an instance change within one dimension, and a client before 1.16 clears its world
 * only on a dimension change: it keeps the old chunk objects and their render state, and the new world's entities
 * file into chunks about to be replaced. CraftBukkit ({@code moveToWorld}) and Bungee hop through another dimension
 * first; so does this, before the new world's first spawn. The client rebuilds its player on a respawn, so the state
 * the server keeps for it goes out again.
 */
public final class LegacyWorldSwapFix {
    private LegacyWorldSwapFix() {}

    private static final Tag<Instance> SWAPPING = Tag.Transient("polyp:legacy-world-swap");
    private static final int SEA_LEVEL = 63;

    public static void install(EventNode<@NotNull Event> node, FixesSystem fixes) {
        node.addListener(AddEntityToInstanceEvent.class, e -> {
            if (!(e.getEntity() instanceof Player player) || e.isCancelled()) return;
            Instance from = player.getInstance();
            Instance to = e.getInstance();
            if (from == null || from == to || SharedInstance.areLinked(from, to)) return;
            if (!from.getDimensionName().equals(to.getDimensionName())) return; // Minestom respawns across dimensions itself
            FixToggleConfig cfg = fixes.forClient(player).legacyWorldSwap();
            if (cfg != null && cfg.enabled(player)) player.setTag(SWAPPING, to);
        });
        // past every add listener, so a cancelled add never costs the client its world
        node.addListener(RemoveEntityFromInstanceEvent.class, e -> {
            if (!(e.getEntity() instanceof Player player)) return;
            Instance to = player.getTag(SWAPPING);
            if (to == null) return;
            player.removeTag(SWAPPING);
            hop(player, to);
        });
    }

    private static void hop(Player player, Instance to) {
        RegistryKey<DimensionType> away = to.getDimensionType().equals(DimensionType.THE_NETHER)
                ? DimensionType.OVERWORLD : DimensionType.THE_NETHER;
        player.sendPacket(respawn(player, away, away.key().asString()));
        player.sendPacket(respawn(player, to.getDimensionType(), to.getDimensionName()));
        player.sendPacket(new UpdateHealthPacket(player.getHealth(), player.getFood(), player.getFoodSaturation()));
        player.sendPacket(new SetExperiencePacket(player.getExp(), player.getLevel(), 0));
        player.sendPacket(abilities(player));
        player.sendPacket(new HeldItemChangePacket(player.getHeldSlot()));
        player.getInventory().update();
        for (TimedPotion effect : player.getActiveEffects()) {
            player.sendPacket(new EntityEffectPacket(player.getEntityId(), effect.potion()));
        }
    }

    private static RespawnPacket respawn(Player player, RegistryKey<DimensionType> type, String world) {
        int id = MinecraftServer.getDimensionTypeRegistry().getId(type);
        return new RespawnPacket(new PlayerSpawnInfo(id, world, 0, player.getGameMode(), player.getGameMode(),
                false, true, player.getDeathLocation(), player.getPortalCooldown(), SEA_LEVEL),
                (byte) RespawnPacket.COPY_ALL);
    }

    private static PlayerAbilitiesPacket abilities(Player player) {
        byte flags = 0;
        if (player.isInvulnerable()) flags |= PlayerAbilitiesPacket.FLAG_INVULNERABLE;
        if (player.isFlying()) flags |= PlayerAbilitiesPacket.FLAG_FLYING;
        if (player.isAllowFlying()) flags |= PlayerAbilitiesPacket.FLAG_ALLOW_FLYING;
        if (player.isInstantBreak()) flags |= PlayerAbilitiesPacket.FLAG_INSTANT_BREAK;
        return new PlayerAbilitiesPacket(flags, player.getFlyingSpeed(), player.getFieldViewModifier());
    }
}
