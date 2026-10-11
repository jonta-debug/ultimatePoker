import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import javax.swing.JPanel;

/** Minimal public-only central table for the first Hold'em mode. */
public final class HoldemTablePanel extends JPanel {
    private HoldemSnapshot snapshot;
    public HoldemTablePanel(){setBackground(new Color(12,18,17));}
    public void setSnapshot(HoldemSnapshot snapshot){this.snapshot=snapshot;repaint();}
    @Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);int w=getWidth(),h=getHeight();g.setColor(new Color(35,70,55));g.fill(new Ellipse2D.Double(w*.12,h*.13,w*.76,h*.68));g.setColor(new Color(189,151,76));g.setFont(getFont().deriveFont(Font.BOLD,22f));if(snapshot==null){drawCentered(g,"Waiting for Hold'em",w/2,h/2);g.dispose();return;}drawCentered(g,snapshot.getStatusText()+"   Pot: "+snapshot.getPotChips(),w/2,(int)(h*.33));StringBuilder board=new StringBuilder();for(Card c:snapshot.getVisibleCommunityCards())board.append(shortCard(c)).append("   ");g.setFont(getFont().deriveFont(Font.BOLD,28f));drawCentered(g,board.toString(),w/2,h/2);g.setFont(getFont().deriveFont(15f));int y=(int)(h*.72);for(HoldemPlayerSnapshot p:snapshot.getPlayers()){String tags=(p.isDealer()?" D":"")+(p.isSmallBlind()?" SB":"")+(p.isBigBlind()?" BB":"")+(p.isFolded()?" FOLDED":"")+(p.isAllIn()?" ALL-IN":"")+(p.isCurrentActor()?" ◀ ACT":"");g.setColor(p.isCurrentActor()?new Color(244,206,109):Color.WHITE);drawCentered(g,p.getDisplayName()+"  "+p.getStackChips()+tags,w/2,y);y+=22;}if(snapshot.isHandComplete())drawCentered(g,"Winner(s): "+snapshot.getWinnerPlayerIds(),w/2,(int)(h*.88));g.dispose();}
    private static String shortCard(Card c){String rank=switch(c.getRank()){case TWO->"2";case THREE->"3";case FOUR->"4";case FIVE->"5";case SIX->"6";case SEVEN->"7";case EIGHT->"8";case NINE->"9";case TEN->"10";case JACK->"J";case QUEEN->"Q";case KING->"K";case ACE->"A";};String suit=switch(c.getSuit()){case SPADES->"♠";case HEARTS->"♥";case DIAMONDS->"♦";case CLUBS->"♣";};return rank+suit;}
    private static void drawCentered(Graphics2D g,String text,int x,int y){g.drawString(text,x-g.getFontMetrics().stringWidth(text)/2,y);}
}
