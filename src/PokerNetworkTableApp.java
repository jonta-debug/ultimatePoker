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

/** Composition root sharing one selected-mode session between server and table. */
public final class PokerNetworkTableApp {
    private PokerNetworkTableApp(){}
    public static void main(String[] args){int port=PokerServerApp.configuredPort(args);GameMode mode=PokerServerApp.configuredMode(args);SwingUtilities.invokeLater(()->launch(port,mode));}
    private static void launch(int port,GameMode mode){GameSessionService service=new GameSessionService();PokerHttpServer server=new PokerHttpServer(service,new SessionDirectory()).start("0.0.0.0",port);HostedSession hosted=server.createSession(mode);String url="http://"+PokerServerApp.localAddress()+":"+server.getPort();System.out.println("Phone URL: "+url);System.out.println("Mode: "+mode);System.out.println("Join code: "+hosted.joinCode());JFrame frame=new JFrame("Ultimate Poker - "+mode+" Lobby");frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);JLabel details=new JLabel("",SwingConstants.CENTER);details.setFont(details.getFont().deriveFont(Font.PLAIN,18f));JButton start=new JButton("Start table");JPanel lobby=new JPanel(new BorderLayout(12,12));lobby.add(details,BorderLayout.CENTER);lobby.add(start,BorderLayout.SOUTH);frame.setContentPane(lobby);frame.setMinimumSize(new Dimension(620,300));frame.setSize(720,380);frame.setLocationRelativeTo(null);Timer refresh=new Timer(400,e->{int count=service.getPlayers(hosted.sessionId()).size();details.setText("<html><div style='text-align:center'>"+mode+"<br>Open <b>"+url+"</b><br>Join code: <b>"+hosted.joinCode()+"</b><br><br>Joined players: "+count+" / 9</div></html>");start.setEnabled(count>=2);});start.setEnabled(false);start.addActionListener(e->{refresh.stop();frame.setTitle("Ultimate Poker - "+mode);frame.setMinimumSize(new Dimension(960,640));frame.setSize(1280,800);if(mode==GameMode.TEXAS_HOLDEM){HoldemTableController controller=new HoldemTableController(service,hosted.sessionId(),100,1,2);frame.setContentPane(controller.getView());frame.revalidate();controller.start();}else{PokerTableController controller=new PokerTableController(service,hosted.sessionId(),20,1,()->{server.stop();frame.dispose();});frame.setContentPane(controller.getView());frame.revalidate();controller.startNewRound();}});frame.addWindowListener(new java.awt.event.WindowAdapter(){@Override public void windowClosed(java.awt.event.WindowEvent e){refresh.stop();server.stop();}});refresh.start();frame.setVisible(true);}
}
