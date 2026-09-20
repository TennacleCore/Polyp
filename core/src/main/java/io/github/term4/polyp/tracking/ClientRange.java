package io.github.term4.polyp.tracking;

/**
 * The client versions a fix or a compat knob APPLIES to, as an inclusive protocol range - not the version it was
 * written for. The two differ: the 1.7 tab grid rides a Join Game every client is sent, and Minestom's tab-complete
 * gap is every client's. A knob outside its range resolves unset, so a scope that sets it anyway is simply ignored
 * where it cannot apply. Ask with a protocol from {@link ClientInfoTracker#protocolOrAssumed}, never the raw
 * {@link ClientVersion#UNKNOWN_PROTOCOL}.
 */
public record ClientRange(int min, int max) {

    public static final ClientRange ANY = new ClientRange(0, Integer.MAX_VALUE);
    /** 1.8 and older, as {@link ClientVersion#isLegacy} reads it. */
    public static final ClientRange LEGACY = new ClientRange(0, ClientVersion.LEGACY_PROTOCOL_MAX);
    /** 1.7.x - protocol 4 (1.7.2-1.7.5) and 5 (1.7.6-1.7.10). */
    public static final ClientRange V1_7 = new ClientRange(0, 5);
    /** 1.9 and newer. */
    public static final ClientRange MODERN = new ClientRange(ClientVersion.LEGACY_PROTOCOL_MAX + 1, Integer.MAX_VALUE);

    public boolean covers(int protocol) {
        return protocol >= min && protocol <= max;
    }

    /** {@code "any"}, {@code "1.8 and older"}, {@code "1.7"}, {@code "1.9+"} or the raw bounds - for a listing. */
    @Override
    public String toString() {
        if (equals(ANY)) return "any";
        if (equals(LEGACY)) return "1.8 and older";
        if (equals(V1_7)) return "1.7";
        if (equals(MODERN)) return "1.9+";
        return max == Integer.MAX_VALUE ? min + "+" : min + ".." + max;
    }
}
