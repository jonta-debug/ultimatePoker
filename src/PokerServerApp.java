import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

/** Headless-capable local-network server entry point. */
public final class PokerServerApp {
    private PokerServerApp() { }
    public static void main(String[] args) throws Exception {
        int port = configuredPort(args);
        GameSessionService service = new GameSessionService();
        PokerHttpServer server = new PokerHttpServer(service, new SessionDirectory()).start("0.0.0.0", port);
        HostedSession session = server.createSession();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        System.out.println("Ultimate Poker server is running.");
        System.out.println("Open: http://" + localAddress() + ":" + server.getPort());
        System.out.println("Join code: " + session.joinCode());
        System.out.println("Table token (keep private): " + session.hostToken());
    }
    static int configuredPort(String[] args) {
        String value = args.length > 0 ? args[0] : System.getProperty("poker.port", System.getenv().getOrDefault("PORT", "8080"));
        int port; try { port = Integer.parseInt(value); } catch (NumberFormatException e) { throw new IllegalArgumentException("Port must be an integer", e); }
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Port must be between 0 and 65535");
        return port;
    }
    static String localAddress() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (var address : Collections.list(network.getInetAddresses())) if (address instanceof Inet4Address && address.isSiteLocalAddress()) return address.getHostAddress();
            }
        } catch (Exception ignored) { }
        return "localhost";
    }
}
