/** Receives a transport-neutral notification after a session mutation succeeds. */
@FunctionalInterface
public interface GameSessionChangeListener { void onSessionChanged(String sessionId); }
