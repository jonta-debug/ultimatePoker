import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory join-code and opaque-token registry. It contains no poker rules. */
public final class SessionDirectory {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final Map<String,String> sessionByCode = new ConcurrentHashMap<>();
    private final Map<String,String> codeBySession = new ConcurrentHashMap<>();
    private final Map<String,ClientIdentity> identityByToken = new ConcurrentHashMap<>();
    private final Map<String,String> sessionByHostToken = new ConcurrentHashMap<>();

    public HostedSession registerSession(String sessionId) {
        if (codeBySession.containsKey(sessionId)) throw new IllegalStateException("Session is already registered");
        String code;
        do { code = randomCode(); } while (sessionByCode.putIfAbsent(code, sessionId) != null);
        codeBySession.put(sessionId, code);
        String hostToken = randomToken(); sessionByHostToken.put(hostToken, sessionId);
        return new HostedSession(sessionId, code, hostToken);
    }
    public String requireSessionId(String code) {
        String result = sessionByCode.get(normalizeCode(code));
        if (result == null) throw new TransportException(404, "INVALID_SESSION_CODE", "Unknown join code");
        return result;
    }
    public String getJoinCode(String sessionId) { return codeBySession.get(sessionId); }
    public String issuePlayerToken(PlayerRegistration registration) {
        String token = randomToken();
        identityByToken.put(token, new ClientIdentity(registration.getSessionId(), registration.getPlayerId(), registration.getDisplayName()));
        return token;
    }
    public ClientIdentity requirePlayer(String token) {
        ClientIdentity result = identityByToken.get(token);
        if (result == null) throw new TransportException(401, "UNAUTHORIZED", "Invalid or missing player token");
        return result;
    }
    public String requireHostedSession(String token) {
        String result = sessionByHostToken.get(token);
        if (result == null) throw new TransportException(401, "UNAUTHORIZED", "Invalid or missing table token");
        return result;
    }
    static String normalizeCode(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private String randomCode() { StringBuilder b = new StringBuilder(6); for (int i=0;i<6;i++) b.append(ALPHABET[random.nextInt(ALPHABET.length)]); return b.toString(); }
    private String randomToken() { byte[] b = new byte[32]; random.nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
}
