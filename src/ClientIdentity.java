/** Server-owned binding between an opaque browser token and one player. */
public record ClientIdentity(String sessionId, int playerId, String displayName) { }
