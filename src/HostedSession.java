/** Transport-facing session metadata; the UUID remains the internal identity. */
public record HostedSession(String sessionId, String joinCode, String hostToken) { }
