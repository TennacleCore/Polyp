package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.tracking.ClientVersion;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Backend RPC to the ViaBridge Velocity plugin ({@value #CHANNEL}). Player version via {@code vv:proxy_details}.
 * Whether the bridge answers for a player is settled by one ping at login ({@link #available}), so no hot path
 * ever waits out the timeout to learn it.
 */
public final class ViaBridgeRpc {

    public static final String CHANNEL = "viabridge:rpc";
    /** {@code ClientboundPackets1_21_6} - shorts-format {@code SET_ENTITY_MOTION}. */
    public static final int PROTOCOL_1_21_6 = 771;
    public static final int PROTOCOL_1_8 = 47;
    public static final String PACKETS_1_8 = "ClientboundPackets1_8";

    private static final int WIRE_VERSION = 1;
    private static final int OPCODE_PING = 0;
    private static final int OPCODE_SEND_CLIENTBOUND = 1;
    private static final int OPCODE_TRANSFORM_CLIENTBOUND = 5;
    private static final byte STATUS_OK = 0;
    private static final byte STATUS_CANCELLED = 1;
    private static final long DEFAULT_TIMEOUT_MS = 2_000;
    private static final int PROBE_INTERVAL_TICKS = 5;
    private static final int PROBE_TIMEOUT_TICKS = 100;

    private static volatile ViaBridgeRpc instance;

    private enum Availability { AVAILABLE, UNAVAILABLE }

    private record Pending(CompletableFuture<Response> future, UUID playerId) {}
    private record Response(int opcode, byte[] payload) {}
    private record Status(byte code, String message, byte[] body) {}

    private final Map<UUID, Availability> availability = new ConcurrentHashMap<>();

    private final AtomicInteger nextRequestId = new AtomicInteger(1);
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private final long timeoutMs;

    private ViaBridgeRpc(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public static void install(@NotNull Polyp polyp) {
        install(polyp, DEFAULT_TIMEOUT_MS);
    }

    public static void install(@NotNull Polyp polyp, long timeoutMs) {
        if (instance != null) return;
        synchronized (ViaBridgeRpc.class) {
            if (instance != null) return;
            instance = new ViaBridgeRpc(timeoutMs);
            EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:viabridge-rpc", EventFilter.PLAYER);
            node.addListener(PlayerPluginMessageEvent.class, instance::onPluginMessage);
            node.addListener(PlayerDisconnectEvent.class, e -> {
                instance.failPending(e.getPlayer(), "disconnect");
                instance.availability.remove(e.getPlayer().getUuid());
            });
            node.addListener(PlayerSpawnEvent.class, e -> {
                if (e.isFirstSpawn()) instance.probe(polyp, e.getPlayer());
            });
            polyp.install(node);
        }
    }

    /** The bridge answered this player's login ping; {@code false} until it has, and for every modern client. */
    public static boolean available(@NotNull Player player) {
        ViaBridgeRpc rpc = instance;
        return rpc != null && rpc.availability.get(player.getUuid()) == Availability.AVAILABLE;
    }

    // only legacy clients route through the bridge; the protocol lands with Via's proxy-details message, after the join events
    private void probe(Polyp polyp, Player player) {
        int[] waited = {0};
        MinecraftServer.getSchedulerManager().scheduleTask(() -> {
            if (!player.isOnline()) return TaskSchedule.stop();
            if (polyp.clientInfo().getProtocol(player) == ClientVersion.UNKNOWN_PROTOCOL) {
                return (waited[0] += PROBE_INTERVAL_TICKS) < PROBE_TIMEOUT_TICKS
                        ? TaskSchedule.tick(PROBE_INTERVAL_TICKS) : TaskSchedule.stop();
            }
            if (polyp.clientInfo().isLegacy(player)) ping(player, new byte[0]);
            return TaskSchedule.stop();
        }, TaskSchedule.tick(PROBE_INTERVAL_TICKS));
    }

    public static @NotNull ViaBridgeRpc get() {
        ViaBridgeRpc rpc = instance;
        if (rpc == null) throw new IllegalStateException("ViaBridgeRpc not installed");
        return rpc;
    }

    /** Whether the backend RPC is installed (the player provider is on). */
    public static boolean isInstalled() { return instance != null; }

    public @NotNull CompletableFuture<byte[]> ping(@NotNull Player player, @NotNull byte[] echo) {
        return send(player, OPCODE_PING, echo).thenApply(Response::payload);
    }

    public @NotNull CompletableFuture<Void> sendEntityMotion(
            @NotNull Player player,
            int inputProtocolId,
            int entityId,
            short vx,
            short vy,
            short vz
    ) {
        return sendClientbound(player, inputProtocolId, "ClientboundPackets1_21_6", "SET_ENTITY_MOTION",
                encodeEntityMotionBody(entityId, vx, vy, vz));
    }

    /** {@code body} = the packet's fields as {@code inputProtocolId} writes them (no id); the proxy translates down to the client. */
    public @NotNull CompletableFuture<Void> sendClientbound(@NotNull Player player, int inputProtocolId,
                                                           @NotNull String packetClass, @NotNull String packetType,
                                                           byte[] body) {
        byte[] payload = encodeSendClientbound(inputProtocolId, packetClass, packetType, body);
        return send(player, OPCODE_SEND_CLIENTBOUND, payload)
                .thenApply(response -> {
                    checked(response, "SEND_CLIENTBOUND");
                    return null;
                });
    }

    /** The same packet as the client's own version would read it: packet id first, then the translated fields. */
    public @NotNull CompletableFuture<byte[]> transformClientbound(@NotNull Player player, int inputProtocolId,
                                                                  @NotNull String packetClass, @NotNull String packetType,
                                                                  byte[] body) {
        byte[] payload = encodeSendClientbound(inputProtocolId, packetClass, packetType, body);
        return send(player, OPCODE_TRANSFORM_CLIENTBOUND, payload)
                .thenApply(response -> checked(response, "TRANSFORM_CLIENTBOUND").body());
    }

    private static Status checked(Response response, String what) {
        Status status = decodeStatus(response.payload());
        if (status.code == STATUS_OK) return status;
        if (status.code == STATUS_CANCELLED) throw new ViaBridgeException("packet cancelled by Via pipeline");
        throw new ViaBridgeException(status.message != null ? status.message : what + " failed (status=" + status.code + ")");
    }

    private @NotNull CompletableFuture<Response> send(@NotNull Player player, int opcode, byte[] payload) {
        int requestId = nextId();
        CompletableFuture<Response> future = new CompletableFuture<>();
        pending.put(requestId, new Pending(future, player.getUuid()));
        player.sendPluginMessage(CHANNEL, encodeFrame(WIRE_VERSION, requestId, opcode, payload));
        // own timeout, not orTimeout: that completes the future FIRST, making a replacement exception a no-op
        CompletableFuture.delayedExecutor(timeoutMs, TimeUnit.MILLISECONDS).execute(() ->
                future.completeExceptionally(new ViaBridgeException(
                        "ViaBridge RPC timed out - is the Velocity plugin installed?")));
        future.whenComplete((ignored, err) -> {
            pending.remove(requestId);
            if (err == null) availability.put(player.getUuid(), Availability.AVAILABLE);
            else if (err instanceof ViaBridgeException && player.isOnline()) availability.put(player.getUuid(), Availability.UNAVAILABLE);
        });
        return future;
    }

    private void onPluginMessage(@NotNull PlayerPluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) return;
        Frame frame;
        try {
            frame = decodeFrame(event.getMessage());
        } catch (RuntimeException ignored) {
            return;
        }
        if (frame.requestId == 0) return;
        Pending req = pending.get(frame.requestId);
        // spoof guard: a frame from another connection must not complete or evict this pending RPC
        if (req == null || !req.playerId().equals(event.getPlayer().getUuid())) return;
        pending.remove(frame.requestId);
        req.future().complete(new Response(frame.opcode, frame.payload));
    }

    private void failPending(@NotNull Player player, @NotNull String reason) {
        pending.entrySet().removeIf(entry -> {
            if (!entry.getValue().playerId().equals(player.getUuid())) return false;
            entry.getValue().future().completeExceptionally(new ViaBridgeException(reason));
            return true;
        });
    }

    private int nextId() {
        int id;
        do {
            id = nextRequestId.getAndIncrement();
        } while (id == 0); // 0 is the reserved unsolicited-frame id
        return id;
    }

    private record Frame(int requestId, int opcode, byte[] payload) {}

    private static byte[] encodeEntityMotionBody(int entityId, short vx, short vy, short vz) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeVarInt(out, entityId);
            writeShort(out, vx);
            writeShort(out, vy);
            writeShort(out, vz);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] encodeSendClientbound(int protocolId, String packetClass, String packetType, byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            writeVarInt(out, protocolId);
            writeString(out, packetClass);
            writeString(out, packetType);
            out.write(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] encodeFrame(int wireVersion, int requestId, int opcode, byte[] payload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write(wireVersion & 0xFF);
            writeVarInt(out, requestId);
            writeVarInt(out, opcode);
            out.write(payload);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static Frame decodeFrame(byte[] bytes) {
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        try {
            int version = in.read();
            if (version < 0) throw new IllegalArgumentException("empty frame");
            int requestId = readVarInt(in);
            int opcode = readVarInt(in);
            return new Frame(requestId, opcode, in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Status decodeStatus(byte[] payload) {
        if (payload.length == 0) throw new IllegalArgumentException("empty status");
        byte code = payload[0];
        if (payload.length == 1) return new Status(code, null, new byte[0]);
        if (code != STATUS_OK && code != STATUS_CANCELLED) {
            ByteArrayInputStream in = new ByteArrayInputStream(payload, 1, payload.length - 1);
            try {
                return new Status(code, readString(in), new byte[0]);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return new Status(code, null, Arrays.copyOfRange(payload, 1, payload.length));
    }

    private static void writeShort(ByteArrayOutputStream out, short value) throws IOException {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeString(ByteArrayOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static String readString(ByteArrayInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new IllegalArgumentException("truncated string");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarInt(ByteArrayInputStream in) throws IOException {
        int value = 0;
        int size = 0;
        int b;
        while ((b = in.read()) >= 0) {
            value |= (b & 0x7F) << (size * 7);
            size++;
            if (size > 5) throw new IllegalArgumentException("varint too long");
            if ((b & 0x80) == 0) return value;
        }
        throw new IllegalArgumentException("truncated varint");
    }
}
