import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** Binds a shared Hold'em session to its public-only Swing display. */
public final class HoldemTableController {
    private final GameSessionService service;
    private final String sessionId;
    private final long stack,smallBlind,bigBlind;
    private final HoldemTablePanel panel=new HoldemTablePanel();
    private final JPanel root=new JPanel(new BorderLayout());
    private final JButton nextHand=new JButton("Next Hand");
    private final GameSessionChangeListener listener;
    public HoldemTableController(GameSessionService service,String sessionId,long stack,long smallBlind,long bigBlind){this.service=service;this.sessionId=sessionId;this.stack=stack;this.smallBlind=smallBlind;this.bigBlind=bigBlind;root.add(panel,BorderLayout.CENTER);root.add(nextHand,BorderLayout.SOUTH);nextHand.setEnabled(false);nextHand.addActionListener(e->nextHand());listener=id->{if(id.equals(sessionId))SwingUtilities.invokeLater(this::refresh);};service.addChangeListener(listener);}
    public JPanel getView(){return root;}
    public void start(){service.startHoldemRound(new StartHoldemRoundCommand(sessionId,stack,smallBlind,bigBlind));refresh();}
    public void nextHand(){service.startNextPokerRound(sessionId);refresh();}
    public void close(){service.removeChangeListener(listener);}
    private void refresh(){HoldemSnapshot snapshot=(HoldemSnapshot)service.getPublicPokerState(sessionId);panel.setSnapshot(snapshot);nextHand.setEnabled(snapshot.isHandComplete());}
}
