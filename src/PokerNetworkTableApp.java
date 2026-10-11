import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

/** Composition root for one shared local server and the existing canvas table UI. */
public final class PokerNetworkTableApp {
    private PokerNetworkTableApp() { }
    public static void main(String[] args) {
        int port = PokerServerApp.configuredPort(args);
        SwingUtilities.invokeLater(() -> launch(port));
    }
    private static void launch(int port) {
        GameSessionService service = new GameSessionService();
        PokerHttpServer server = new PokerHttpServer(service, new SessionDirectory()).start("0.0.0.0", port);
        HostedSession hosted = server.createSession();
        String url = "http://" + PokerServerApp.localAddress() + ":" + server.getPort();
        System.out.println("Phone URL: " + url); System.out.println("Join code: " + hosted.joinCode());

        JFrame frame = new JFrame("Ultimate Poker - Network Lobby");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JLabel details = new JLabel("", SwingConstants.CENTER); details.setFont(details.getFont().deriveFont(Font.PLAIN, 18f));
        JButton start = new JButton("Start table");
        JPanel lobby = new JPanel(new BorderLayout(12, 12)); lobby.add(details, BorderLayout.CENTER); lobby.add(start, BorderLayout.SOUTH);
        frame.setContentPane(lobby); frame.setMinimumSize(new Dimension(620, 300)); frame.setSize(720, 380); frame.setLocationRelativeTo(null);

        Timer refresh = new Timer(400, event -> {
            int players = service.getPlayers(hosted.sessionId()).size();
            details.setText("<html><div style='text-align:center'>Open <b>" + url + "</b><br>Join code: <b>" + hosted.joinCode() + "</b><br><br>Joined players: " + players + " / 9</div></html>");
            start.setEnabled(players >= 2);
        });
        start.setEnabled(false);
        start.addActionListener(event -> {
            refresh.stop();
            PokerTableController controller = new PokerTableController(service, hosted.sessionId(), 20, 1, () -> { server.stop(); frame.dispose(); });
            frame.setTitle("Ultimate Poker - Network Table"); frame.setContentPane(controller.getView()); frame.setMinimumSize(new Dimension(960, 640)); frame.setSize(1280, 800); frame.setLocationRelativeTo(null); frame.revalidate();
            controller.startNewRound();
        });
        frame.addWindowListener(new java.awt.event.WindowAdapter() { @Override public void windowClosed(java.awt.event.WindowEvent event) { refresh.stop(); server.stop(); } });
        refresh.start(); frame.setVisible(true);
    }
}
