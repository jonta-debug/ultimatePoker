/** Safe client-facing transport failure without stack-trace exposure. */
public final class TransportException extends RuntimeException {
    private final int status; private final String code;
    public TransportException(int status, String code, String message) { super(message); this.status = status; this.code = code; }
    public int getStatus() { return status; } public String getCode() { return code; }
}
