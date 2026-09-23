package io.github.term4.polyp.tracking;

import net.minestom.server.MinecraftServer;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.platform.player.PlayerConfigApplier;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Inbound plugin-message hub: protocol detection plus routing of other channels to registered client-mod handlers.
 * Detection is source-agnostic - ViaVersion's connection-details message (the Via on a Velocity proxy sends it;
 * ViaProxy never does) is one producer (toggleable), and
 * {@link #setProtocol} lets the app mark a player from any source of truth (a custom proxy plugin, a whitelist);
 * a mark outranks the Via message. Per-player info lives in a transient tag, so it dies with the player object.
 */
public final class ClientInfoTracker implements Tracker {

    /** ViaVersion's connection-details channel: {@code {"version": <protocol>, ...}} JSON from the Via on a Velocity proxy. */
    public static final String VIA_CONNECTION_DETAILS_CHANNEL = "vv:proxy_details";

    private static final Tag<ClientInfo> CLIENT_INFO = Tag.Transient("polyp:client-info");

    private final boolean handleConnectionDetails;
    private final Map<String, BiConsumer<Player, byte[]>> modHandlers = new java.util.concurrent.ConcurrentHashMap<>();

    public ClientInfoTracker(boolean handleConnectionDetails) {
        this.handleConnectionDetails = handleConnectionDetails;
    }

    private static final class ClientInfo {
        String connectionDetails;
        Integer cachedProtocol;
        Integer markedProtocol;
    }

    /** Handler runs on the network thread with the raw payload; register at install, before any player joins. */
    public void onPluginMessage(@NotNull String channel, @NotNull BiConsumer<Player, byte[]> handler) {
        modHandlers.put(channel, handler);
    }

    @Override
    public EventNode<@NotNull PlayerEvent> node() {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:client-info-tracker", EventFilter.PLAYER);
        // Fabric gates ClientPlayNetworking#canSend on minecraft:register, so a well-behaved mod never handshakes without it
        node.addListener(PlayerSpawnEvent.class, e -> {
            if (!e.isFirstSpawn() || modHandlers.isEmpty()) return;
            e.getPlayer().sendPluginMessage("minecraft:register",
                    String.join("\0", modHandlers.keySet()).getBytes(StandardCharsets.UTF_8));
        });
        node.addListener(PlayerPluginMessageEvent.class, e -> {
            String channel = e.getIdentifier();
            byte[] data = e.getMessage();
            if (handleConnectionDetails && VIA_CONNECTION_DETAILS_CHANNEL.equals(channel)) {
                if (data.length != 0) setConnectionDetails(e.getPlayer(), new String(data, StandardCharsets.UTF_8));
                return;
            }
            BiConsumer<Player, byte[]> handler = modHandlers.get(channel);
            if (handler != null) handler.accept(e.getPlayer(), data);
        });
        return node;
    }

    public void setConnectionDetails(Player player, String connectionDetails) {
        ClientInfo info = infoOf(player);
        info.connectionDetails = connectionDetails;
        info.cachedProtocol = null;
        rescopeCompat(player);
    }

    /**
     * Marks {@code player}'s protocol from the app's own source of truth (a re-terminating proxy like ViaProxy hides
     * the real client, so only a side channel can know it). A mark outranks the Via details message and syncs the
     * legacy-compat state both ways; {@link ClientVersion#UNKNOWN_PROTOCOL} clears it back to detection.
     */
    public void setProtocol(Player player, int protocolVersion) {
        ClientInfo info = infoOf(player);
        info.markedProtocol = protocolVersion == ClientVersion.UNKNOWN_PROTOCOL ? null : protocolVersion;
        rescopeCompat(player);
    }

    // the item stamps each have a protocol floor, and the join inventory went out under the unknown-protocol guess
    private void rescopeCompat(Player player) {
        if (!(player instanceof OptimizedPlayer op)) return;
        List<Object> view = op.compat().itemViewKey();
        op.compat().setLegacyClient(ClientVersion.isLegacy(getProtocol(player)));
        if (!view.equals(op.compat().itemViewKey())) PlayerConfigApplier.resendItemView(op);
    }

    private static ClientInfo infoOf(Player player) {
        ClientInfo info = player.getTag(CLIENT_INFO);
        if (info == null) {
            info = new ClientInfo();
            player.setTag(CLIENT_INFO, info);
        }
        return info;
    }

    public @Nullable String getConnectionDetails(Player player) {
        ClientInfo info = player.getTag(CLIENT_INFO);
        return info == null ? null : info.connectionDetails;
    }

    /** What a player reads as while their own protocol is unknown; see {@link #protocolWhenUnknown}. */
    private volatile int whenUnknown = MinecraftServer.PROTOCOL_VERSION;

    /**
     * The protocol a player reads as while theirs has not arrived - the server's own build (so, modern) unless the
     * app says otherwise. Each reader picks its own: a wrong guess at modern only mis-renders until the resync,
     * a wrong guess at legacy can send a modern client 1.8 wire tricks it draws literally.
     */
    public void protocolWhenUnknown(int protocol) {
        this.whenUnknown = protocol;
    }

    /** {@link #getProtocol}, or {@link #protocolWhenUnknown} while the player's own is not known yet. */
    public int protocolOrAssumed(Player player) {
        int protocol = getProtocol(player);
        return protocol == ClientVersion.UNKNOWN_PROTOCOL ? whenUnknown : protocol;
    }

    /**
     * {@link ClientVersion#UNKNOWN_PROTOCOL} until the connection-details message arrives - it lands shortly after login, not
     * during the join events. {@link #protocolOrAssumed} answers for that window instead.
     */
    public int getProtocol(Player player) {
        ClientInfo info = player.getTag(CLIENT_INFO);
        if (info == null) return ClientVersion.UNKNOWN_PROTOCOL;
        if (info.markedProtocol != null) return info.markedProtocol;
        if (info.cachedProtocol != null) return info.cachedProtocol;

        int parsed = ClientVersion.parse(info.connectionDetails);
        if (parsed != ClientVersion.UNKNOWN_PROTOCOL) info.cachedProtocol = parsed;
        return parsed;
    }

    public ClientProfile of(Player player) {
        return new ClientProfile(player, this);
    }

    /** Reads the unknown window as {@link #protocolWhenUnknown}; {@link #getProtocol} distinguishes it. */
    public boolean isLegacy(Player player) {
        return ClientVersion.isLegacy(protocolOrAssumed(player));
    }
}
